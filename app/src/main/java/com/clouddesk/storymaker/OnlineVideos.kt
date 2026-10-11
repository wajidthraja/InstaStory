package com.clouddesk.storymaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One stock clip from Pixabay or Pexels. Both licenses allow free use and editing, including on social media. */
data class StockVideo(
    val source: String,        // "Pixabay" or "Pexels"
    val id: Long,
    val thumbUrl: String,
    val durationSec: Int,
    val author: String,
    val pageUrl: String,
    val fileUrl: String,
    val fileWidth: Int,
    val fileHeight: Int,
)

/**
 * Searches free stock-video libraries for clips that match the Story's context.
 * Keys come from the build (GitHub secrets PIXABAY_API_KEY and/or PEXELS_API_KEY), both free.
 * Pixabay is used when its key is set; otherwise Pexels.
 */
object OnlineVideos {

    private val usePixabay get() = BuildConfig.PIXABAY_API_KEY.isNotBlank()
    val hasKey: Boolean get() = usePixabay || BuildConfig.PEXELS_API_KEY.isNotBlank()
    val sourceName: String get() = if (usePixabay) "Pixabay" else "Pexels"
    val sourceUrl: String get() = if (usePixabay) "https://pixabay.com" else "https://www.pexels.com"

    /** Search words for a scene the user mentions; otherwise a scene that fits the mood. */
    fun suggestQuery(context: String, mood: Mood): String {
        val lower = context.lowercase()
        val scenes = SCENES.filter { (word, _) -> Regex("\\b$word").containsMatchIn(lower) }.map { it.second }.distinct()
        return if (scenes.isNotEmpty()) scenes.take(2).joinToString(" ") else MOOD_SCENES.getValue(mood)
    }

    suspend fun search(query: String, page: Int = 1): List<StockVideo> =
        if (usePixabay) searchPixabay(query, page) else searchPexels(query, page)

