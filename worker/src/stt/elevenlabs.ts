import { clamp01, DEFAULT_STT_TIMEOUT_MS, providerFetch, type ProviderContext, type TranscribeOptions, type TranscribeResult, type Word } from "./types.ts";

/** ElevenLabs Scribe v2 (gate-zero candidate, ADR-002): word timestamps, confidence from `logprob`, keyterms. */
export const ELEVENLABS_MODEL = "scribe_v2";

interface ElWord { text?: string; start?: number; end?: number; type?: string; logprob?: number }
interface ElResponse { words?: ElWord[] }

export function parseElevenLabs(json: unknown): Omit<TranscribeResult, "provider" | "model"> {
  const r = json as ElResponse;
  const words: Word[] = [];
  for (const w of r.words ?? []) {
    if (w.type !== undefined && w.type !== "word") continue; // spacing and audio events are not words
    const text = (w.text ?? "").trim();
    if (!text || typeof w.start !== "number" || typeof w.end !== "number") continue;
    words.push({ w: text, s: Math.round(w.start * 1000), e: Math.round(w.end * 1000), c: clamp01(typeof w.logprob === "number" ? Math.exp(w.logprob) : 0) });
  }
  return { words, durationMs: words.at(-1)?.e ?? 0, hasConfidence: true };
}

export async function transcribeElevenLabs(audio: ArrayBuffer | Uint8Array, opts: TranscribeOptions, ctx: ProviderContext): Promise<TranscribeResult> {
  const form = new FormData();
  form.append("file", new Blob([audio], { type: opts.mimeType }), "audio.aac");
  form.append("model_id", ELEVENLABS_MODEL);
  form.append("language_code", opts.variant ?? opts.lang);
  form.append("timestamps_granularity", "word");
  form.append("tag_audio_events", "true");
  for (const k of opts.keyterms.slice(0, 20)) form.append("keyterms", k);
  const json = await providerFetch(
    ctx,
    "https://api.elevenlabs.io/v1/speech-to-text",
    { method: "POST", headers: { "xi-api-key": ctx.apiKey }, body: form },
    opts.timeoutMs ?? DEFAULT_STT_TIMEOUT_MS,
    "elevenlabs",
  );
  return { ...parseElevenLabs(json), provider: "elevenlabs", model: ELEVENLABS_MODEL };
}
