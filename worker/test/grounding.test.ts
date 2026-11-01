import { describe, expect, it } from "vitest";
import vectors from "../../shared/test-vectors/grounding.json";
import { containsPhrase, validate } from "../src/grounding/validate.ts";
import { fallbackGlow, fallbackGrow } from "../src/grounding/fallback.ts";
import { selfHarmKeywordHit } from "../src/grounding/safety.ts";
import { parseFeedback } from "../src/llm/schema.ts";

type Span = [number, number] | "fallback";
type V = (typeof vectors.cases)[number];

const metrics = { wpm: 138, wpm_band: "comfortable", fillers_per_min: 6.2, long_pauses_per_min: 1, repetitions: 2, speech_s: 112 };

function spanOf(item: { start: number | null; end: number | null; fallback?: boolean }): Span {
  return "fallback" in item && item.fallback ? "fallback" : [item.start as number, item.end as number];
}

describe("grounding validator: shared/test-vectors/grounding.json", () => {
  it.each(vectors.cases.map((c) => [c.name, c] as const))("%s", (_name, c: V) => {
    const tokens = c.words.map((w) => ({ w: w[0] as string, lc: (w[3] as number) < vectors.low_conf }));
    const r = validate({
      feedback: parseFeedback(c.output),
      tokens,
      lang: c.lang as "en" | "hi",
      stutterFriendly: c.stutter_friendly,
      metrics,
    });
    const e = c.expected;
    expect(spanOf(r.feedback.glow)).toEqual(e.glow);
    expect(spanOf(r.feedback.grow)).toEqual(e.grow);
    expect(r.feedback.better_versions.map((b) => [b.start, b.end, b.upgraded_words.map((w) => w.word)])).toEqual(e.better_versions);
    expect(r.feedback.mistakes.map((m) => [m.start, m.end])).toEqual(e.mistakes);
    expect(r.feedback.words_for_you.map((w) => w.word)).toEqual(e.words_for_you);
    expect(r.dropped).toBe(e.dropped);
    expect(r.safety).toBe(e.safety);
    expect(r.fallback).toEqual({ glow: e.glow === "fallback", grow: e.grow === "fallback" });
  });

  it("covers EN, HI, Hinglish, nukta, ZWJ and ZWNJ", () => {
    const names = vectors.cases.map((c) => c.name).join("\n");
    for (const tag of ["EN ", "HI ", "Hinglish", "nukta", "ZWJ", "ZWNJ", "candrabindu", "low-confidence"]) expect(names).toContain(tag);
  });
});

describe("grounding helpers", () => {
  it("containsPhrase matches whole normalised tokens only", () => {
    expect(containsPhrase("I spearheaded a team", "Spearheaded")).toBe(true);
    expect(containsPhrase("careful work", "care")).toBe(false);
    expect(containsPhrase("उसके बाद, कुछ देर", "उसके बाद")).toBe(true);
  });

  it("pre-filter grounding rate counts model glow and grow before fallbacks", () => {
    const c = vectors.cases.find((x) => x.name.startsWith("EN hallucinated glow"))!;
    const r = validate({ feedback: parseFeedback(c.output), tokens: c.words.map((w) => ({ w: w[0] as string, lc: false })), lang: "en", stutterFriendly: false, metrics });
    expect(r.preFilterGroundingRate).toBe(0.5);
  });

  it("fallbacks are written in the talk's language and use only metric values", () => {
    expect(fallbackGlow(metrics, "en").note).toBe("Your pace stayed comfortable at 138 wpm.");
    expect(fallbackGlow(metrics, "hi").note).toContain("138");
    expect(fallbackGlow({ wpm_band: "fast", fillers_per_min: 9, speech_s: 112 }, "en").note).toContain("1:52");
    expect(fallbackGrow(metrics, "en", false).issue).toBe("filler");
    expect(fallbackGrow({ wpm_band: "fast", fillers_per_min: 2 }, "en", false).issue).toBe("pace");
    expect(fallbackGrow({ wpm_band: "fast", fillers_per_min: 2 }, "en", true).issue).toBe("structure");
  });

  it("self-harm keywords match token runs, not substrings", () => {
    expect(selfHarmKeywordHit(["I", "want", "to", "die."])).toBe(true);
    expect(selfHarmKeywordHit(["the", "suicide", "squad", "movie"])).toBe(true);
    expect(selfHarmKeywordHit(["my", "skills", "improved"])).toBe(false);
    expect(selfHarmKeywordHit(["मैं", "ठीक", "हूं"])).toBe(false);
  });
});
