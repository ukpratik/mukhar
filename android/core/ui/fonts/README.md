# Bundled fonts

| File | Source | Instance |
|---|---|---|
| `fraunces_500.ttf`, `fraunces_600.ttf` | Copied from Lantern Sudoku (`sudoku/app/src/main/res/font`), Latin subset of google/fonts `ofl/fraunces/Fraunces[SOFT,WONK,opsz,wght].ttf` | wght 500/600, opsz 28 |

Licence: SIL OFL 1.1 (`Fraunces-OFL.txt`).

**M0 shortcut (to replace in M3/M6):** design-system §2/§9.2 asks for two Fraunces cuts (72pt display, 9pt text incl.
italic), Manrope variable and Noto Sans Devanagari variable, all bundled. For now one opsz-28 Fraunces cut serves both
serif families (no true italic), and Manrope / Noto Sans Devanagari fall back to the system sans-serif (Android ships
Noto Sans Devanagari, so Hindi renders correctly).
