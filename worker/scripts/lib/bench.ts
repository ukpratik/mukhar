import { norm } from "../../src/grounding/normalize.ts";

/**
 * Pure metrics for the gate-zero STT benchmark (LLD §10, NFR-003). No Node APIs, so Vitest covers it in workerd.
 */

// ---------------------------------------------------------------- normalisation
const DEVANAGARI_DIGITS = "०१२३४५६७८९";

/** WER tokens: norm() (NFC, case, punctuation, nukta, ZWJ/ZWNJ, candrabindu) + Devanagari digits → ASCII. */
export function werTokens(text: string): string[] {
  const n = norm(text).replace(/[०-९]/gu, (d) => String(DEVANAGARI_DIGITS.indexOf(d)));
  return n.length === 0 ? [] : n.split(" ");
}

// Rough Devanagari → Latin romanisation for an *indicative* script-agnostic WER (Hinglish may be written in either
// script by the reference and by a provider). Words are reduced to a consonant skeleton so "matlab" and "मतलब" meet;
// the pass rule always uses the strict WER.
const CONS: Record<string, string> = {
  क: "k", ख: "kh", ग: "g", घ: "gh", ङ: "n", च: "ch", छ: "chh", ज: "j", झ: "jh", ञ: "n", ट: "t", ठ: "th", ड: "d", ढ: "dh", ण: "n",
  त: "t", थ: "th", द: "d", ध: "dh", न: "n", प: "p", फ: "f", ब: "b", भ: "bh", म: "m", य: "y", र: "r", ल: "l", व: "v", श: "sh",
  ष: "sh", स: "s", ह: "h",
};
const VOWELS: Record<string, string> = { अ: "a", आ: "a", इ: "i", ई: "i", उ: "u", ऊ: "u", ऋ: "ri", ए: "e", ऐ: "ai", ओ: "o", औ: "au", ऑ: "o", ऍ: "e" };
const MATRAS: Record<string, string> = { "ा": "a", "ि": "i", "ी": "i", "ु": "u", "ू": "u", "ृ": "ri", "े": "e", "ै": "ai", "ो": "o", "ौ": "au", "ॉ": "o", "ॅ": "e" };

export function romanize(token: string): string {
  if (!/[\u0900-\u097F]/u.test(token)) return token;
  let out = "";
  for (const ch of token) {
    if (CONS[ch] !== undefined) out += CONS[ch];
    else if (VOWELS[ch] !== undefined) out += VOWELS[ch];
    else if (MATRAS[ch] !== undefined) out += MATRAS[ch];
    else if (ch === "ं") out += "n";
    else if (ch === "्" || ch === "ः") continue;
    else out += ch;
  }
  return out;
}

/** Consonant skeleton: romanise, drop vowels, collapse repeats ("matlab" → "mtlb", "मतलब" → "mtlb"). */
export function skeleton(token: string): string {
  const s = romanize(token).replace(/[aeiou]/g, "").replace(/chh/g, "ch").replace(/(ch)+/g, "ch").replace(/(.)\1+/g, "$1");
  return s.length > 0 ? s : token;
}

export const scriptAgnostic = (tokens: string[]) => tokens.map(skeleton);

// ---------------------------------------------------------------- WER
export interface WerResult { substitutions: number; deletions: number; insertions: number; refWords: number; wer: number }

export function wer(ref: string[], hyp: string[]): WerResult {
  const n = ref.length;
  const m = hyp.length;
  // dp over (cost, S, D, I)
  type Cell = [number, number, number, number];
  let prev: Cell[] = Array.from({ length: m + 1 }, (_, j) => [j, 0, 0, j] as Cell);
  for (let i = 1; i <= n; i++) {
    const cur: Cell[] = [[i, 0, i, 0]];
    for (let j = 1; j <= m; j++) {
      const sub = prev[j - 1]!;
      const del = prev[j]!;
      const ins = cur[j - 1]!;
      const same = ref[i - 1] === hyp[j - 1];
      const options: Cell[] = [
        [sub[0] + (same ? 0 : 1), sub[1] + (same ? 0 : 1), sub[2], sub[3]],
        [del[0] + 1, del[1], del[2] + 1, del[3]],
        [ins[0] + 1, ins[1], ins[2], ins[3] + 1],
      ];
      cur[j] = options.reduce((a, b) => (b[0] < a[0] ? b : a));
    }
    prev = cur;
  }
  const [cost, s, d, ins] = prev[m]!;
  return { substitutions: s, deletions: d, insertions: ins, refWords: n, wer: n === 0 ? (m === 0 ? 0 : 1) : cost / n };
}

// ---------------------------------------------------------------- fillers (unambiguous set only)
export interface FillerLists { en: { unambiguous: string[] }; hi: { unambiguous: string[] } }

/** Spelling variants of the same filler count as one class (umm = um, हम्म = hmm …). */
const FILLER_CLASS: Record<string, string> = {
  um: "UM", umm: "UM", उम्म: "UM", उम: "UM", uh: "UH", er: "UH", erm: "UH", hmm: "HMM", हम्म: "HMM", हम: "HMM",
  aa: "AA", आ: "AA", अ: "AA", matlab: "MATLAB", मतलब: "MATLAB",
};

export function unambiguousSet(lists: FillerLists, set: "en" | "hi" | "hinglish"): Set<string> {
  const words = set === "en" ? lists.en.unambiguous : [...lists.hi.unambiguous, ...lists.en.unambiguous];
  return new Set(words.map((w) => norm(w)));
}