    private suspend fun searchPixabay(query: String, page: Int): List<StockVideo> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL("https://pixabay.com/api/videos/?key=${BuildConfig.PIXABAY_API_KEY}&q=$q&safesearch=true&per_page=24&page=$page")
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
        try {
            when (conn.responseCode) {
                200 -> Unit
                400 -> if (page > 1) return@withContext emptyList() else error("Search failed. Check the PIXABAY_API_KEY secret.")
                401, 403 -> error("The Pixabay API key isn't valid. Check the PIXABAY_API_KEY secret.")
                429 -> error("Too many searches in a minute. Wait a moment and try again.")
                else -> error("Search failed (HTTP ${conn.responseCode}).")
            }
            val hits = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("hits")
            (0 until hits.length()).mapNotNull { parsePixabay(hits.getJSONObject(it)) }
                .sortedByDescending { it.fileHeight > it.fileWidth }   // portrait clips first; others get cropped to 9:16
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePixabay(v: JSONObject): StockVideo? {
        val renditions = v.optJSONObject("videos") ?: return null
        val all = listOf("large", "medium", "small", "tiny").mapNotNull { renditions.optJSONObject(it) }
            .filter { it.optString("url").isNotBlank() && it.optInt("width") > 0 }
        // Largest file that isn't bigger than a 1080x1920 Story needs (skips 4K downloads).
        val best = all.filter { maxOf(it.optInt("width"), it.optInt("height")) <= 1920 }.maxByOrNull { it.optInt("height") }
            ?: all.minByOrNull { it.optInt("height") } ?: return null
        val thumb = all.firstNotNullOfOrNull { it.optString("thumbnail").takeIf { t -> t.isNotBlank() } }
            ?: v.optString("picture_id").takeIf { it.isNotBlank() }?.let { "https://i.vimeocdn.com/video/${it}_295x166.jpg" }
            ?: ""
        return StockVideo(
            source = "Pixabay",
            id = v.getLong("id"),
            thumbUrl = thumb,
            durationSec = v.optInt("duration"),
            author = v.optString("user").ifBlank { "Pixabay" },
            pageUrl = v.optString("pageURL"),
            fileUrl = best.getString("url"),
            fileWidth = best.optInt("width"),
            fileHeight = best.optInt("height"),
        )
    }

    private suspend fun searchPexels(query: String, page: Int): List<StockVideo> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL("https://api.pexels.com/videos/search?query=$q&orientation=portrait&size=medium&per_page=24&page=$page")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", BuildConfig.PEXELS_API_KEY)
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        try {
            when (conn.responseCode) {
                200 -> Unit
                401, 403 -> error("The Pexels API key isn't valid. Check the PEXELS_API_KEY secret.")
                429 -> error("Too many searches this hour. Try again in a little while.")
                else -> error("Search failed (HTTP ${conn.responseCode}).")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val videos = json.getJSONArray("videos")
            (0 until videos.length()).mapNotNull { i -> parsePexels(videos.getJSONObject(i)) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePexels(v: JSONObject): StockVideo? {
        val files = v.getJSONArray("video_files")
        // Best file for a 1080x1920 Story: a portrait MP4 at or just under 1920 tall; avoid 4K downloads.
        val candidates = (0 until files.length()).map { files.getJSONObject(it) }
            .filter { it.optString("file_type") == "video/mp4" && it.optInt("height") > 0 && it.optInt("width") > 0 }
        val best = candidates.filter { it.optInt("height") in 1000..2000 && it.optInt("height") >= it.optInt("width") }
            .maxByOrNull { it.optInt("height") }
            ?: candidates.filter { it.optInt("height") <= 2000 }.maxByOrNull { it.optInt("height") }
            ?: candidates.minByOrNull { it.optInt("height") }
            ?: return null
        val user = v.optJSONObject("user")
        return StockVideo(
            source = "Pexels",
            id = v.getLong("id"),
            thumbUrl = v.optString("image"),
            durationSec = v.optInt("duration"),
            author = user?.optString("name").orEmpty().ifBlank { "Pexels" },
            pageUrl = v.optString("url"),
            fileUrl = best.getString("link"),
            fileWidth = best.optInt("width"),
            fileHeight = best.optInt("height"),
        )
    }

    /** Downloads the clip to the app's cache (old downloads are removed). */
    suspend fun download(context: Context, video: StockVideo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "online").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "${video.source.lowercase()}_${video.id}.mp4")
        val conn = (URL(video.fileUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) error("Download failed (HTTP ${conn.responseCode}).")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                    }
                }
            }
            out
        } catch (e: Exception) {
            out.delete(); throw e
        } finally {
            conn.disconnect()
        }
    }

    // ---- thumbnails ----
    private val thumbs = LruCache<String, Bitmap>(40)

    suspend fun thumbnail(url: String): Bitmap? {
        if (url.isBlank()) return null
        thumbs.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 240) sample *= 2   // thumbnails only need ~240 px wide
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()?.also { thumbs.put(url, it) }
        }
    }

    /** Words in the context → what to search for. */
    private val SCENES = listOf(
        "beach" to "beach", "sea" to "sea waves", "ocean" to "ocean waves", "mountain" to "mountains", "hill" to "hills",
        "rain" to "rain", "coffee" to "coffee", "tea" to "tea cup", "temple" to "temple", "church" to "church",
        "party" to "party lights", "birthday" to "birthday cake", "wedding" to "wedding", "diwali" to "diwali lights",
        "festival" to "festival", "gym" to "gym workout", "workout" to "workout", "run" to "running", "cricket" to "cricket",
        "football" to "football", "book" to "books", "exam" to "student studying", "stud" to "student studying",
        "city" to "city night", "traffic" to "city traffic", "train" to "train journey", "bike" to "motorcycle ride",
        "drive" to "road drive", "road trip" to "road trip", "sunset" to "sunset", "sunrise" to "sunrise",
        "food" to "food", "biryani" to "biryani", "dog" to "dog", "cat" to "cat", "flower" to "flowers",
        "night" to "night", "office" to "office", "laptop" to "laptop work", "code" to "coding", "baby" to "baby",
        "airport" to "airport", "flight" to "airplane", "snow" to "snow", "forest" to "forest", "river" to "river",
    )

    private val MOOD_SCENES = mapOf(
        Mood.HAPPY to "happy friends laughing", Mood.CELEBRATION to "celebration confetti", Mood.CALM to "calm nature",
        Mood.LOVE to "couple sunset", Mood.SAD to "rain window", Mood.NERVOUS to "stressed student",
        Mood.MOTIVATED to "workout motivation", Mood.ADVENTURE to "road trip mountains", Mood.NOSTALGIC to "old film memories",
        Mood.GRATEFUL to "temple lamps", Mood.STUDY to "studying at night",
    )
}
