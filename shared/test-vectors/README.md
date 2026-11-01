# Shared test vectors

Consumed by both the Android domain tests (`android/core/domain`, JUnit) and the Worker tests (Vitest), so the Kotlin
and TypeScript implementations can't drift (LLD §1, §8). Change a vector only together with both implementations.

| File | What | Kotlin consumer |
|---|---|---|
| `normalize.json` | `norm()` cases (NFC, case, punctuation, nukta, ZWJ/ZWNJ, candrabindu) | `NormalizerTest` |
| `fillers.json` | Versioned filler lists + detection cases (pause gating, bigrams, "ki") | `FillersVectorTest` |
| `quota.json` | Quota day boundaries and `decide` cases (free/Premium, re-take, queued, caps) | `QuotaMirrorTest` |

Word tuples are `[w, start_ms, end_ms, conf]`. Instants are ISO-8601 UTC.
