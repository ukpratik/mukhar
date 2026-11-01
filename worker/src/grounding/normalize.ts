/**
 * `norm(t)` (LLD §4.2), byte-for-byte the same rules as the Kotlin `Normalizer` (shared/test-vectors/normalize.json):
 * NFC → lowercase → remove nukta U+093C, ZWNJ U+200C, ZWJ U+200D → candrabindu ँ → anusvara ं
 * → strip Unicode punctuation (incl. । ॥) → collapse whitespace → trim.
 */
const NUKTA = "़";
const ZWNJ = "‌";
const ZWJ = "‍";
const CANDRABINDU = "ँ";
const ANUSVARA = "ं";
const PUNCT = /\p{P}/u;
const SPACE = /\s/u;

export function norm(text: string): string {
  const nfc = text.normalize("NFC").toLowerCase();
  let out = "";
  let pendingSpace = false;
  for (const ch of nfc) {
    if (ch === NUKTA || ch === ZWNJ || ch === ZWJ) continue;
    if (PUNCT.test(ch)) continue;
    if (SPACE.test(ch)) {
      pendingSpace = out.length > 0;
      continue;
    }
    if (pendingSpace) out += " ";
    pendingSpace = false;
    out += ch === CANDRABINDU ? ANUSVARA : ch;
  }
  return out;
}

/** Normalised tokens of a phrase (split on spaces after `norm`), dropping empties. */
export function normTokens(text: string): string[] {
  const n = norm(text);
  return n.length === 0 ? [] : n.split(" ");
}
