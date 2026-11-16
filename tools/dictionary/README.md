# Offline dictionary (D-056)

`build_dictionary.py` builds `android/core/data/src/main/assets/dict_en.jsonl` and `dict_hi.jsonl` from open data:

| Data | Source | Licence |
|---|---|---|
| English words, meanings, examples, IPA, irregular forms | Open English WordNet 2025 (https://en-word.net) | CC BY 4.0 |
| Hindi words, English meanings, examples, romanisation, inflected forms; Hindi words for English entries | English Wiktionary via kaikki.org | CC BY-SA 4.0 (and GFDL) |
| Word frequency for filtering and ranking | FrequencyWords en_50k, hi_full (Hermit Dave) | MIT |

`dict_hi.jsonl` is a derivative of Wiktionary and is shared under CC BY-SA 4.0. The app shows the source and licence on every word page.

Run from the repo root (standard library only; downloads into `tools/dictionary/.cache`, which is git-ignored):

```
python3 tools/dictionary/build_dictionary.py
```

When the output changes, bump `DictionaryStore.VERSION` so phones re-import it.
