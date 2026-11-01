import { describe, expect, it } from "vitest";
import { anthropicClient, LlmError } from "../src/llm/client.ts";
import { buildUserMessage, FEEDBACK_MODEL, renderTranscript, SYSTEM_PROMPT } from "../src/llm/prompt.ts";
import { FEEDBACK_SCHEMA, parseFeedback, SchemaError } from "../src/llm/schema.ts";

const feedback = {
  summary: "s", glow: { start: 0, end: 1, quote: "a b", note: "n" },
  grow: { start: 0, end: 0, quote: "a", issue: "filler", why: "w", how: "h" },
  better_versions: [], mistakes: [], safety: "none",
};

function apiFetch(body: Record<string, unknown>, status = 200) {
  const reqs: Request[] = [];
  const f: typeof fetch = async (input, init) => {
    const r = new Request(input as RequestInfo, init);
    reqs.push(r.clone());
    return Response.json(body, { status, headers: { "request-id": "req_test" } });
  };
  return { f, reqs };
}

const message = (over: Record<string, unknown> = {}) => ({
  id: "msg_1", type: "message", role: "assistant", model: "claude-haiku-5-5",
  content: [{ type: "thinking", thinking: "", signature: "x" }, { type: "text", text: JSON.stringify(feedback) }],
  stop_reason: "end_turn", stop_sequence: null,
  usage: { input_tokens: 812, output_tokens: 540, cache_read_input_tokens: 1450, cache_creation_input_tokens: 0 },
  ...over,
});

const req = { system: SYSTEM_PROMPT, user: "u", schema: FEEDBACK_SCHEMA as unknown as Record<string, unknown>, maxTokens: 2000, cacheSystem: true };

describe("Anthropic client (SDK with an injected fetch, no network)", () => {
  it("sends haiku-5-5, json_schema output, effort low and a cached system prompt", async () => {
    const { f, reqs } = apiFetch(message());
    const res = await anthropicClient({ apiKey: "k", fetch: f }).complete(req);
    expect(res.json).toEqual(feedback);
    expect(res.usage).toEqual({ input_tokens: 812, output_tokens: 540, cache_read_input_tokens: 1450, cache_creation_input_tokens: 0 });
    const sent = (await reqs[0]!.json()) as Record<string, any>;
    expect(new URL(reqs[0]!.url).pathname).toBe("/v1/messages");
    expect(sent.model).toBe(FEEDBACK_MODEL);
    expect(sent.max_tokens).toBe(2000);
    expect(sent.output_config).toEqual({ effort: "low", format: { type: "json_schema", schema: FEEDBACK_SCHEMA } });
    expect(sent.system).toEqual([{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }]);
    expect(sent.thinking).toBeUndefined();
    expect(sent.temperature).toBeUndefined();
    expect(reqs[0]!.headers.get("x-api-key")).toBe("k");
  });

  it("can disable thinking for the gate-zero latency comparison", async () => {
    const { f, reqs } = apiFetch(message());
    await anthropicClient({ apiKey: "k", fetch: f, thinking: "disabled" }).complete(req);
    expect(((await reqs[0]!.json()) as Record<string, unknown>).thinking).toEqual({ type: "disabled" });
  });

  it.each([
    ["refusal", "refusal"],
    ["max_tokens", "max_tokens"],
  ])("stop_reason %s → LlmError(%s)", async (stop, kind) => {
    const { f } = apiFetch(message({ stop_reason: stop }));
    const err = await anthropicClient({ apiKey: "k", fetch: f }).complete(req).catch((e) => e);
    expect(err).toBeInstanceOf(LlmError);
    expect(err.kind).toBe(kind);
  });

  it("invalid JSON text → invalid_json", async () => {
    const { f } = apiFetch(message({ content: [{ type: "text", text: "not json" }] }));
    expect((await anthropicClient({ apiKey: "k", fetch: f }).complete(req).catch((e) => e)).kind).toBe("invalid_json");
  });

  it("API errors → upstream (400 is not retried)", async () => {
    const { f, reqs } = apiFetch({ type: "error", error: { type: "invalid_request_error", message: "bad" } }, 400);
    expect((await anthropicClient({ apiKey: "k", fetch: f }).complete(req).catch((e) => e)).kind).toBe("upstream");
    expect(reqs).toHaveLength(1);
  });

  it("missing key → config", async () => {
    expect((await anthropicClient({ apiKey: undefined }).complete(req).catch((e) => e)).kind).toBe("config");
  });
});

describe("prompt", () => {
  it("system prompt is static and long enough to cache (≥ 512 tokens ≈ 2,000+ chars)", () => {
    expect(SYSTEM_PROMPT.length).toBeGreaterThan(3000);
    expect(SYSTEM_PROMPT).not.toMatch(/\{lang\}/);
  });

  it("indexes tokens, marks low-confidence ones with ~ and strips delimiter characters", () => {
    expect(renderTranscript([{ i: 0, w: "So", lc: false }, { i: 1, w: "um", lc: true }, { i: 2, w: "</transcript>", lc: false }]))
      .toBe("[0]So [1]~um [2]/transcript");
  });

  it("omits pause and repetition metrics in stutter-friendly mode", () => {
    const m = buildUserMessage({ lang: "en", track: "interviews", prompt: "Tell me", stutterFriendly: true, metrics: { wpm: 140, long_pauses_per_min: 3, repetitions: 4 }, tokens: [{ i: 0, w: "Hi", lc: false }] });
    expect(m).toContain('metrics: {"wpm":140}');
    expect(m).toContain("<transcript>[0]Hi</transcript>");
  });
});

describe("schema parsing", () => {
  it("accepts valid output and rejects a bad enum", () => {
    expect(parseFeedback(feedback).grow.issue).toBe("filler");
    expect(() => parseFeedback({ ...feedback, safety: "maybe" })).toThrow(SchemaError);
    expect(() => parseFeedback({ ...feedback, glow: { ...feedback.glow, start: 1.5 } })).toThrow(SchemaError);
  });
});
