import Anthropic from "@anthropic-ai/sdk";
import type { LlmUsage } from "../spend/ledger.ts";
import { FEEDBACK_MODEL } from "./prompt.ts";

/**
 * Anthropic Messages API client (ADR-004, LLD §4.3): claude-haiku-5-5, structured outputs via output_config.format,
 * effort "low", cached system prompt, 15 s timeout, 1 SDK retry. Haiku 5.5 has no server-side refusal fallback, so a
 * `refusal` (or `max_tokens`) stop reason becomes LLM_FAILED and the caller falls back to metrics.
 */
export interface LlmRequest {
  system: string;
  user: string;
  schema: Record<string, unknown>;
  maxTokens: number;
  /** Mark the system prompt with cache_control (only worth it for prompts ≥ 512 tokens). */
  cacheSystem: boolean;
}

export interface LlmResponse {
  json: unknown;
  usage: LlmUsage;
  stopReason: string;
  model: string;
  ms: number;
}

export interface LlmClient {
  complete(req: LlmRequest): Promise<LlmResponse>;
}

export type LlmErrorKind = "timeout" | "refusal" | "max_tokens" | "invalid_json" | "upstream" | "config";

export class LlmError extends Error {
  readonly kind: LlmErrorKind;
  constructor(kind: LlmErrorKind, message: string) {
    super(message);
    this.kind = kind;
  }
}

export interface AnthropicClientOptions {
  apiKey: string | undefined;
  fetch?: typeof fetch;
  model?: string;
  /** "adaptive" = model default; "disabled" is the gate-zero latency comparison (valid at effort low). */
  thinking?: "adaptive" | "disabled";
  effort?: "low" | "medium" | "high";
  timeoutMs?: number;
  maxRetries?: number;
  now?: () => number;
}

export function anthropicClient(o: AnthropicClientOptions): LlmClient {
  const now = o.now ?? Date.now;
  return {
    async complete(req: LlmRequest): Promise<LlmResponse> {
      if (!o.apiKey) throw new LlmError("config", "ANTHROPIC_API_KEY is not set");
      const client = new Anthropic({
        apiKey: o.apiKey,
        maxRetries: o.maxRetries ?? 1,
        timeout: o.timeoutMs ?? 15_000,
        ...(o.fetch ? { fetch: o.fetch } : {}),
      });
      const started = now();
      let msg: Anthropic.Message;
      try {
        msg = await client.messages.create({
          model: o.model ?? FEEDBACK_MODEL,
          max_tokens: req.maxTokens,
          system: [{ type: "text", text: req.system, ...(req.cacheSystem ? { cache_control: { type: "ephemeral" as const } } : {}) }],
          messages: [{ role: "user", content: req.user }],
          output_config: { effort: o.effort ?? "low", format: { type: "json_schema", schema: req.schema } },
          ...(o.thinking === "disabled" ? { thinking: { type: "disabled" as const } } : {}),
        });
      } catch (e) {
        if (e instanceof Anthropic.APIConnectionTimeoutError) throw new LlmError("timeout", "LLM request timed out");
        if (e instanceof Anthropic.APIError) throw new LlmError("upstream", `LLM API error ${e.status ?? "network"}`);
        throw new LlmError("upstream", "LLM request failed");
      }
      const ms = now() - started;
      if (msg.stop_reason === "refusal") throw new LlmError("refusal", "LLM declined the request");
      if (msg.stop_reason === "max_tokens") throw new LlmError("max_tokens", "LLM output was truncated");
      const text = msg.content.find((b): b is Anthropic.TextBlock => b.type === "text")?.text;
      if (!text) throw new LlmError("invalid_json", "LLM returned no text");
      let json: unknown;
      try {
        json = JSON.parse(text);
      } catch {
        throw new LlmError("invalid_json", "LLM returned invalid JSON");
      }
      return {
        json,
        usage: {
          input_tokens: msg.usage.input_tokens,
          output_tokens: msg.usage.output_tokens,
          cache_read_input_tokens: msg.usage.cache_read_input_tokens ?? 0,
          cache_creation_input_tokens: msg.usage.cache_creation_input_tokens ?? 0,
        },
        stopReason: msg.stop_reason ?? "unknown",
        model: msg.model,
        ms,
      };
    },
  };
}
