package com.clouddesk.storymaker

/**
 * Classic = clean, sentence case.
 * Gen Z = lowercase, "pov:" opener, sarcastic closer, meme-style text.
 * Tanglish = Gen Z vibe in Tamil-English, the way people text in Tamil Nadu.
 */
enum class Tone(val label: String) {
    CLASSIC("Classic"),
    GENZ("Gen Z"),
    TANGLISH("Tanglish"),
}

class GenZLines(val pov: String, val closers: List<String>)

/** Light sarcasm, never mean. "Try another line" cycles through the closers. */
val GENZ_LINES: Map<Mood, GenZLines> = mapOf(
    Mood.HAPPY to GenZLines("pov: i'm actually happy for once", listOf(
        "serotonin finally showed up 😌", "no thoughts, just vibes ✨", "main character energy, sorry not sorry")),
    Mood.CELEBRATION to GenZLines("pov: we're celebrating (finally)", listOf(
        "slay. that's the tweet 💅", "we ate and left no crumbs", "doing the most and loving it 🎉")),
    Mood.CALM to GenZLines("pov: touching grass, as recommended", listOf(
        "inner peace speedrun 🍃", "do not disturb, i'm healing", "low battery, high peace ☕")),
    Mood.LOVE to GenZLines("pov: down bad, respectfully", listOf(
        "yeah i'm down bad, and? 🫶", "they're my comfort person, deal with it", "certified yapper about them 💌")),
    Mood.SAD to GenZLines("pov: it's giving emotional damage", listOf(
        "it's fine. i'm fine. everything's fine 🙂", "crying in 1080p", "healing era loading… slowly")),
    Mood.NERVOUS to GenZLines("pov: overthinking at 2am", listOf(
        "my anxiety has anxiety 😭", "manifesting calm, receiving panic", "this is fine 🔥🙂")),
    Mood.MOTIVATED to GenZLines("pov: locked in, do not disturb", listOf(
        "locked in. don't text me 🔒", "villain arc: productive edition", "delusion is a strategy 💪")),
    Mood.ADVENTURE to GenZLines("pov: bank account crying, soul thriving", listOf(
        "broke but make it scenic 🌄", "touch grass? i touched a whole mountain", "plot twist: i actually left the house")),
    Mood.NOSTALGIC to GenZLines("pov: emotionally damaged by old photos", listOf(
        "take me back, respectfully 📼", "core memory unlocked", "we were so unserious back then")),
    Mood.GRATEFUL to GenZLines("pov: blessed and lowkey emotional", listOf(
        "blessed fr fr 🙏", "god said 'let them cook' 🙏", "grateful era, no cap")),
    Mood.STUDY to GenZLines("pov: studying the night before like a pro", listOf(
        "academic weapon? more like academic victim 💀", "the syllabus said 'surprise me' 😭", "if i pass, it's character development")),
)

/** Tanglish versions. Kept casual and kind, like a friend's Story. */
val TANGLISH_LINES: Map<Mood, GenZLines> = mapOf(
    Mood.HAPPY to GenZLines("pov: semma happy-aa iruken", listOf(
        "inniku vibe vera level ✨", "yaarum disturb pannadheenga, happy mode on 😌", "sirippu mattum dhaan, tension illa")),
    Mood.CELEBRATION to GenZLines("pov: celebration time, kalakkurom", listOf(
        "vera level celebration da 🎉", "treat yaaru kudukka poreenga? 👀", "inniku full jolly dhaan 💃")),
    Mood.CALM to GenZLines("pov: oru coffee, konjam peace", listOf(
        "no tension, only filter coffee ☕", "inner peace mode, disturb pannadheenga 🍃", "life-la ippo dhaan konjam nimmadhi")),
    Mood.LOVE to GenZLines("pov: en comfort person kooda", listOf(
        "romba down bad, so what? 🫶", "neenga illama mudiyala 💌", "my person, my peace")),
    Mood.SAD to GenZLines("pov: mood sari illa", listOf(
        "ellam okay, naan okay… apdi dhaan solren 🙂", "konjam feel aagudhu, but paathukalam", "healing era loading… romba slow-aa")),
    Mood.NERVOUS to GenZLines("pov: rendu mani ku overthinking", listOf(
        "tension-la thalaye vedikkudhu 😭", "calm-aa iru nu solren, mind kekka maatengudhu", "idhu fine dhaan 🔥🙂")),
    Mood.MOTIVATED to GenZLines("pov: locked in, phone silent mode", listOf(
        "ippo dhaan aarambam 💪", "villain arc: productive version", "vidamaatom, mudichiduvom 🔥")),
    Mood.ADVENTURE to GenZLines("pov: bank balance azhudhu, manasu santhosham", listOf(
        "kaasu illa, aana view semma 🌄", "veetla irundhadhu podhum, kelambitom", "trip mode on, signal off")),
    Mood.NOSTALGIC to GenZLines("pov: pazhaya photos paathu feel aagren", listOf(
        "andha naal thirumba varuma? 📼", "core memory unlocked", "appo naama evlo jolly-aa irundhom")),
    Mood.GRATEFUL to GenZLines("pov: blessed, konjam emotional", listOf(
        "kadavul kaapathitaaru 🙏", "nandri solla vaarthaiye illa", "amma appa blessings dhaan ellam 🙏")),
    Mood.STUDY to GenZLines("pov: exam ku munnadi night dhaan padippen", listOf(
        "mudiyala da, idhu syllabus-aa illa marathon-aa? 😭", "padichadhu onnume puriyala, aana confidence full 💀",
        "pass aana adhu kadavul grace dhaan 🙏")),
)

/** Opener/closer set for a tone; null for Classic (which uses the mood's own closer). */
fun slangFor(tone: Tone, mood: Mood): GenZLines? = when (tone) {
    Tone.CLASSIC -> null
    Tone.GENZ -> GENZ_LINES.getValue(mood)
    Tone.TANGLISH -> TANGLISH_LINES.getValue(mood)
}
