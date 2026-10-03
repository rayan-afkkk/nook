package com.nook.msgapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nook.core.Limits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UploadResult(
    val url: String,
    val publicId: String,
    val resourceType: String,
    val width: Int?,
    val height: Int?,
    val bytes: Long,
    val durationMs: Long?,
)

/** A picked file: content:// uri plus its display name, MIME type and size. */
data class PickedFile(val uri: Uri, val name: String, val mime: String, val size: Long)

object Cloudinary {
    /** Reads the display name and size of a content:// uri. */
    fun describe(context: Context, uri: Uri, fallbackName: String = "file"): PickedFile {
        var name = fallbackName
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }?.let { name = it }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return PickedFile(uri, name, mime, size)
    }

    fun fromFile(file: File, mime: String) = PickedFile(Uri.fromFile(file), file.name, mime, file.length())

    /**
     * Unsigned upload straight from the phone. The preset (Cloudinary console) limits formats, size and
     * folder; deletion happens later through the Worker, which holds the API secret.
     * resourceType: image for photos/stickers, video for audio (Cloudinary's convention), raw for files.
     */
    suspend fun upload(
        context: Context,
        file: PickedFile,
        resourceType: String,
        onProgress: (Float) -> Unit = {},
    ): UploadResult = withContext(Dispatchers.IO) {
        if (!AppConfig.cloudinaryConfigured) throw UserFacingError("Media uploads are not set up yet (Cloudinary keys missing in this build).")
        if (file.size > Limits.MAX_FILE_BYTES) throw UserFacingError("That file is over the 10 MB limit.")
        val resolver = context.contentResolver
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = file.mime.toMediaTypeOrNull()
            override fun contentLength(): Long = file.size
            override fun writeTo(sink: BufferedSink) {
                val input = (if (file.uri.scheme == "file") File(file.uri.path!!).inputStream() else resolver.openInputStream(file.uri))
                    ?: throw IOException("Can't open file")
                input.source().use { src ->
                    var total = 0L
                    while (true) {
                        val read = src.read(sink.buffer, 64 * 1024)
                        if (read == -1L) break
                        total += read
                        sink.flush()
                        if (file.size > 0) onProgress((total.toFloat() / file.size).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, body)
            .addFormDataPart("upload_preset", AppConfig.cloudinaryUploadPreset)
            .build()
        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/${AppConfig.cloudinaryCloudName}/$resourceType/upload")
            .post(form)
            .build()
        val call = Worker.http.newCall(request)
        val text: Pair<Int, String> = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(UserFacingError("Upload failed. Check your connection and retry."))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { cont.resume(it.code to (it.body?.string().orEmpty())) }
                }
            })
        }
        val data = runCatching { JSONObject(text.second) }.getOrNull() ?: throw UserFacingError("Upload failed. Please try again.")
        val url = data.optString("secure_url")
        val publicId = data.optString("public_id")
        if (text.first !in 200..299 || url.isEmpty() || publicId.isEmpty()) {
            throw UserFacingError(data.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() } ?: "Upload failed (${text.first}).")
        }
        UploadResult(
            url = url,
            publicId = publicId,
            resourceType = data.optString("resource_type", resourceType),
            width = data.optInt("width", 0).takeIf { it > 0 },
            height = data.optInt("height", 0).takeIf { it > 0 },
            bytes = data.optLong("bytes", file.size),
            durationMs = data.optDouble("duration", 0.0).takeIf { it > 0 }?.let { (it * 1000).toLong() },
        )
    }
}
