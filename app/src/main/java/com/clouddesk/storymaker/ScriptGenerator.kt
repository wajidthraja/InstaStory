package com.clouddesk.storymaker

/**
 * Turns the user's context into Story text, a mood (style + music) and a caption.
 *
 * v1 runs fully on the phone with simple rules (no internet, no API key):
 * your sentences become the lines, the mood is detected from keywords, and the
 * mood adds an opener/closer in the chosen tone. To plug in an LLM later, implement
 * this interface with a call to your own backend (never ship an API key inside the APK).
 */
interface ScriptGenerator {
    /**
     * @param variant picks a different closing line on "Try another line".
     * @param mood    forces a mood instead of detecting one (user tapped a mood chip).
     */
    fun generate(context: String, tone: Tone, variant: Int = 0, mood: Mood? = null): StoryScript
}

class LocalScriptGenerator : ScriptGenerator {

    override fun generate(context: String, tone: Tone, variant: Int, mood: Mood?): StoryScript {
        val text = normalizeQuotes(context.trim())
        val m = mood ?: detectMood(text)
        val own = toLines(text)
        val slang = slangFor(tone, m)
        val style: (String) -> String = when (tone) {
            Tone.CLASSIC -> { s -> s }
            Tone.GENZ -> { s -> genZify(s) }
            Tone.TANGLISH -> { s -> tanglishify(s) }
        }
        val closer = slang?.closers?.get(Math.floorMod(variant, slang.closers.size)) ?: m.closer
        val lines = when {
            // Short context: frame it with a "pov:" opener and a closer.
            slang != null && own.size <= 3 -> listOf(slang.pov) + own.map(style) + closer
            // Long context already tells the story: keep all of it, the closer is the punchline.
            slang != null -> fit(own, MAX_LINES - 1).map(style) + closer
            own.size <= 6 -> own.map(style) + closer
            else -> fit(own, MAX_LINES)
        }
        return StoryScript(lines = lines.flatMap { splitLong(it) }, mood = m, tone = tone, caption = caption(text, m, tone))
    }

    /** A line over ~90 characters can't be read in one look: split it at the comma (or space) nearest the middle. */
    private fun splitLong(line: String): List<String> {
        if (line.length <= SPLIT_CHARS) return listOf(line)
        val mid = line.length / 2
        val commas = Regex(",\\s").findAll(line).map { it.range.first }.toList()
        val cut = commas.minByOrNull { kotlin.math.abs(it - mid) }?.plus(1)
            ?: line.lastIndexOf(' ', mid).takeIf { it > 0 }
            ?: return listOf(line)
        return listOf(line.substring(0, cut).trim().trimEnd(','), line.substring(cut).trim())
    }

    /** Highest keyword count wins; ties go to the mood listed first. Calm when nothing matches. */
    fun detectMood(text: String): Mood {
        val lower = text.lowercase()
        val best = Mood.entries.maxByOrNull { m -> m.words.count { hasWord(lower, it) } }
        return if (best != null && best.words.any { hasWord(lower, it) }) best else Mood.CALM
    }

    /**
     * Whole-word match. Keywords of 4+ letters also match longer forms ("exam" → "exams",
     * "struggl" → "struggling"); short ones must match exactly so "tea" doesn't hit "instead".
     */
    private fun hasWord(lower: String, word: String): Boolean {
        val w = Regex.escape(word)
        return Regex(if (word.length >= 4) "\\b$w" else "\\b$w\\b").containsMatchIn(lower)
    }

    /** Merge the shortest neighbouring lines until it fits, so the ending (the punchline) is never cut. */
    private fun fit(lines: List<String>, max: Int): List<String> {
        val l = lines.toMutableList()
        while (l.size > max) {
            val i = (0 until l.size - 1).minBy { l[it].length + l[it + 1].length }
            l[i] = l[i] + " " + l[i + 1].replaceFirstChar(Char::lowercaseChar)
            l.removeAt(i + 1)
        }
        return l
    }

    private fun normalizeQuotes(s: String) =
        s.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u201C', '"').replace('\u201D', '"')

