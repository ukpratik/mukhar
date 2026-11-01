import type { FeedbackMetrics } from "../llm/prompt.ts";

/**
 * Metrics-based fallbacks (LLD §4.4) used when the model's glow or grow fails grounding. Template strings in both
 * languages; numbers come only from on-device metrics. The server has no history, so "best metric" is band-based.
 */
export interface FallbackGlow { start: null; end: null; quote: null; note: string; fallback: true; metric: string }
export interface FallbackGrow {
  start: null; end: null; quote: null; issue: "filler" | "pace" | "structure"; why: string; how: string; fallback: true; metric: string;
}

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, "0")}`;

export function fallbackGlow(m: FeedbackMetrics, lang: "en" | "hi"): FallbackGlow {
  const base = { start: null, end: null, quote: null, fallback: true } as const;
  if (m.wpm_band === "comfortable" && typeof m.wpm === "number") {
    const wpm = Math.round(m.wpm);
    return { ...base, metric: "wpm", note: lang === "hi" ? `आपकी रफ़्तार आराम से ${wpm} शब्द प्रति मिनट पर बनी रही।` : `Your pace stayed comfortable at ${wpm} wpm.` };
  }
  if (typeof m.fillers_per_min === "number" && m.fillers_per_min <= 5) {
    return { ...base, metric: "fillers", note: lang === "hi" ? "आपने बहुत कम भराव वाले शब्द इस्तेमाल किए, बात साफ़ रही।" : "You used very few fillers, so your points came through clearly." };
  }
  const s = typeof m.speech_s === "number" && m.speech_s > 0 ? mmss(m.speech_s) : null;
  return {
    ...base,
    metric: "duration",
    note: lang === "hi"
      ? `आप ${s ?? "पूरे समय"} तक बोले — शुरुआत करना ही सबसे मुश्किल हिस्सा है।`
      : `You spoke for ${s ?? "the whole talk"} — showing up is the hard part.`,
  };
}

export function fallbackGrow(m: FeedbackMetrics, lang: "en" | "hi", stutterFriendly: boolean): FallbackGrow {
  const base = { start: null, end: null, quote: null, fallback: true } as const;
  const hi = lang === "hi";
  if (typeof m.fillers_per_min === "number" && m.fillers_per_min > 5) {
    return {
      ...base, issue: "filler", metric: "fillers",
      why: hi ? "भराव वाले शब्द आपकी बात को कम पक्का बना देते हैं।" : "Fillers make your points sound less sure.",
      how: hi ? "'उम्म' की जगह एक पल रुकिए — चुप रहना ठीक है।" : "Pause instead of saying 'um' — a short silence sounds confident.",
    };
  }
  if (!stutterFriendly && m.wpm_band === "fast") {
    return {
      ...base, issue: "pace", metric: "wpm",
      why: hi ? "तेज़ बोलने पर सुनने वाले ज़रूरी बातें चूक जाते हैं।" : "When you rush, listeners miss your key points.",
      how: hi ? "हर अहम वाक्य के बाद एक साँस लीजिए।" : "Take a breath after each key sentence.",
    };
  }
  if (!stutterFriendly && m.wpm_band === "slow") {
    return {
      ...base, issue: "pace", metric: "wpm",
      why: hi ? "बहुत धीमी रफ़्तार से ध्यान भटक सकता है।" : "A very slow pace can make listeners drift.",
      how: hi ? "अगली बार वाक्यों को थोड़ा जोड़कर, एक लय में बोलिए।" : "Next time, link your sentences and keep a steady rhythm.",
    };
  }
  return {
    ...base, issue: "structure", metric: "structure",
    why: hi ? "साफ़ ढाँचा सुनने वालों को आपके साथ रखता है।" : "A clear shape helps listeners follow you.",
    how: hi ? "शुरुआत में मुख्य बात कहिए, फिर एक उदाहरण, फिर एक लाइन में निष्कर्ष।" : "Say your main point first, then one example, then a one-line wrap-up.",
  };
}
