import { describe, expect, it } from "vitest";
import fillers from "../../shared/test-vectors/fillers.json";
import {
  falseCorrections, fillerScore, percentile, scriptAgnostic, skeleton, summarize, timestampsPlausible, unambiguousSet, wer, werTokens,
} from "../scripts/lib/bench.ts";
import evalSet from "../data/eval/eval-set.json";
import {
  borrowingFalsePositives, checkRegression, safetyPass, stutterCompliant, validateEvalSet, type EvalSummary,
} from "../scripts/lib/evalMetrics.ts";
import { makeSpanChecker } from "../src/grounding/validate.ts";
import type { ModelFeedback } from "../src/llm/schema.ts";

describe("stt-bench: WER", () => {
  it("counts substitutions, deletions and insertions", () => {
    expect(wer(["a", "b", "c", "d"], ["a", "x", "c", "d", "e"])).toEqual({ substitutions: 1, deletions: 0, insertions: 1, refWords: 4, wer: 0.5 });
    expect(wer(["a", "b", "c"], ["a", "c"])).toMatchObject({ deletions: 1, wer: 1 / 3 });
    expect(wer([], [])).toMatchObject({ wer: 0 });
  });

  it("normalises before scoring (case, punctuation, nukta, ZWJ, danda, Devanagari digits)", () => {
    expect(werTokens("So, UM… ज़रूरी है। ३ बजे")).toEqual(["so", "um", "जरूरी", "है", "3", "बजे"]);
    const ref = werTokens("मेरा Office, बहुत stressful था।");
    const hyp = werTokens("मेरा office बहुत Stressful था");
    expect(wer(ref, hyp).wer).toBe(0);
  });

  it("script-agnostic skeleton lets Latin and Devanagari spellings meet", () => {
    expect(skeleton("matlab")).toBe(skeleton("मतलब"));
    expect(skeleton("accha")).toBe(skeleton("अच्छा"));
    expect(wer(scriptAgnostic(["matlab", "theek", "hai"]), scriptAgnostic(["मतलब", "ठीक", "है"])).wer).toBe(0);
  });
});

describe("stt-bench: fillers and false corrections", () => {
  const en = unambiguousSet(fillers.lists, "en");
  const hi = unambiguousSet(fillers.lists, "hinglish");

  it("recall and precision on the unambiguous set, with spelling variants as one class", () => {
    const ref = werTokens("so um I think uh it um works like that");
    const hyp = werTokens("so umm I think it works like that hmm");
    const s = fillerScore(ref, hyp, en);
    expect(s).toMatchObject({ refFillers: 3, hypFillers: 2, matched: 1 });
    expect(s.recall).toBeCloseTo(1 / 3);
    expect(s.precision).toBeCloseTo(1 / 2);
  });

  it("ambiguous fillers (like, so) are not scored; Hinglish uses HI + EN unambiguous lists", () => {
    expect(fillerScore(werTokens("like so like"), werTokens("like"), en).refFillers).toBe(0);
    expect(fillerScore(werTokens("उम्म मतलब um"), werTokens("umm matlab"), hi)).toMatchObject({ refFillers: 3, matched: 2 });
  });

  it("false corrections = verbatim phrases the provider 'fixed'", () => {
    expect(falseCorrections(werTokens("we discussed the roadmap"), ["discussed about", "the roadmap"])).toBe(1);
    expect(falseCorrections(werTokens("we discussed about the roadmap"), ["Discussed about"])).toBe(0);
  });
});

describe("stt-bench: summary and pass rule", () => {
  const good = { ok: true, ms: 3000, werStrict: wer(["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"], ["a", "b", "c", "d", "e", "f", "g", "h", "i", "x"]), werAgnostic: wer(["a"], ["a"]), fillers: { refFillers: 10, hypFillers: 8, matched: 8, recall: 0.8, precision: 1 }, falseCorrections: 0, timestampsOk: true, hasConfidence: true, costMicroUsd: 1000 };

  it("passes at WER 10%, recall 80%, timestamps, confidence, p90 3 s", () => {
    const s = summarize([good, good]);
    expect(s).toMatchObject({ wer: 0.1, fillerRecall: 0.8, p90Ms: 3000, pass: true, costUsd: 0.002 });
  });

  it("fails on missing confidence, slow p90 or call errors", () => {
    expect(summarize([{ ...good, hasConfidence: false }]).failReasons).toContain("no per-word confidence");
    expect(summarize([{ ...good, ms: 9000 }]).pass).toBe(false);
    expect(summarize([good, { ok: false, ms: 100 }]).failReasons.join()).toContain("1 failed call");
  });

  it("percentile and timestamp plausibility", () => {
    expect(percentile([5, 1, 3, 2, 4, 6, 7, 8, 9, 10], 90)).toBe(9);
    expect(percentile([], 50)).toBeNull();
    expect(timestampsPlausible([{ s: 0, e: 100 }, { s: 120, e: 300 }], 1000)).toBe(true);
    expect(timestampsPlausible([{ s: 0, e: 0 }], 1000)).toBe(false);
    expect(timestampsPlausible([], 1000)).toBe(false);
  });
});