function fillerCounts(tokens: string[], allowed: Set<string>): Map<string, number> {
  const m = new Map<string, number>();
  for (const t of tokens) {
    if (!allowed.has(t)) continue;
    const k = FILLER_CLASS[t] ?? t;
    m.set(k, (m.get(k) ?? 0) + 1);
  }
  return m;
}

export interface FillerResult { refFillers: number; hypFillers: number; matched: number; recall: number | null; precision: number | null }

export function fillerScore(ref: string[], hyp: string[], allowed: Set<string>): FillerResult {
  const r = fillerCounts(ref, allowed);
  const h = fillerCounts(hyp, allowed);
  let matched = 0;
  for (const [k, n] of r) matched += Math.min(n, h.get(k) ?? 0);
  const refFillers = [...r.values()].reduce((a, b) => a + b, 0);
  const hypFillers = [...h.values()].reduce((a, b) => a + b, 0);
  return { refFillers, hypFillers, matched, recall: refFillers ? matched / refFillers : null, precision: hypFillers ? matched / hypFillers : null };
}

// ---------------------------------------------------------------- false corrections
/** Verbatim checks are deliberate non-standard phrases in the reference ("discussed about"); missing in the hypothesis = the provider "corrected" the speaker. */
export function falseCorrections(hyp: string[], checks: string[]): number {
  const h = ` ${hyp.join(" ")} `;
  return checks.filter((c) => !h.includes(` ${werTokens(c).join(" ")} `)).length;
}

// ---------------------------------------------------------------- aggregation
export function percentile(values: number[], p: number): number | null {
  if (values.length === 0) return null;
  const s = [...values].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.ceil((p / 100) * s.length) - 1)]!;
}

export interface PassRule { maxWer: number; minFillerRecall: number; maxP90Ms: number }
export const PASS_RULE: PassRule = { maxWer: 0.15, minFillerRecall: 0.7, maxP90Ms: 8000 };

export interface GroupSummary {
  clips: number;
  errors: number;
  wer: number | null;
  werScriptAgnostic: number | null;
  fillerRecall: number | null;
  fillerPrecision: number | null;
  falseCorrections: number;
  timestampsOk: boolean;
  confidenceOk: boolean;
  p50Ms: number | null;
  p90Ms: number | null;
  costUsd: number;
  pass: boolean;
  failReasons: string[];
}

export interface ClipResult {
  ok: boolean;
  ms: number;
  werStrict?: WerResult;
  werAgnostic?: WerResult;
  fillers?: FillerResult;
  falseCorrections?: number;
  timestampsOk?: boolean;
  hasConfidence?: boolean;
  costMicroUsd?: number;
}

/** Corpus-level WER (sum of edits / sum of reference words), pooled filler recall. */
export function summarize(results: ClipResult[], rule: PassRule = PASS_RULE): GroupSummary {
  const ok = results.filter((r) => r.ok);
  const sum = (f: (r: ClipResult) => number) => ok.reduce((a, r) => a + f(r), 0);
  const refWords = sum((r) => r.werStrict?.refWords ?? 0);
  const edits = (w?: WerResult) => (w ? w.substitutions + w.deletions + w.insertions : 0);
  const werS = refWords ? sum((r) => edits(r.werStrict)) / refWords : null;
  const werA = refWords ? sum((r) => edits(r.werAgnostic)) / refWords : null;
  const refF = sum((r) => r.fillers?.refFillers ?? 0);
  const hypF = sum((r) => r.fillers?.hypFillers ?? 0);
  const matched = sum((r) => r.fillers?.matched ?? 0);
  const lat = ok.map((r) => r.ms);
  const s: GroupSummary = {
    clips: results.length,
    errors: results.length - ok.length,
    wer: werS,
    werScriptAgnostic: werA,
    fillerRecall: refF ? matched / refF : null,
    fillerPrecision: hypF ? matched / hypF : null,
    falseCorrections: sum((r) => r.falseCorrections ?? 0),
    timestampsOk: ok.length > 0 && ok.every((r) => r.timestampsOk),
    confidenceOk: ok.length > 0 && ok.every((r) => r.hasConfidence),
    p50Ms: percentile(lat, 50),
    p90Ms: percentile(lat, 90),
    costUsd: sum((r) => r.costMicroUsd ?? 0) / 1e6,
    pass: false,
    failReasons: [],
  };
  if (s.wer === null || s.wer > rule.maxWer) s.failReasons.push(`WER ${fmtPct(s.wer)} > ${rule.maxWer * 100}%`);
  if (s.fillerRecall === null || s.fillerRecall < rule.minFillerRecall) s.failReasons.push(`filler recall ${fmtPct(s.fillerRecall)} < ${rule.minFillerRecall * 100}%`);
  if (!s.timestampsOk) s.failReasons.push("word timestamps missing/implausible");
  if (!s.confidenceOk) s.failReasons.push("no per-word confidence");
  if (s.p90Ms === null || s.p90Ms > rule.maxP90Ms) s.failReasons.push(`p90 ${s.p90Ms ?? "–"} ms > ${rule.maxP90Ms} ms`);
  if (s.errors > 0) s.failReasons.push(`${s.errors} failed call(s)`);
  s.pass = s.failReasons.length === 0;
  return s;
}

export const fmtPct = (x: number | null) => (x === null ? "–" : `${(x * 100).toFixed(1)}%`);

/** Plausible timestamps: monotonic, positive durations, within the clip. */
export function timestampsPlausible(words: Array<{ s: number; e: number }>, durationMs: number): boolean {
  if (words.length === 0) return false;
  let last = -1;
  for (const w of words) {
    if (w.e <= w.s || w.s < last - 50 || (durationMs > 0 && w.e > durationMs + 1000)) return false;
    last = w.s;
  }
  return true;
}
