# Story Maker

Android app that turns a video and a short description into a ready-to-post Instagram Story (MP4).

**Flow:** pick video → describe the context (type or speak) → pick a tone → app writes the Story text, detects the mood, picks a style and music → review and edit → create a 1080×1920 MP4 → share to Instagram Story or save to gallery.

## Prerequisites

| You need | Why |
|---|---|
| Android phone, Android 10 or newer | The app's minimum version |
| Free GitHub account | Builds the APK for you (no Android Studio needed) |
| Instagram app on the same phone | For "Share to Story" |
| Chrome (or any browser) on the phone, signed in to GitHub | To download the APK from your private repo's Releases page |
| "Install unknown apps" allowed for Chrome | Android asks once during install |
| Your own video, or royalty-free footage | Copyright |
| Optional: Facebook App ID | Opens Instagram's Story editor directly instead of the share screen |
| Optional: Android Studio on a PC (8 GB+ RAM) | Only if you want to change code and run it over USB |

## Tones and moods

**Tone** (chips above "Build my Story", changeable later):

| Tone | What it does |
|---|---|
| Classic | Your words, sentence case, a clean closing line |
| Gen Z | lowercase, "pov:" opener for short context, sarcastic closer, meme-style text (white with black outline) |
| Tanglish | Gen Z vibe in Tamil-English: converts common phrases ("having difficulty in X" → "X onnume puriyala", "can't survive X" → "X kooda survive panna mudiyala", "tomorrow" → "naalaiku") and ends with a Tanglish closer ("mudiyala da…") |

**Mood** is detected from your words, and you can tap another. There are 11: Happy, Celebration, Calm, Love, Sad, Nervous, Motivated, Adventure, Nostalgic, Grateful, Study. Each sets the text style, the music and the closing line for every tone. **Try another line** shuffles the closer.

**Long context:** your whole text is kept (up to 8 lines). Long sentences are split at commas into readable chunks; the ending (usually the punchline) is never cut. Each line stays on screen in proportion to its length (about 3 words per second). If your text needs longer than the clip, the app tells you.

**Honest limit:** this version is rule-based and offline. It restyles and frames *your* words; it doesn't invent new jokes about your topic. For that, the next step is an AI rewrite through a small backend (see Next steps).

## Find videos online (Pexels)

In step 2, **Find online** searches free stock videos on Pexels using words from your context (for example "rain", "beach", or a scene that fits the mood, like "studying at night"). Tap a clip to download and use it. Pexels videos are free to use and edit, including on social media, with no attribution required; see pexels.com/license.

One-time setup:
1. Create a free account at **pexels.com/api** and copy your API key.
2. In this GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**. Name: `PEXELS_API_KEY`, value: your key.
3. **Actions → Build APK → Run workflow**, then install the new APK.

Without the key, the app works as before and "Find online" explains how to add it.

## Install on your phone (no Android Studio needed)

1. Create a new **private** repository on GitHub and upload this whole folder (or `git push` it).
2. Open the **Actions** tab. The "Build APK" workflow starts by itself; wait about 5–8 minutes for the green tick.
3. On your phone, open the repo's **Releases** page → "Latest build" → tap **StoryMaker.apk**.
4. Android will ask to allow installs from your browser: allow it, then install.

Every time you push a change, a fresh APK appears on the same Releases page. Install it over the old one.

> The private repo's Releases page needs you to be logged into GitHub in the phone browser. Alternatively download the APK on your PC from the Actions run and copy it to the phone.

### With Android Studio instead
Open the folder in Android Studio → plug in your phone with USB debugging on → press **Run**.

## What the video follows (Instagram Story guidelines)

| Spec | Value |
|---|---|
| Size | 1080 × 1920 (9:16), video cropped to fill |
| Codec | H.264 video, AAC audio, MP4 |
| Length | First 60 seconds of the clip (one Story) |
| Bitrate | 8 Mbps |
| HDR | Converted to standard video so colours don't look washed out on Instagram |
| Text | Kept inside the safe zone: away from the top 270 px (profile bar) and bottom 380 px (reply bar) |

## Music (works like Instagram's music picker)

Tap the music row to open the picker: search, tap ▶ to preview 15 seconds, tap a song to select it, then drag the slider to choose which part plays.

| Option | What happens |
|---|---|
| **Popular & film songs** | The video is made without music. After sharing, add the song in Instagram: sticker icon → Music. Instagram licenses these songs, so they won't be muted. No outside app can access Instagram's song library. |
| **Copyright-free tracks** | Bundled in the app, mixed into the MP4. |
| **From my phone** | Any audio file you pick. Use only royalty-free tracks. |
| **No music** | Silent, or only the video's own sound. |

## Copyright

- **Music:** the three tracks in `app/src/main/res/raw` (Calm, Upbeat, Cinematic) were synthesized from scratch for this project. No samples or third-party audio. Released as CC0, so Instagram has nothing to match or mute.
- **Video:** use footage you shot yourself or royalty-free footage.
- **"Keep the video's own sound"** is off by default. Turn it on only if the clip has no copyrighted song playing in the background (e.g. at a wedding or a shop).
- To add more music, drop royalty-free `.m4a` files into `res/raw` and add them to `MusicTrack` in `StoryModel.kt`. Check the licence allows use in social media without attribution, or put the credit in your caption.

## Sharing to Instagram

- By default, **Share to Story** opens Instagram's share screen; tap **Story** there.
- To jump straight into the Story editor, Instagram requires a Facebook App ID: create a free app at developers.facebook.com, then add its ID as a repo secret named `FACEBOOK_APP_ID` (Settings → Secrets → Actions), or set `facebookAppId=` in `gradle.properties` for local builds.

## Project layout

| File | What it does |
|---|---|
| `ScriptGenerator.kt` | Context → text lines, style, music, caption. v1 is rule-based and offline. |
| `StoryRenderer.kt` | Media3 Transformer pipeline: crop, overlay, music mix, encode |
| `StoryOverlay.kt` | Draws the text on each frame with fades |
| `StoryExport.kt` | Video probing, save to gallery, share to Instagram |
| `StoryViewModel.kt` / `MainActivity.kt` | Screen and state |

## Next steps

- Replace `LocalScriptGenerator` with an LLM call through your own small backend (keeps the API key out of the APK).
- Live video preview with the same effects (Media3 ExoPlayer supports this).
- Multiple clips, beat-synced cuts.
