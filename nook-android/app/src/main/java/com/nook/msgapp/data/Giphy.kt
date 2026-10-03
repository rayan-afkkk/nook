package com.nook.msgapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

data class GiphyItem(val id: String, val url: String, val previewUrl: String, val width: Int, val height: Int, val title: String)

enum class GiphyType(val path: String) { Gifs("gifs"), Stickers("stickers") }

object Giphy {
    /** Trending when the query is empty; 24 results per page; PG-13. Returns items and the next offset (or null). */
    suspend fun search(type: GiphyType, q: String, offset: Int = 0): Pair<List<GiphyItem>, Int?> = withContext(Dispatchers.IO) {
        if (!AppConfig.giphyConfigured) throw UserFacingError("GIF search is not set up yet (Giphy key missing in this build).")
        val query = q.trim().take(50)
        val endpoint = if (query.isEmpty()) "trending" else "search"
        val url = "https://api.giphy.com/v1/${type.path}/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", AppConfig.giphyApiKey)
            .addQueryParameter("limit", "24")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("rating", "pg-13")
            .addQueryParameter("bundle", "messaging_non_clips")
            .apply { if (query.isNotEmpty()) addQueryParameter("q", query) }
            .build()
        val body = Worker.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw UserFacingError("Couldn't reach Giphy. Try again.")
            JSONObject(res.body?.string().orEmpty())
        }
        val data = body.optJSONArray("data")
        val items = buildList {
            for (i in 0 until (data?.length() ?: 0)) {
                val r = data!!.getJSONObject(i)
                val images = r.optJSONObject("images") ?: continue
                val main = images.optJSONObject("fixed_width") ?: images.optJSONObject("downsized") ?: continue
                val still = images.optJSONObject("fixed_width_still") ?: images.optJSONObject("fixed_width_small_still")
                val mainUrl = main.optString("url")
                if (mainUrl.isEmpty()) continue
                add(
                    GiphyItem(
                        id = r.optString("id"),
                        url = main.optString("webp").ifEmpty { mainUrl },
                        previewUrl = still?.optString("url")?.ifEmpty { null } ?: mainUrl,
                        width = main.optString("width").toIntOrNull() ?: 200,
                        height = main.optString("height").toIntOrNull() ?: 200,
                        title = r.optString("title"),
                    ),
                )
            }
        }
        val p = body.optJSONObject("pagination")
        val total = p?.optInt("total_count", 0) ?: 0
        val next = (p?.optInt("offset", offset) ?: offset) + (p?.optInt("count", items.size) ?: items.size)
        items to (if (total > 0 && next < total && items.isNotEmpty()) next else null)
    }
}
