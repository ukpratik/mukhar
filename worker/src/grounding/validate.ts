import type { FeedbackMetrics } from "../llm/prompt.ts";
import type { BetterVersion, Glow, Grow, Mistake, ModelFeedback, Safety, UpgradedWord } from "../llm/schema.ts";
import { fallbackGlow, fallbackGrow, type FallbackGlow, type FallbackGrow } from "./fallback.ts";
import { norm, normTokens } from "./normalize.ts";
import { selfHarmKeywordHit } from "./safety.ts";

/** Grounding validation (LLD §4.4, US-014/015). Pure: same input, same output. */
export const MAX_SPAN_TOKENS = 40; // e − s ≤ 40
export const REPAIR_WINDOW = 5;
export const MAX_ITEMS = 3;

export interface Token { w: string; lc: boolean }

export interface GroundingInput {
  feedback: ModelFeedback;
  tokens: Token[];
  lang: "en" | "hi";
  stutterFriendly: boolean;
  metrics: FeedbackMetrics;
}

export interface ValidatedFeedback {
  summary: string;
  glow: Glow | FallbackGlow;
  grow: Grow | FallbackGrow;
  better_versions: BetterVersion[];
  mistakes: Mistake[];
  words_for_you: UpgradedWord[];
}

export interface GroundingResult {
  feedback: ValidatedFeedback;
  dropped: number;
  fallback: { glow: boolean; grow: boolean };
  safety: Safety;
  /** kept model glow + grow / 2, before fallbacks (NFR-004 pre-filter grounding rate). */
  preFilterGroundingRate: number;
}

const collapse = (s: string) => s.split(" ").filter(Boolean).join(" ");

/** Joined normalised tokens s..e (empty tokens, e.g. punctuation-only, are skipped). */
function joinSpan(T: string[], s: number, e: number): string {
  return collapse(T.slice(s, e + 1).join(" "));
}

export function makeSpanChecker(tokens: Token[]) {
  const n = tokens.length;
  const T = tokens.map((t) => norm(t.w));
  const lowConf = tokens.map((t) => t.lc);
  const clean = (s: number, e: number) => {
    for (let i = s; i <= e; i++) if (lowConf[i]) return false;
    return true;
  };
  const bounded = (s: number, e: number) => Number.isInteger(s) && Number.isInteger(e) && 0 <= s && s <= e && e < n && e - s <= MAX_SPAN_TOKENS;

  /** Returns the (possibly repaired) span, or null to reject. */
  return function spanOk(s: number, e: number, quote: string): { start: number; end: number; q: string } | null {
    const Q = collapse(norm(quote));
    if (Q.length === 0) return null;
    if (bounded(s, e) && clean(s, e) && joinSpan(T, s, e) === Q) return { start: s, end: e, q: Q };
    // Off-by-few repair: find Q as a contiguous token run within s−5..e+5, nearest to the claimed start.
    if (!Number.isInteger(s) || !Number.isInteger(e)) return null;
    const qLen = Q.split(" ").length;
    const lo = Math.max(0, s - REPAIR_WINDOW);
    const hi = Math.min(n - 1, e + REPAIR_WINDOW);
    let best: { start: number; end: number } | null = null;
    for (let s2 = lo; s2 <= hi; s2++) {
      // allow for empty (punctuation-only) tokens inside the run
      for (let e2 = s2 + qLen - 1; e2 <= Math.min(hi, s2 + qLen - 1 + 3); e2++) {
        if (!bounded(s2, e2) || !clean(s2, e2) || T[s2] === "" || joinSpan(T, s2, e2) !== Q) continue;
        if (!best || Math.abs(s2 - s) < Math.abs(best.start - s)) best = { start: s2, end: e2 };
        break;
      }
    }
    return best ? { ...best, q: Q } : null;
  };
}

/** True if the normalised phrase appears as a whole-token run inside the normalised haystack. */
export function containsPhrase(haystack: string, phrase: string): boolean {
  const h = normTokens(haystack);
  const p = normTokens(phrase);
  if (p.length === 0) return false;
  for (let i = 0; i + p.length <= h.length; i++) {
    let ok = true;
    for (let k = 0; k < p.length; k++) if (h[i + k] !== p[k]) { ok = false; break; }
    if (ok) return true;
  }
  return false;
}

