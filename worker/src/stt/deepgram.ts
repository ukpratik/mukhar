import { clamp01, DEFAULT_STT_TIMEOUT_MS, providerFetch, type ProviderContext, type TranscribeOptions, type TranscribeResult, type Word } from "./types.ts";

/**
 * Deepgram Nova-3 pre-recorded API (default adapter, ADR-002): filler_words=true keeps "um/uh", smart_format=false keeps
 * the transcript verbatim, word timestamps + confidence, keyterm prompting for target words.
 */
export const DEEPGRAM_MODEL = "nova-3";

export function deepgramUrl(opts: TranscribeOptions): string {
  const language = opts.variant ?? (opts.lang === "hi" ? "multi" : "en");
  const p = new URLSearchParams({
    model: DEEPGRAM_MODEL,
    language,
    filler_words: "true",
    smart_format: "false",
    punctuate: "true",
  });
  for (const k of opts.keyterms.slice(0, 20)) p.append("keyterm", k);
  return `https://api.deepgram.com/v1/listen?${p.toString()}`;
}

interface DgWord { word?: string; punctuated_word?: string; start?: number; end?: number; confidence?: number }
interface DgResponse {
  metadata?: { duration?: number };
  results?: { channels?: Array<{ alternatives?: Array<{ words?: DgWord[] }> }> };
}

export function parseDeepgram(json: unknown): Omit<TranscribeResult, "provider" | "model"> {
  const r = json as DgResponse;
  const raw = r.results?.channels?.[0]?.alternatives?.[0]?.words ?? [];
  const words: Word[] = [];
  for (const w of raw) {
    const text = (w.punctuated_word ?? w.word ?? "").trim();
    if (!text || typeof w.start !== "number" || typeof w.end !== "number") continue;
    words.push({ w: text, s: Math.round(w.start * 1000), e: Math.round(w.end * 1000), c: clamp01(w.confidence ?? 0) });
  }
  const durationMs = Math.round((r.metadata?.duration ?? 0) * 1000) || (words.at(-1)?.e ?? 0);
  return { words, durationMs, hasConfidence: true };
}

export async function transcribeDeepgram(audio: ArrayBuffer | Uint8Array, opts: TranscribeOptions, ctx: ProviderContext): Promise<TranscribeResult> {
  const json = await providerFetch(
    ctx,
    deepgramUrl(opts),
    { method: "POST", headers: { Authorization: `Token ${ctx.apiKey}`, "Content-Type": opts.mimeType }, body: audio },
    opts.timeoutMs ?? DEFAULT_STT_TIMEOUT_MS,
    "deepgram",
  );
  return { ...parseDeepgram(json), provider: "deepgram", model: DEEPGRAM_MODEL };
}
