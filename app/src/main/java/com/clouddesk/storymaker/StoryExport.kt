package com.clouddesk.storymaker

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class VideoInfo(val durationMs: Long, val hasAudio: Boolean, val firstFrame: Bitmap?)

object StoryExport {

    private const val INSTAGRAM = "com.instagram.android"

    fun probe(context: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            VideoInfo(
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                firstFrame = r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 540, 960),
            )
        } catch (e: Exception) {
            VideoInfo(0L, false, null)
        } finally {
            r.release()
        }
    }

    fun audioDurationMs(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            r.release()
        }
    }

    /** File name of a picked audio file, without extension. */
    fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.substringBeforeLast('.')
            ?: "My track"

    /** Copies the MP4 into the phone gallery under Movies/StoryMaker. */
    fun saveToGallery(context: Context, file: File): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StoryMaker")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /**
     * Opens Instagram with the video.
     * - With a Facebook App ID configured: straight into the Story editor.
     * - Without one: Instagram's share screen, where you tap "Story".
     * - Instagram not installed: the normal Android share sheet.
     */
    fun shareToInstagram(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val appId = BuildConfig.FACEBOOK_APP_ID
        if (appId.isNotBlank()) {
            val story = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(uri, "video/mp4")
                putExtra("source_application", appId)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(INSTAGRAM)
            }
            if (tryStart(context, story)) return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (tryStart(context, Intent(send).setPackage(INSTAGRAM))) return
        context.startActivity(Intent.createChooser(send, "Share Story"))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
