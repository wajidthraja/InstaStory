package com.clouddesk.storymaker

import androidx.annotation.RawRes

/** Instagram Story output spec. */
object StorySpec {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val MAX_DURATION_MS = 60_000L          // one Story segment
    const val VIDEO_BITRATE = 8_000_000          // 8 Mbps H.264, comfortably within Instagram's limits

    // Instagram covers the top (profile/progress bar) and bottom (reply bar) of a Story.
    // All text is kept between these lines.
    const val SAFE_TOP = 270
    const val SAFE_BOTTOM = 1920 - 380
}

/** Copyright-free music bundled with the app (original tracks synthesized for this project, CC0). */
enum class MusicTrack(val label: String, val mood: String, @RawRes val resId: Int) {
    CALM("Calm", "Soft piano and pads · family, mornings, temples", R.raw.music_calm),
    UPBEAT("Upbeat", "Drums and bright synths · parties, festivals, friends", R.raw.music_upbeat),
    CINEMATIC("Cinematic", "Slow swells and deep drums · trips, sunsets, big moments", R.raw.music_cinematic),
    EMOTIONAL("Emotional", "Slow minor piano · missing someone, goodbyes, tough days", R.raw.music_emotional),
    MOTIVATIONAL("Motivational", "Building drums and synths · goals, gym, comebacks", R.raw.music_motivational),
    LOFI("Lo-fi", "Mellow beat with vinyl crackle · study, late nights, love", R.raw.music_lofi),
}

/**
 * Every mood the app understands. Detected from the user's words; the user can override it.
 * Each mood decides the text style, the music and a short closing line.
 */
enum class Mood(
    val label: String,
    val template: TextTemplate,
    val music: MusicTrack,
    val closer: String,
    val words: List<String>,
) {
    HAPPY("Happy", TextTemplate.LOWER_CAPTION, MusicTrack.UPBEAT, "Good vibes only",
        listOf("happy", "smile", "fun", "awesome", "great day", "enjoy", "laugh", "best day", "yay")),
    CELEBRATION("Celebration", TextTemplate.LOWER_CAPTION, MusicTrack.UPBEAT, "Let's celebrate!",
        listOf("party", "celebrat", "birthday", "anniversary", "won", "win", "festival", "diwali", "pongal",
            "wedding", "result", "passed", "promotion", "congrat")),
    CALM("Calm", TextTemplate.BOLD_CENTER, MusicTrack.CALM, "Slow down. Breathe.",
        listOf("calm", "peace", "quiet", "relax", "morning", "rain", "coffee", "tea", "sunday", "chill")),
    LOVE("Love", TextTemplate.MINIMAL_TOP, MusicTrack.LOFI, "Grateful for you",
        listOf("love", "wife", "husband", "darling", "together", "date", "romantic", "my person", "heart")),
    SAD("Sad", TextTemplate.MINIMAL_TOP, MusicTrack.EMOTIONAL, "This too shall pass",
        listOf("sad", "miss", "missing", "lonely", "cry", "hurt", "goodbye", "lost", "broken", "alone", "upset")),
    NERVOUS("Nervous", TextTemplate.BOLD_CENTER, MusicTrack.LOFI, "One step at a time",
        listOf("nervous", "scared", "tension", "stress", "worried", "anxious", "difficult", "difficulty",
            "tough", "struggl", "confus", "hard", "fear", "afraid")),
    MOTIVATED("Motivated", TextTemplate.BOLD_CENTER, MusicTrack.MOTIVATIONAL, "Let's do this",
        listOf("goal", "gym", "workout", "focus", "never give up", "hustle", "grind", "comeback", "dream",
            "determined", "challenge", "practice", "prepar", "will do")),
    ADVENTURE("Adventure", TextTemplate.MINIMAL_TOP, MusicTrack.CINEMATIC, "Collecting moments",
        listOf("trip", "travel", "journey", "mountain", "beach", "sunset", "sunrise", "road", "adventure",
            "hike", "trek", "view", "ocean", "drive", "explore", "first time")),
    NOSTALGIC("Nostalgic", TextTemplate.MINIMAL_TOP, MusicTrack.LOFI, "Those were the days",
        listOf("remember", "old days", "childhood", "throwback", "memories", "years ago", "school days", "back then")),
    GRATEFUL("Grateful", TextTemplate.BOLD_CENTER, MusicTrack.CALM, "Thankful for every blessing",
        listOf("grateful", "thankful", "blessed", "temple", "prayer", "god", "family", "parents", "amma", "appa")),
    STUDY("Study", TextTemplate.BOLD_CENTER, MusicTrack.LOFI, "Revision mode on. Wish me luck!",
        listOf("exam", "study", "studying", "lesson", "revision", "school", "board", "chapter", "homework",
            "tuition", "english", "maths", "science", "syllabus", "test tomorrow", "paragraph", "question",
            "marks", "padikk", "biography", "notes")),
}

/** Where the Story's music comes from — mirrors Instagram's music picker. */
sealed interface MusicChoice {
    val title: String

    data object None : MusicChoice { override val title = "No music" }

    /** Bundled copyright-free track. */
    data class Library(val track: MusicTrack) : MusicChoice { override val title = track.label }

    /** An audio file from the phone (user confirms it's royalty-free). */
    data class Device(val uri: android.net.Uri, val name: String) : MusicChoice { override val title = name }

    /**
     * Popular / film songs: the video is made without music and the user adds the song
     * with Instagram's own Music sticker, which is licensed by Instagram.
     */
    data object InInstagram : MusicChoice { override val title = "Add song in Instagram" }
}

/** How long the music clip plays, like Instagram's 15–30s song selection. */
const val MUSIC_PREVIEW_MS = 15_000L

enum class TextTemplate(val label: String) {
    BOLD_CENTER("Bold center"),
    LOWER_CAPTION("Lower caption"),
    MINIMAL_TOP("Minimal top"),
    MEME("Meme"),          // white text, thick black outline — the Gen Z look
}

data class StoryLine(val text: String, val startMs: Long, val endMs: Long)

data class StoryScript(
    val lines: List<String>,
    val mood: Mood,
    val tone: Tone,
    val caption: String,
) {
    val template get() = if (tone == Tone.GENZ) TextTemplate.MEME else mood.template
    val music get() = mood.music
}

/** Comfortable reading time for one line: ~3 words per second plus a beat to notice it. */
fun readingMs(line: String): Long = 900L + 330L * line.trim().split(Regex("\\s+")).size

/** Total time the text needs to be readable — the app warns when the clip is shorter. */
fun readingMsTotal(lines: List<String>): Long = lines.filter { it.isNotBlank() }.sumOf { readingMs(it) }

/** Gives each line screen time in proportion to how long it takes to read, with a short gap between lines. */
fun timeLines(lines: List<String>, durationMs: Long): List<StoryLine> {
    val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (clean.isEmpty() || durationMs <= 0) return emptyList()
    val weights = clean.map { readingMs(it).toDouble() }
    val total = weights.sum()
    var t = 0.0
    return clean.mapIndexed { i, text ->
        val slot = durationMs * weights[i] / total
        val gap = if (slot > 2_000) 250.0 else 0.0
        val start = (t + if (i == 0) 200.0 else gap).toLong()
        t += slot
        val end = if (i == clean.lastIndex) durationMs - 200 else t.toLong()
        StoryLine(text, start.coerceAtMost(end - 500).coerceAtLeast(0), end)
    }
}
