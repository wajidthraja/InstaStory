package com.clouddesk.storymaker

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StoryViewModel(app: Application) : AndroidViewModel(app) {

    private val generator = LocalScriptGenerator()
    private val renderer = StoryRenderer(app)
    private var player: MediaPlayer? = null
    private var previewJob: Job? = null

    var videoUri by mutableStateOf<Uri?>(null); private set
    var videoInfo by mutableStateOf<VideoInfo?>(null); private set
    var contextText by mutableStateOf("")
    val lines = mutableStateListOf<String>()
    var template by mutableStateOf(TextTemplate.BOLD_CENTER)
    var keepOriginalSound by mutableStateOf(false)
    var caption by mutableStateOf("")

    // Music, Instagram-style: pick a song, then choose which part plays.
    var music by mutableStateOf<MusicChoice>(MusicChoice.Library(MusicTrack.CALM)); private set
    var musicDurationMs by mutableStateOf(0L); private set
    var musicStartMs by mutableStateOf(0L); private set
    var previewing by mutableStateOf<MusicChoice?>(null); private set

    var rendering by mutableStateOf(false); private set
    var progress by mutableStateOf(-1); private set
    var result by mutableStateOf<File?>(null); private set
    var message by mutableStateOf<String?>(null)

    val clipDurationMs: Long get() = minOf(videoInfo?.durationMs ?: 0L, StorySpec.MAX_DURATION_MS)
    val canGenerate get() = videoUri != null && contextText.isNotBlank()
    val canRender get() = videoUri != null && lines.any { it.isNotBlank() } && clipDurationMs > 0 && !rendering

    // Online (Pexels) video
    var downloadProgress by mutableStateOf<Float?>(null); private set   // non-null while downloading
    var videoCredit by mutableStateOf<String?>(null); private set

    /** Search words for the online video picker, from what the user has written so far. */
    fun suggestedVideoQuery(): String =
        OnlineVideos.suggestQuery(contextText, if (contextText.isBlank()) Mood.CALM else generator.detectMood(contextText))

    fun useOnlineVideo(video: StockVideo) {
        downloadProgress = 0f
        viewModelScope.launch {
            runCatching { OnlineVideos.download(getApplication(), video) { downloadProgress = it } }
                .onSuccess { file ->
                    downloadProgress = null
                    onVideoPicked(Uri.fromFile(file))
                    videoCredit = "Video by ${video.author} on ${video.source}"
                }
                .onFailure { downloadProgress = null; message = it.message ?: "Download failed. Check your internet connection." }
        }
    }

    fun onVideoPicked(uri: Uri) {
        videoCredit = null
        videoUri = uri
        result = null
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { StoryExport.probe(getApplication(), uri) }
            videoInfo = info
            if (info.durationMs <= 0) message = "Couldn't read this video. Try another clip."
            else if (info.durationMs > StorySpec.MAX_DURATION_MS) message = "Only the first 60 seconds will be used."
            if (!info.hasAudio) keepOriginalSound = false
        }
    }

    var mood by mutableStateOf(Mood.CALM); private set
    var tone by mutableStateOf(Tone.CLASSIC); private set
    private var variant = 0
    private var moodChosen = false   // true once the user taps a mood chip

    /** "Build my Story": detect the mood (unless the user picked one) and write the lines. */
    fun generate() {
        moodChosen = false
        variant = 0
        apply(generator.generate(contextText, tone, variant))
    }

    /** User overrides the detected mood: new style, lines and (bundled) music. */
    fun selectMood(new: Mood) {
        if (new == mood) return
        moodChosen = true
        apply(generator.generate(contextText, tone, variant, new))
    }

    fun selectTone(new: Tone) {
        if (new == tone) return
        tone = new
        if (lines.isNotEmpty()) apply(generator.generate(contextText, tone, variant, mood.takeIf { moodChosen }))
    }

    /** Next opener/closer variation in the same mood and tone. */
    fun tryAnotherLine() {
        variant++
        apply(generator.generate(contextText, tone, variant, mood))
    }

    /** Seconds of video the text needs; shown as a hint when the clip is too short to read everything. */
    val neededMs: Long get() = readingMsTotal(lines)

    private fun apply(script: StoryScript) {
        lines.clear(); lines.addAll(script.lines)
        mood = script.mood
        template = script.template
        // Don't override a song the user already chose themselves.
        if (music is MusicChoice.Library) selectMusic(MusicChoice.Library(script.music))
        caption = script.caption
        result = null
    }

    fun updateLine(i: Int, text: String) { lines[i] = text; result = null }
    fun removeLine(i: Int) { lines.removeAt(i); result = null }
    fun addLine() { if (lines.size < 8) lines.add("") }

    fun musicUri(choice: MusicChoice): Uri? = when (choice) {
        is MusicChoice.Library -> rawUri(getApplication(), choice.track.resId)
        is MusicChoice.Device -> choice.uri
        MusicChoice.None, MusicChoice.InInstagram -> null
    }

    fun selectMusic(choice: MusicChoice) {
        if (choice != music) result = null
        music = choice
        musicStartMs = 0L
        musicDurationMs = 0L
        val uri = musicUri(choice) ?: return
        viewModelScope.launch {
            musicDurationMs = withContext(Dispatchers.IO) { StoryExport.audioDurationMs(getApplication(), uri) }
        }
    }

    fun onDeviceAudioPicked(uri: Uri) {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectMusic(MusicChoice.Device(uri, StoryExport.displayName(app, uri)))
    }

    /** Slider: which part of the song plays. Restarts the preview from the new point. */
    fun setMusicStart(ms: Long) {
        musicStartMs = ms.coerceIn(0L, maxMusicStartMs())
        result = null
    }

    fun maxMusicStartMs(): Long = (musicDurationMs - MUSIC_PREVIEW_MS).coerceAtLeast(0L)

    /** Plays 15 s of a song (from the chosen start for the selected song, from 0 for others). */
    fun togglePreview(choice: MusicChoice) {
        val wasPlaying = previewing == choice
        stopPreview()
        if (wasPlaying) return
        val uri = musicUri(choice) ?: return
        val from = if (choice == music) musicStartMs else 0L
        val p = MediaPlayer.create(getApplication(), uri) ?: run { message = "Can't play this file."; return }
        p.seekTo(from.toInt())
        p.setOnCompletionListener { stopPreview() }
        p.start()
        player = p
        previewing = choice
        previewJob = viewModelScope.launch { delay(MUSIC_PREVIEW_MS); stopPreview() }
    }

    fun render() {
        val uri = videoUri ?: return
        stopPreview()
        val timed = timeLines(lines, clipDurationMs)
        rendering = true; progress = 0; result = null
        renderer.start(
            RenderRequest(
                videoUri = uri,
                clipDurationMs = clipDurationMs,
                lines = timed,
                template = template,
                musicUri = musicUri(music),
                musicStartMs = musicStartMs,
                keepOriginalSound = keepOriginalSound && videoInfo?.hasAudio == true,
            ),
            onDone = { file ->
                rendering = false; result = file
                message = if (music == MusicChoice.InInstagram) "Story ready. Add your song with Instagram's Music sticker."
                else "Story ready."
            },
            onError = { err -> rendering = false; message = "Couldn't create the video: $err" },
        )
        viewModelScope.launch {
            while (rendering) {
                progress = renderer.progress()
                delay(300)
            }
        }
    }

    fun cancelRender() { renderer.cancel(); rendering = false }

    fun saveToGallery() {
        val file = result ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { StoryExport.saveToGallery(getApplication(), file) }
            message = if (ok) "Saved to Gallery › Movies › StoryMaker" else "Couldn't save to gallery."
        }
    }

    fun stopPreview() {
        previewJob?.cancel(); previewJob = null
        player?.release(); player = null
        previewing = null
    }

    fun previewOverlay(): Bitmap? {
        val first = lines.firstOrNull { it.isNotBlank() } ?: return null
        return Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888).also {
            val c = android.graphics.Canvas(it)
            c.scale(0.5f, 0.5f)
            drawStoryText(c, first, template)
        }
    }

    override fun onCleared() {
        stopPreview()
        renderer.cancel()
    }
}
