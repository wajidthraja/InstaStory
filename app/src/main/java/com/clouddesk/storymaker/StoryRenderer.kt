@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.clouddesk.storymaker

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import java.io.File

data class RenderRequest(
    val videoUri: Uri,
    val clipDurationMs: Long,          // already capped at 60 s
    val lines: List<StoryLine>,
    val template: TextTemplate,
    val musicUri: Uri?,                 // null = no music in the file
    val musicStartMs: Long,             // which part of the song plays, like Instagram's song slider
    val keepOriginalSound: Boolean,
)

/** Uri for a bundled raw resource, readable by Media3 and MediaPlayer. */
fun rawUri(context: Context, resId: Int): Uri = Uri.parse("android.resource://${context.packageName}/$resId")

/**
 * Builds the Story MP4: 1080x1920 (crop to fill), H.264 + AAC, text overlay,
 * optional looping music mixed with the (quieter) original sound.
 * Must be started on the main thread.
 */
class StoryRenderer(private val context: Context) {

    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()

    fun start(
        request: RenderRequest,
        onDone: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val outDir = File(context.cacheDir, "stories").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }   // keep only the latest render
        val output = File(outDir, "story_${System.currentTimeMillis()}.mp4")

        // 1) The user's video: clipped to 60 s, cropped to 9:16, text drawn on top.
        val videoItem = MediaItem.Builder()
            .setUri(request.videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(request.clipDurationMs).build()
            )
            .build()

        val videoEffects = listOf(
            Presentation.createForWidthAndHeight(
                StorySpec.WIDTH, StorySpec.HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            ),
            OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(StoryOverlay(request.lines, request.template))),
        )

        val hasMusic = request.musicUri != null
        val originalVolume = if (hasMusic) 0.35f else 1f
        val videoAudio: List<AudioProcessor> =
            if (request.keepOriginalSound && originalVolume < 1f) listOf(volume(originalVolume)) else emptyList()

        val editedVideo = EditedMediaItem.Builder(videoItem)
            .setRemoveAudio(!request.keepOriginalSound)
            .setEffects(Effects(videoAudio, videoEffects))
            .build()

        val videoTracks = if (request.keepOriginalSound) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
        else setOf(C.TRACK_TYPE_VIDEO)
        val sequences = mutableListOf(
            EditedMediaItemSequence.Builder(videoTracks).addItem(editedVideo).build()
        )

        // 2) Music from the chosen start point, looped until the video ends.
        request.musicUri?.let { musicUri ->
            val clipped = MediaItem.Builder()
                .setUri(musicUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(request.musicStartMs).build()
                )
                .build()
            val musicItem = EditedMediaItem.Builder(clipped)
                .setEffects(Effects(listOf(volume(0.9f)), emptyList()))
                .build()
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItem(musicItem)
                .setIsLooping(true)
                .build()
        }

        val composition = Composition.Builder(sequences)
            // Phone HDR clips look washed out on Instagram; convert to standard (SDR) video.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(StorySpec.VIDEO_BITRATE).build()
            )
            .setEnableFallback(true)
            .build()

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    onDone(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    transformer = null
                    output.delete()
                    onError(exportException.message ?: "Export failed (code ${exportException.errorCode})")
                }
            })
            .build()
            .also { it.start(composition, output.absolutePath) }
    }

    /** 0–100, or -1 when not known yet. Call on the main thread. */
    fun progress(): Int {
        val t = transformer ?: return -1
        return if (t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) progressHolder.progress else -1
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    private fun volume(gain: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 1).scaleBy(gain))
        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(gain))
    }
}
