/** Normalised STT output (ADR-002): `words[{w, s, e, c}]` in ms with confidence 0–1. */
export interface Word {
  w: string;
  s: number;
  e: number;
  c: number;
}

export interface TranscribeOptions {
  lang: "en" | "hi";
  keyterms: string[];
  mimeType: string;
  /** Provider-specific variant, e.g. Deepgram language "multi" or "en-IN" (config value `deepgram:multi`). */
  variant?: string | undefined;
  timeoutMs?: number;
}

export interface TranscribeResult {
  words: Word[];
  durationMs: number;
  provider: string;
  model: string;
  /** False when the provider gave no per-word confidence (c is then 1.0 and must not be trusted). */
  hasConfidence: boolean;
}

export interface ProviderContext {
  fetch: typeof fetch;
  apiKey: string;
}

export class SttError extends Error {
  readonly kind: "timeout" | "upstream" | "config";
  readonly upstreamStatus: number | undefined;
  constructor(kind: "timeout" | "upstream" | "config", message: string, upstreamStatus?: number) {
    super(message);
    this.kind = kind;
    this.upstreamStatus = upstreamStatus;
  }
}

export const DEFAULT_STT_TIMEOUT_MS = 25_000;

export const clamp01 = (x: number) => (Number.isFinite(x) ? Math.min(1, Math.max(0, x)) : 0);

/** Wraps fetch so timeouts and non-2xx responses become SttError (never leaks the body, which may hold transcript text). */
export async function providerFetch(ctx: ProviderContext, url: string, init: RequestInit, timeoutMs: number, name: string): Promise<unknown> {
  let res: Response;
  try {
    res = await ctx.fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
  } catch (e) {
    const isTimeout = e instanceof Error && (e.name === "TimeoutError" || e.name === "AbortError");
    throw new SttError(isTimeout ? "timeout" : "upstream", `${name} request failed${isTimeout ? " (timeout)" : ""}`);
  }
  if (!res.ok) {
    await res.body?.cancel();
    throw new SttError("upstream", `${name} returned ${res.status}`, res.status);
  }
  try {
    return await res.json();
  } catch {
    throw new SttError("upstream", `${name} returned invalid JSON`);
  }
}
