import { transcribeDeepgram } from "./deepgram.ts";
import { transcribeElevenLabs } from "./elevenlabs.ts";
import { transcribeSarvam } from "./sarvam.ts";
import { SttError, type TranscribeOptions, type TranscribeResult } from "./types.ts";

export type { TranscribeOptions, TranscribeResult, Word } from "./types.ts";
export { SttError } from "./types.ts";

/**
 * The single STT entry point (ADR-002). The provider per language is a D1 config row (`stt_provider_en/hi`), written as
 * `name` or `name:variant`, e.g. `deepgram:multi`. Used by the Worker routes and by scripts/stt-bench.ts unchanged.
 */
export type ProviderName = "deepgram" | "sarvam" | "elevenlabs";
export const PROVIDERS: readonly ProviderName[] = ["deepgram", "sarvam", "elevenlabs"];

export interface SttKeys {
  deepgram?: string | undefined;
  sarvam?: string | undefined;
  elevenlabs?: string | undefined;
}

export function parseProviderSpec(spec: string): { provider: ProviderName; variant?: string } {
  const [name, variant] = spec.split(":", 2) as [string, string | undefined];
  if (!PROVIDERS.includes(name as ProviderName)) throw new SttError("config", `unknown STT provider "${name}"`);
  return variant ? { provider: name as ProviderName, variant } : { provider: name as ProviderName };
}

export async function transcribe(
  audio: ArrayBuffer | Uint8Array,
  opts: TranscribeOptions & { providerSpec: string },
  deps: { fetch: typeof fetch; keys: SttKeys },
): Promise<TranscribeResult> {
  const { provider, variant } = parseProviderSpec(opts.providerSpec);
  const apiKey = deps.keys[provider];
  if (!apiKey) throw new SttError("config", `missing API key for ${provider}`);
  const o: TranscribeOptions = { ...opts, variant: variant ?? opts.variant };
  const ctx = { fetch: deps.fetch, apiKey };
  switch (provider) {
    case "deepgram":
      return transcribeDeepgram(audio, o, ctx);
    case "sarvam":
      return transcribeSarvam(audio, o, ctx);
    case "elevenlabs":
      return transcribeElevenLabs(audio, o, ctx);
  }
}