const PAUSE_TALK = /paus|silen|रुक|ठहर|विराम/iu;

export function validate(input: GroundingInput): GroundingResult {
  const { feedback: f, tokens, lang, stutterFriendly, metrics } = input;
  const spanOk = makeSpanChecker(tokens);
  let dropped = 0;

  // glow
  let glow: Glow | FallbackGlow;
  const g = spanOk(f.glow.start, f.glow.end, f.glow.quote);
  const glowOk = g !== null;
  if (g) glow = { ...f.glow, start: g.start, end: g.end };
  else { dropped++; glow = fallbackGlow(metrics, lang); }

  // grow (stutter-friendly: never repetition or pauses)
  let grow: Grow | FallbackGrow;
  const r = spanOk(f.grow.start, f.grow.end, f.grow.quote);
  const stutterViolation = stutterFriendly &&
    (f.grow.issue === "repetition" || (f.grow.issue === "pace" && PAUSE_TALK.test(`${f.grow.why} ${f.grow.how}`)));
  const growOk = r !== null && !stutterViolation;
  if (r && !stutterViolation) grow = { ...f.grow, start: r.start, end: r.end };
  else { dropped++; grow = fallbackGrow(metrics, lang, stutterFriendly); }

  // better versions
  const better: BetterVersion[] = [];
  for (const b of f.better_versions) {
    if (better.length >= MAX_ITEMS) { dropped++; continue; }
    const sp = spanOk(b.start, b.end, b.quote);
    const rewriteNorm = collapse(norm(b.rewrite));
    if (!sp || rewriteNorm.length === 0 || rewriteNorm === sp.q || b.rewrite.length > 2 * b.quote.length + 60) { dropped++; continue; }
    const words = b.upgraded_words.filter((w) => containsPhrase(b.rewrite, w.word) && !containsPhrase(b.quote, w.word));
    if (words.length === 0) { dropped++; continue; }
    better.push({ ...b, start: sp.start, end: sp.end, upgraded_words: words });
  }

  // mistakes
  const mistakes: Mistake[] = [];
  for (const m of f.mistakes) {
    if (mistakes.length >= MAX_ITEMS) { dropped++; continue; }
    const sp = spanOk(m.start, m.end, m.quote);
    const fixNorm = collapse(norm(m.fix));
    if (!sp || fixNorm.length === 0 || fixNorm === sp.q) { dropped++; continue; }
    mistakes.push({ ...m, start: sp.start, end: sp.end });
  }

  // words for you (US-021): de-duplicated upgraded words of kept better versions
  const seen = new Set<string>();
  const wordsForYou: UpgradedWord[] = [];
  for (const b of better) for (const w of b.upgraded_words) {
    const k = norm(w.word);
    if (!seen.has(k)) { seen.add(k); wordsForYou.push(w); }
  }

  const preFilterGroundingRate = ((glowOk ? 1 : 0) + (growOk ? 1 : 0)) / 2;

  // safety: model flag or keyword backstop → helpline card, no coaching items
  const keywordHit = selfHarmKeywordHit(tokens.map((t) => t.w));
  const safety: Safety = f.safety === "self_harm" || keywordHit ? "self_harm" : f.safety;
  if (safety === "self_harm") {
    return {
      feedback: { summary: "", glow: fallbackGlow(metrics, lang), grow: fallbackGrow(metrics, lang, stutterFriendly), better_versions: [], mistakes: [], words_for_you: [] },
      dropped,
      fallback: { glow: true, grow: true },
      safety,
      preFilterGroundingRate,
    };
  }

  return {
    feedback: { summary: f.summary, glow, grow, better_versions: better, mistakes, words_for_you: wordsForYou },
    dropped,
    fallback: { glow: !glowOk, grow: !growOk },
    safety,
    preFilterGroundingRate,
  };
}
