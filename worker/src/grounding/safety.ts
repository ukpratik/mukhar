import { norm } from "./normalize.ts";

/**
 * Self-harm keyword backstop (LLD §4.4, US-014), versioned. Matched on normalised token runs so "kill myself." matches but
 * "skills" does not. Deliberately small and high-precision: the model's own safety flag is the primary signal.
 */
export const SAFETY_KEYWORDS_VERSION = "sk-2026-11-01";

const PHRASES: Record<"en" | "hi", string[]> = {
  en: [
    "kill myself", "killing myself", "end my life", "ending my life", "suicide", "suicidal", "want to die", "wanna die",
    "hurt myself", "harm myself", "self harm", "cut myself", "no reason to live", "better off dead",
  ],
  hi: [
    "आत्महत्या", "खुदकुशी", "ख़ुदकुशी", "मरना चाहता", "मरना चाहती", "जान दे दूं", "जान दे दूँ", "अपनी जान", "खुद को खत्म",
    "jaan de dun", "marna chahta", "marna chahti", "khudkushi", "aatmahatya",
  ],
};

const COMPILED = [...PHRASES.en, ...PHRASES.hi].map((p) => norm(p).split(" "));

export function selfHarmKeywordHit(tokens: string[]): boolean {
  const t = tokens.map(norm).filter((x) => x.length > 0);
  for (const phrase of COMPILED) {
    for (let i = 0; i + phrase.length <= t.length; i++) {
      let ok = true;
      for (let k = 0; k < phrase.length; k++) if (t[i + k] !== phrase[k]) { ok = false; break; }
      if (ok) return true;
    }
  }
  return false;
}