    /**
     * Split into Story-sized lines: sentences first; long sentences are cut at commas / "and" / "but" / "so"
     * and the pieces merged back up to ~60 characters so no line is a lonely word.
     */
    private fun toLines(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val sentences = text.split(Regex("(?<=[.!?…])\\s+|\\n+"))
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotBlank() }
        return sentences.flatMap { if (it.length <= LINE_CHARS) listOf(it) else chunk(it) }
            .map { it.trim().trimEnd(',').replaceFirstChar(Char::uppercaseChar) }
            .filter { it.isNotBlank() }
    }

    private fun chunk(sentence: String): List<String> {
        val parts = sentence.split(Regex("(?<=,)\\s+|\\s+(?=(?:and|but|so)\\s)"))
        val out = mutableListOf<String>()
        var cur = ""
        for (p in parts) {
            val next = if (cur.isEmpty()) p else "$cur $p"
            // Keep going while it fits; never leave a tiny piece ("Bro,", "freedom,") on its own line.
            if (cur.isEmpty() || next.length <= LINE_CHARS || cur.length < 20 || p.length < 12) cur = next
            else { out += cur; cur = p }
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /** Gen Z texting style: all lowercase, no full stops, a few word swaps. */
    private fun genZify(line: String): String {
        var s = line.lowercase().trimEnd('.', '!')
        for ((from, to) in SWAPS) s = s.replace(Regex("\\b$from\\b"), to)
        return s
    }

    /** Tanglish: Gen Z lowercase, then common phrases turned into how people say them in Tamil. */
    private fun tanglishify(line: String): String {
        var s = line.lowercase().trimEnd('.', '!', ',')
        for ((pattern, replacement) in TANGLISH_PHRASES) s = s.replace(Regex(pattern), replacement)
        for ((from, to) in TANGLISH_WORDS) s = s.replace(Regex("\\b$from\\b"), to)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun caption(text: String, mood: Mood, tone: Tone): String {
        val lower = text.lowercase()
        val matched = (mood.words + Mood.entries.flatMap { it.words })
            .filter { it.length > 3 && it.all(Char::isLetter) && Regex("\\b$it\\b").containsMatchIn(lower) }
            .distinct()
        val base = listOf(mood.label.lowercase()) +
            matched.filter { w -> matched.none { it != w && it.contains(w) } }   // "difficulty" beats "difficult"
        val tags = when (tone) {
            Tone.CLASSIC -> base
            Tone.GENZ -> base.take(3) + listOf("relatable", "pov")
            Tone.TANGLISH -> base.take(3) + listOf("tanglish", "relatable")
        }.distinct().take(5).map { "#$it" }
        val first = text.split(Regex("(?<=[.!?])\\s+|\\n+")).firstOrNull()?.trim().orEmpty()
        val head = when (tone) {
            Tone.CLASSIC -> first
            Tone.GENZ -> genZify(first) + " 💀"
            Tone.TANGLISH -> tanglishify(first) + " 😭"
        }
        return (head + "\n\n" + tags.joinToString(" ")).trim()
    }

    companion object {
        const val MAX_LINES = 8
        const val LINE_CHARS = 70
        const val SPLIT_CHARS = 90
        private val SWAPS = listOf(
            "very" to "lowkey", "really" to "fr", "difficulty" to "a whole crisis", "difficult" to "a whole crisis",
            "tired" to "dead", "good" to "valid", "amazing" to "elite", "friends" to "the gang",
        )

        /** Whole phrases first (they reorder words the Tamil way), then single words. */
        private val TANGLISH_PHRASES = listOf(
            "(?:i am |i'm |im )?(?:having |facing )?(?:difficulty|trouble|problem|problems) (?:in|with|understanding) (.+)" to "$1 onnume puriyala",
            "(?:i am |i'm |im )?(?:confused|confusing) (?:about |in |with )?(.+)" to "$1 puriyala",
            "(?:i )?(?:don't|dont|do not) understand (.+)" to "$1 puriyala",
            "(?:i )?(?:can't|cant|cannot) (?:take|handle) (?:it|this)( anymore)?" to "mudiyala",
            "(?:i am |i'm |im )?(?:so |very )?tired" to "romba tired-aa iruku",
            "(?:i am |i'm |im )?(?:so |very )?happy" to "semma happy",
            "thinking i'?d finish (?:it )?in (.+)" to "$1-la mudichiduven nu nenachen",
            "now i'?m sitting here at (.+)" to "ippo mani $1, inga ukkaandhurukken",
            "trying to understand (.+)" to "— $1 onnume puriyala",
            "(?:and )?i (?:can't|cant|cannot) survive (.+)" to "naan $1 kooda survive panna mudiyala",
            "(?:i )?(?:can't|cant|cannot) (?:focus|concentrate)" to "focus panna mudiyala",
            "if the question says (.+?),? (?=i'?m |i am )" to "question-la $1 nu vandha, ",
            "i'?m (?:about|going) to (\\w+) my (\\w+) instead" to "naan adhukku badhila en $2-a $1 panna poren",
            "^bro\\b" to "machi",
        )
        private val TANGLISH_WORDS = listOf(
            "tomorrow" to "naalaiku", "today" to "inniku", "tonight" to "inniku night", "yesterday" to "nethu",
            "very" to "romba", "really" to "nijamave", "so much" to "romba", "super" to "semma",
            "this one" to "indha oru",
        )
    }
}
