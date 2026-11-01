# Shared test vectors

Consumed by both the Android domain tests (`android/core/domain`, JUnit) and the Worker tests (Vitest), so the Kotlin
and TypeScript implementations can't drift (LLD §1, §8). Change a vector only together with both implementations.

| File | What | Kotlin consumer |
|---|---|---|
| `normalize.json` | `norm()` cases (NFC, case, punctuation, nukta, ZWJ/ZWNJ, candrabindu) | `NormalizerTest` |
| `fillers.json` | Versioned filler lists + detection cases (pause gating, bigrams, "ki") | `FillersVectorTest` |
| `quota.json` | Quota day boundaries and `decide` cases (free/Premium, re-take, queued, caps); `baseline` cases (D-031) | `QuotaMirrorTest` (`quota_day`, `decide`); `baseline` Worker only for now |
| `grounding.json` | Grounding validator cases (LLD §4.4): exact, repaired and rejected spans, low-confidence, length cap, better-version and mistake rules, stutter mode, safety; EN, HI and Hinglish incl. nukta, ZWJ/ZWNJ, candrabindu | none yet (Worker `test/grounding.test.ts`) |

Word tuples are `[w, start_ms, end_ms, conf]`. Instants are ISO-8601 UTC.
