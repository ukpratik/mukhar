import { addLog, ok, type Ctx } from "../app.ts";
import { claimFeedbackAttempt, getSession, setSessionStatus } from "../db/queries.ts";
import { ApiError } from "../errors.ts";
import { validate } from "../grounding/validate.ts";
import { LlmError } from "../llm/client.ts";
import { buildUserMessage, FEEDBACK_MODEL, PROMPT_VERSION, SYSTEM_PROMPT, type FeedbackMetrics, type FeedbackToken } from "../llm/prompt.ts";
import { FEEDBACK_SCHEMA, parseFeedback, SchemaError, type ModelFeedback } from "../llm/schema.ts";
import { quotaDay } from "../quota/quotaDay.ts";
import { assertBudget, llmCostMicroUsd, recordSpend } from "../spend/ledger.ts";
import { alertSender, cfgOf } from "./common.ts";
import { transcriptHash } from "./transcribe.ts";
import { asObj, jsonBody, optBool, reqEnum, reqStr, type Obj } from "./validate.ts";

export const MAX_FEEDBACK_ATTEMPTS = 3;
export const MAX_TOKENS_IN = 1500; // ~3:05 at a fast 400 wpm
const READY = new Set(["transcribed", "feedback_ready", "feedback_failed"]);

function parseTokens(v: unknown): FeedbackToken[] {
  if (!Array.isArray(v) || v.length === 0 || v.length > MAX_TOKENS_IN) throw new ApiError("BAD_REQUEST", `tokens must be an array of 1-${MAX_TOKENS_IN}`);
  return v.map((t, idx) => {
    const o = asObj(t, "token");
    if (o.i !== idx) throw new ApiError("BAD_REQUEST", "token indices must be 0..n-1 in order");
    if (typeof o.w !== "string" || o.w.length === 0 || o.w.length > 64) throw new ApiError("BAD_REQUEST", "token w must be 1-64 characters");
    return { i: idx, w: o.w, lc: o.lc === true };
  });
}

const METRIC_NUM = ["wpm", "fillers_per_min", "filler_count", "long_pauses_per_min", "repetitions", "mattr", "speech_s"] as const;

function parseMetrics(v: unknown): FeedbackMetrics {
  const o = asObj(v ?? {}, "metrics");
  const m: FeedbackMetrics = {};
  for (const k of METRIC_NUM) {
    const x = o[k];
    if (x === undefined || x === null) continue;
    if (typeof x !== "number" || !Number.isFinite(x) || x < 0 || x > 100_000) throw new ApiError("BAD_REQUEST", `metrics.${k} must be a non-negative number`);
    m[k] = x;
  }
  if (o.wpm_band !== undefined && o.wpm_band !== null) m.wpm_band = reqEnum(o, "wpm_band", ["slow", "comfortable", "fast"] as const);
  return m;
}

export async function feedbackRoute(c: Ctx) {
  const deps = c.get("deps");
  const db = c.env.DB;
  const body: Obj = await jsonBody(c.req);
  const sessionId = reqStr(body, "session_id", 64);
  const lang = reqEnum(body, "lang", ["en", "hi"] as const);
  const track = reqStr(body, "track", 40, 0);
  const prompt = reqStr(body, "prompt", 500, 0);
  const stutterFriendly = optBool(body, "stutter_friendly", false);
  const tokens = parseTokens(body.tokens);
  const metrics = parseMetrics(body.metrics);

  const session = await getSession(db, sessionId);
  if (!session || session.uid !== c.get("uid")) throw new ApiError("SESSION_NOT_FOUND");
  if ((session.kind !== "talk" && session.kind !== "retake") || !READY.has(session.status)) throw new ApiError("SESSION_STATE");
  if ((await transcriptHash(tokens.map((t) => t.w))) !== session.transcript_sha256) throw new ApiError("SESSION_STATE", "Transcript does not match the session");

  const cfg = await cfgOf(c);
  const day = quotaDay(deps.now());
  await assertBudget(db, cfg, day);
  if (!(await claimFeedbackAttempt(db, session.id, MAX_FEEDBACK_ATTEMPTS))) throw new ApiError("SESSION_STATE", "Feedback retry limit reached");

  let model: ModelFeedback;
  let usage;
  try {
    const res = await deps.llm.complete({
      system: SYSTEM_PROMPT,
      user: buildUserMessage({ lang, track, prompt, stutterFriendly, metrics, tokens }),
      schema: FEEDBACK_SCHEMA as unknown as Record<string, unknown>,
      maxTokens: 2000,
      cacheSystem: true,
    });
    usage = res.usage;
    addLog(c, { in_tok: usage.input_tokens, out_tok: usage.output_tokens, cache_read_tok: usage.cache_read_input_tokens ?? 0 });
    model = parseFeedback(res.json);
  } catch (e) {
    await setSessionStatus(db, session.id, "feedback_failed");
    await recordSpend(db, day, { llmFailures: 1, ...(usage ? { llmMicroUsd: llmCostMicroUsd(usage), calls: 1 } : {}) }, cfg.dailyCapUsd, alertSender(c));
    if (e instanceof LlmError || e instanceof SchemaError) throw new ApiError("LLM_FAILED");
    throw e;
  }

  const g = validate({ feedback: model, tokens, lang, stutterFriendly, metrics });
  const glowGrowDrops = (g.fallback.glow ? 1 : 0) + (g.fallback.grow ? 1 : 0);
  await recordSpend(db, day, { llmMicroUsd: llmCostMicroUsd(usage), calls: 1, groundingDrops: g.safety === "self_harm" ? 0 : glowGrowDrops }, cfg.dailyCapUsd, alertSender(c));
  await setSessionStatus(db, session.id, "feedback_ready");

  return ok(c, {
    prompt_version: PROMPT_VERSION,
    model: FEEDBACK_MODEL,
    feedback: g.feedback,
    dropped: g.dropped,
    fallback: g.fallback,
    safety: g.safety,
  });
}
