# Mukhar (मुखर)

**Speak English and Hindi with clarity and confidence.** Mukhar is an Android app for people who already speak a language but want to sound fluent, articulate and confident: at work, on stage, on camera, or in everyday conversation.

*Mukhar* (मुखर) means "articulate, outspoken" in Hindi.

## What it does

- **Talk practice.** Pick a random topic, speak for 1–3 minutes, and get a report on:
  - pace (words per minute)
  - filler words ("um", "like", "मतलब")
  - long pauses and repeated words
  - vocabulary variety
  - one thing you did well and one thing to work on, both quoted from your own talk
- **Say it again.** Retake the same topic and compare the two talks.
- **Words.** Learn new words one card at a time, chosen for your goal (professional, content creation, public speaking, everyday, or just cool words): Got it, Star, Later or Not for me. Words you learn come back in Revise until they stick.
- **Spaced revision.** FSRS scheduling brings words back just before you'd forget them. A quick revision mode takes 2 minutes.
- **Habit loop.** Daily streaks with freezes and a repair window, gentle reminders that back off if you ignore them, and a weekly goal.
- **Progress.** Trends, personal bests, insights, and a share card.
- **Sound Lab.** Practise sounds that Indian speakers often mix up (v/w, s/sh, ज़/ज and others).
- **Hindi is a full pillar,** not a translation. Common English borrowings are accepted, the way people actually speak.

## How talks are analysed

| Mode | Status | Notes |
|---|---|---|
| **On this phone** | Next release (1.1) | Free and offline. Speech-to-text runs on the phone with a one-time model download of about 40 MB per language, and feedback comes from built-in rules. Pace and pauses are accurate; filler counts are estimates. |
| **Your own keys** | **Available in 1.0** | Paste your own Deepgram or Sarvam key (speech-to-text) and Anthropic key (coaching). The phone calls those services directly, so Mukhar never sees your keys or your audio. |
| **Mukhar Cloud** | Coming soon | The server in `worker/` is complete but switched off. |

Whatever the mode, a talk never gets stuck: if no transcript can be written (no key yet, offline, or the service failed), Mukhar measures pace (estimated from syllables) and long pauses from the recording itself and writes delivery notes. The recording is kept so the full transcript can be written later; Settings → Recordings manages recordings and their space limit (500 MB by default).

## Repository layout

```
android/   Kotlin + Jetpack Compose app (app, core/domain, core/data, core/ui, baselineprofile)
worker/    Cloudflare Worker (Hono + D1): the Mukhar Cloud backend, currently off
shared/    Test vectors shared by the Kotlin and TypeScript tests (text normalisation, fillers, quota, grounding)
```

## Build

Requirements: JDK 21+ and the Android SDK (compileSdk 37). The app runs on Android 8.0 (API 26) and later.

```bash
cd android
./gradlew test                  # unit tests
./gradlew assembleFdroidDebug    # debug APK: app/build/outputs/apk/fdroid/debug/
```

Release builds are signed with a key that is never kept in this repo. Set these in `~/.gradle/gradle.properties`:

```
MUKHAR_RELEASE_STORE_FILE=/path/to/upload.jks
MUKHAR_RELEASE_STORE_PASSWORD=...
MUKHAR_RELEASE_KEY_ALIAS=...
MUKHAR_RELEASE_KEY_PASSWORD=...
```

Worker (optional, for Mukhar Cloud development):

```bash
cd worker
npm ci
npm test
```

## Privacy

Full policy: [PRIVACY.md](PRIVACY.md).

- Your practice history stays on your phone.
- In "On this phone" mode, nothing leaves the device.
- In "Your own keys" mode, audio and transcripts go only to the providers you chose, under your own account.
- Analytics are off unless you turn them on.

## Licence

[GPL-3.0](LICENSE). Fonts: Manrope, Fraunces and Noto Sans Devanagari, all under the SIL Open Font License (see `android/core/ui/fonts/`).
