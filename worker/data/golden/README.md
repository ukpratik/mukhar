# Golden set for the gate-zero STT benchmark

The benchmark (`scripts/stt-bench.ts`, LLD §10) needs about **60 clips**: 20 Indian English, 20 Hindi and 20 Hinglish,
1 to 3 minutes each, from **8 to 10 consenting adult speakers** (Q-005), with at least 6 noisy clips and natural fillers
and some grammar slips. **Audio is never committed**: `data/golden/audio/` and `manifest.json` are git-ignored.

## 1. Consent (before recording anyone)
Each speaker must be 18+ and agree, in writing (a message is fine), to this:
> "I agree that my voice recordings and their transcripts are used only to test speech-recognition providers for the
> Vaak app. They will be sent to Deepgram, Sarvam and ElevenLabs for transcription, kept on the developer's computer
> (not in any repository or cloud folder), never published, and deleted when the benchmark is finished or when I ask."

Keep a private list (outside this repo) of speaker code → name → consent date. Use only the speaker code (`S01`) here.
If someone withdraws, delete their clips and rows and re-run.

## 2. Recording
- Use the real recorder: a debug build on two budget phones (AAC-LC 32 kbps, ADTS `.aac`), so the benchmark sees what
  production will. If a provider rejects ADTS, also export an `.m4a` copy and note it (assumption A-2).
- Mix of prompts from the app's tracks (interviews, work updates, creators, daily life). Let people talk naturally:
  do not ask them to avoid "um" or English words.
- At least 6 clips in real noise (fan, traffic, kitchen, café).
- Name files `<set>-<nnn>.aac` (for example `hinglish-007.aac`) in `data/golden/audio/`.

## 3. Verbatim reference transcripts
- Write exactly what was said: every "um", "uh", "umm", "हम्म", "मतलब", repeated word, false start and grammar slip.
- Hindi in Devanagari. Hinglish: English words in Latin script, Hindi words in Devanagari (the way the app shows it).
  The report also has an indicative script-agnostic WER for words written in the other script.
- A draft from one provider is fine to start from, but correct it by **listening**, and note which provider drafted it
  (it biases WER in that provider's favour).
- Add deliberate non-standard phrases you want kept verbatim to `verbatim_checks` (for example "discussed about").

## 4. Manifest
Copy `manifest.example.json` to `manifest.json` and add one entry per clip:

| Field | Meaning |
|---|---|
| `id` | Unique clip ID |
| `audio` | Path relative to this folder (`audio/en-001.aac`) |
| `lang` | Language sent to the provider: `en` or `hi` (Hinglish clips use `hi`) |
| `set` | Reporting group: `en`, `hi` or `hinglish` |
| `reference` | Verbatim transcript |
| `speaker`, `noisy` | Speaker code and noise flag (for slicing results) |
| `verbatim_checks` | Optional phrases a provider must not "correct" |
| `keyterms`, `mime` | Optional keyterms; MIME type if the extension isn't enough |

## 5. Run
```sh
cd worker
node scripts/stt-bench.ts --dry-run                       # checks the manifest and that every audio file exists
node scripts/stt-bench.ts --limit 2                       # smoke run, 2 clips per set
DEEPGRAM_API_KEY=… SARVAM_API_KEY=… ELEVENLABS_API_KEY=… node scripts/stt-bench.ts
```
Providers without a key are skipped. The summary goes to `reports/stt-benchmark-<date>.md`; per-clip transcripts go to
a git-ignored `.json` next to it. Delete the audio when gate zero is done (or keep it offline, per the consent).
