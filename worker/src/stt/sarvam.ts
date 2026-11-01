import { DEFAULT_STT_TIMEOUT_MS, providerFetch, type ProviderContext, type TranscribeOptions, type TranscribeResult, type Word } from "./types.ts";

/**
 * Sarvam speech-to-text REST API (gate-zero candidate, ADR-002). `mode=codemix` keeps Hinglish in mixed script.
 * Sarvam returns segment-level timestamps, not per-word confidence: tokens inside a segment get evenly interpolated
 * times and c = 1 with `hasConfidence: false` (A-3: excluded at gate zero if word timing proves unusable).
 * The synchronous endpoint is limited to short audio; long clips need Sarvam's batch API (A-3, measured at gate zero).
 */
export const SARVAM_MODEL = "saaras:v3";

interface SarvamResponse {
  transcript?: string;
  timestamps?: { words?: string[]; start_time_seconds?: number[]; end_time_seconds?: number[] } | null;
}

export function parseSarvam(json: unknown): Omit<TranscribeResult, "provider" | "model"> {
  const r = json as SarvamResponse;
  const segs = r.timestamps?.words ?? [];
  const starts = r.timestamps?.start_time_seconds ?? [];
  const ends = r.timestamps?.end_time_seconds ?? [];
  const words: Word[] = [];
  if (segs.length > 0) {
    segs.forEach((seg, i) => {
      const toks = seg.split(/\s+/u).filter(Boolean);
      const s0 = Math.round((starts[i] ?? 0) * 1000);
      const e0 = Math.round((ends[i] ?? starts[i] ?? 0) * 1000);
      const step = toks.length > 0 ? (e0 - s0) / toks.length : 0;
      toks.forEach((t, k) => words.push({ w: t, s: Math.round(s0 + k * step), e: Math.round(s0 + (k + 1) * step), c: 1 }));
    });
  } else if (r.transcript) {
    for (const t of r.transcript.split(/\s+/u).filter(Boolean)) words.push({ w: t, s: 0, e: 0, c: 1 });
  }
  return { words, durationMs: words.at(-1)?.e ?? 0, hasConfidence: false };
}

export async function transcribeSarvam(audio: ArrayBuffer | Uint8Array, opts: TranscribeOptions, ctx: ProviderContext): Promise<TranscribeResult> {
  const form = new FormData();
  form.append("file", new Blob([audio], { type: opts.mimeType }), "audio.aac");
  form.append("model", SARVAM_MODEL);
  form.append("mode", opts.variant ?? "codemix");
  form.append("language_code", opts.lang === "hi" ? "hi-IN" : "en-IN");
  form.append("with_timestamps", "true");
  const json = await providerFetch(
    ctx,
    "https://api.sarvam.ai/speech-to-text",
    { method: "POST", headers: { "api-subscription-key": ctx.apiKey }, body: form },
    opts.timeoutMs ?? DEFAULT_STT_TIMEOUT_MS,
    "sarvam",
  );
  return { ...parseSarvam(json), provider: "sarvam", model: SARVAM_MODEL };
}