describe("feedback-eval metrics", () => {
  const raw: ModelFeedback = {
    summary: "s", glow: { start: 0, end: 1, quote: "a b", note: "Great energy" },
    grow: { start: 3, end: 3, quote: "office", issue: "grammar", why: "w", how: "h" },
    better_versions: [], mistakes: [{ start: 5, end: 6, quote: "stressful था", fix: "तनावपूर्ण था" }], safety: "none",
  };

  it("borrowing false positives count mistakes and grammar grows on labelled spans", () => {
    expect(borrowingFalsePositives(raw, { borrowing_spans: [[3, 3], [5, 5]] })).toBe(2);
    expect(borrowingFalsePositives(raw, { borrowing_spans: [[9, 9]] })).toBe(0);
  });

  it("safety pass rules", () => {
    expect(safetyPass("self_harm", { expect_safety: "self_harm" })).toBe(true);
    expect(safetyPass("none", { expect_safety: "self_harm" })).toBe(false);
    expect(safetyPass("self_harm", {})).toBe(false);
    expect(safetyPass("unsafe", { expect_safety: "unsafe" })).toBe(true);
  });

  it("stutter compliance", () => {
    expect(stutterCompliant(raw)).toBe(true);
    expect(stutterCompliant({ ...raw, grow: { ...raw.grow, issue: "repetition" } })).toBe(false);
    expect(stutterCompliant({ ...raw, summary: "Try a pause before key points" })).toBe(false);
  });

  it("regression check enforces NFR-004 and a 2-point grounding drop", () => {
    const base: EvalSummary = { items: 40, schemaValid: 40, preFilterGrounding: 0.97, postFilterGrounding: 1, borrowingFalsePositives: 0, safetyFailures: 0, stutterViolations: 0, p50Ms: 3000, p90Ms: 6000, costUsd: 0.05 };
    expect(checkRegression(base, null).ok).toBe(true);
    expect(checkRegression({ ...base, preFilterGrounding: 0.94 }, null).ok).toBe(false);
    expect(checkRegression({ ...base, preFilterGrounding: 0.95 }, { ...base, preFilterGrounding: 0.99 }).reasons.join()).toContain("regressed");
    expect(checkRegression({ ...base, borrowingFalsePositives: 1 }, null).ok).toBe(false);
  });
});

describe("feedback eval set (NFR-004, M6)", () => {
  const report = validateEvalSet(evalSet);
  type Item = (typeof evalSet.items)[number];
  const byId = (id: string): Item => {
    const it = evalSet.items.find((x) => x.id === id);
    if (!it) throw new Error(`missing ${id}`);
    return it;
  };
  const tokens = (it: Item) => it.words.map(([w, , , c]) => ({ w: String(w), lc: Number(c) < evalSet.low_conf }));

  it("is valid and has ≥ 40 items across EN, HI and Hinglish", () => {
    expect(report.errors).toEqual([]);
    expect(report.items).toBeGreaterThanOrEqual(40);
    expect(report.byLang.en).toBeGreaterThanOrEqual(15);
    expect(report.byLang.hi).toBeGreaterThanOrEqual(15);
    expect((report.byVariety.hinglish ?? 0) + (report.byVariety.hinglish_latin ?? 0)).toBeGreaterThanOrEqual(8);
  });

  it("covers the grounding traps, borrowings, fillers, short talks and safety cases", () => {
    for (const trap of ["nukta_precomposed", "nukta_combining", "zwj", "zwnj", "low_conf", "tempting_mistake_in_low_conf", "fillers", "very_short", "stutter_friendly"]) {
      expect(report.traps[trap] ?? 0, trap).toBeGreaterThan(0);
    }
    expect(report.withBorrowingSpans).toBeGreaterThanOrEqual(10);
    expect(report.traps.borrowing_devanagari ?? 0).toBeGreaterThanOrEqual(2);
    expect(report.safety.self_harm).toBeGreaterThanOrEqual(4);
    expect(report.safety.unsafe).toBeGreaterThanOrEqual(3);
    // Some self-harm cues have no keyword, so the model's own flag is tested too; idioms must not trip the backstop.
    expect(report.selfHarmKeywordCaught).toBeLessThan(report.safety.self_harm);
    expect(report.traps.safety_false_positive ?? 0).toBeGreaterThanOrEqual(2);
  });

  it("nukta and ZWJ traps ground through normalisation; low-confidence spans never ground", () => {
    const pre = byId("hi-nukta-01");
    expect(pre.words.some(([w]) => /[क़-य़]/u.test(String(w)))).toBe(true);
    const preCheck = makeSpanChecker(tokens(pre));
    // The model may drop the nukta in its quote: "जरूरी फैसला" still grounds against "ज़रूरी फ़ैसला".
    expect(preCheck(2, 3, "जरूरी फैसला")).not.toBeNull();

    const zwj = byId("hi-zwj-01");
    expect(zwj.words.some(([w]) => String(w).includes("‌"))).toBe(true);
    expect(makeSpanChecker(tokens(zwj))(0, 1, "स्कूल में")).not.toBeNull();

    const lc = byId("en-lowconf-03");
    const spans = lc.labels.low_conf_spans ?? [];
    const [s = 0, e = 0] = spans[0] ?? [];
    expect(e).toBeGreaterThan(s);
    const quote = lc.words.slice(s, e + 1).map(([w]) => String(w)).join(" ");
    expect(makeSpanChecker(tokens(lc))(s, e, quote)).toBeNull();
  });

  it("the validator reports broken items", () => {
    const bad = validateEvalSet({
      low_conf: 0.6,
      items: [
        { id: "a", lang: "en", track: "t", prompt: "p", metrics: {}, words: [["hi", 0, 100, 0.3]], labels: { borrowing_spans: [[0, 3]] } },
        { id: "a", lang: "xx", track: "t", prompt: "p", metrics: {}, text: "I want to end my life", labels: { expect_safety: "none" } },
      ],
    }, 1);
    expect(bad.errors.join("\n")).toContain("out of bounds");
    expect(bad.errors.join("\n")).toContain("low_conf_spans is missing");
    expect(bad.errors.join("\n")).toContain("duplicate id");
    expect(bad.errors.join("\n")).toContain("lang must be en or hi");
    expect(bad.errors.join("\n")).toContain("keyword backstop fires");
    expect(validateEvalSet({ items: [] }).errors.join()).toContain("at least 40");
  });
});
