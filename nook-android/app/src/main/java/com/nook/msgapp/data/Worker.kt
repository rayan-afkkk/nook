package com.nook.msgapp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WorkerError(message: String, val status: Int) : Exception(message)

/** Authenticated JSON calls to the NOOK Cloudflare Worker (Firebase ID token as Bearer). */
object Worker {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = "application/json".toMediaType()

    suspend fun call(path: String, body: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        if (!AppConfig.workerConfigured) throw WorkerError("The NOOK server (Cloudflare Worker) is not configured in this build.", 0)
        val user = Fb.auth.currentUser ?: throw WorkerError("Not signed in.", 401)
        val token = user.getIdToken(false).await().token ?: throw WorkerError("Not signed in.", 401)
        val payload = JSONObject()
        body.forEach { (k, v) -> payload.put(k, if (v is List<*>) JSONArray(v) else v) }
        val request = Request.Builder()
            .url(AppConfig.workerUrl + path)
            .header("authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(json))
            .build()
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val data = runCatching { JSONObject(text) }.getOrNull()
            if (!res.isSuccessful) {
                throw WorkerError(data?.optString("error")?.takeIf { it.isNotEmpty() } ?: "Server error (${res.code})", res.code)
            }
            data ?: JSONObject()
        }
    }

    /** Fire-and-forget with exponential backoff; never throws. Used for push notifications. */
    fun callInBackground(path: String, body: Map<String, Any?>, attempts: Int = 4) {
        if (!AppConfig.workerConfigured) return
        scope.launch {
            repeat(attempts) { n ->
                try {
                    call(path, body)
                    return@launch
                } catch (e: WorkerError) {
                    // Don't retry requests the server rejected on purpose.
                    if (e.status in 400..499) return@launch
                } catch (_: Exception) {
                }
                delay(1000L shl n)
            }
        }
    }
}
