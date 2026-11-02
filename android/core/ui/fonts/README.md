# Bundled fonts

All three families are bundled in `src/main/res/font` (not downloadable fonts), so a first launch offline renders
correctly (design-system §2). All are SIL OFL 1.1; the licence files sit next to this README.

| File | Source | Instance | Size |
|---|---|---|---|
| `fraunces_500.ttf`, `fraunces_600.ttf` | Copied from Lantern Sudoku (`sudoku/app/src/main/res/font`), Latin subset of google/fonts `ofl/fraunces/Fraunces[SOFT,WONK,opsz,wght].ttf` | static wght 500/600, opsz 28 | 32 KB each |
| `manrope_variable.ttf` (M6) | google/fonts `ofl/manrope/Manrope[wght].ttf` (sha256 `3ae11c49…9d6ae6`, 2026-11-02) | variable, wght 400..800; Latin, Latin-1, Latin Ext-A, punctuation, ₹ | 84 KB (was 165 KB) |
| `noto_sans_devanagari_variable.ttf` (M6) | google/fonts `ofl/notosansdevanagari/NotoSansDevanagari[wdth,wght].ttf` (sha256 `14ec4af4…d03229853e`, 2026-11-02) | variable, wght 400..700, wdth pinned at 100; Devanagari + Ext + Vedic, ZWJ/ZWNJ, the same Latin set | 288 KB (was 642 KB) |

Licences: `Fraunces-OFL.txt`, `Manrope-OFL.txt`, `NotoSansDevanagari-OFL.txt` (none declares a Reserved Font Name, so
the subsets keep their names). `subset_fonts.py` rebuilds the two M6 files (fontTools, run outside the build).

**Variable fonts on API 26.** minSdk is 26, where `Typeface.Builder.setFontVariationSettings` exists, so Compose's
`Font(resId, weight)` applies `wght` from the weight (its default `variationSettings`). One file serves every weight.

**Mixed scripts.** Android's fallback chain for a resource font is the *system* fallback, and per-font custom fallback
(`Typeface.CustomFallbackBuilder`) needs API 29. So:
- Hindi content styles (`VaakTheme.type.hi*`) use Noto Sans Devanagari, which also has Latin glyphs: Hinglish shares
  one font and one baseline (design-system §2).
- English UI text that contains Hindi uses `mixedScript(text)` (`com.vaak.ui.text`), which puts Devanagari runs in
  Noto Sans Devanagari with `localeList = hi` (TalkBack switches voice) and keeps nukta and ZWJ/ZWNJ clusters whole.
- Anything else still renders: a Devanagari glyph missing from Manrope falls back to the system's Noto Devanagari.

**Still open (D-030 follow-up):** the two Fraunces cuts (72pt display, 9pt text with a true italic) are not bundled
yet; one opsz-28 cut serves both. Visual QA on a Pixel and one Xiaomi/Samsung is still due (design-system §9.2).
