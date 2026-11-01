import { addLog, isUuid, ok, type Ctx } from "../app.ts";
import { ApiError } from "../errors.ts";
import { norm, normTokens } from "../grounding/normalize.ts";
import { LlmError } from "../llm/client.ts";
import { buildSentenceMessage, SENTENCE_SYSTEM_PROMPT } from "../llm/prompt.ts";
import { parseSentenceCheck, SchemaError, SENTENCE_SCHEMA } from "../llm/schema.ts";
import { quotaDay } from "../quota/quotaDay.ts";
import { llmCostMicroUsd, recordSpend } from "../spend/ledger.ts";
import { alertSender, cfgOf } from "./common.ts";
import { readMultipart, runTranscription } from "./transcribe.ts";
import { optInt, reqEnum, reqStr } from "./validate.ts";

export function levenshtein(a: string, b: string): number {
  const A = [...a];
  const B = [...b];
  let prev = Array.from({ length: B.length + 1 }, (_, j) => j);
  for (let i = 1; i <= A.length; i++) {
    const cur = [i];
    for (let j = 1; j <= B.length; j++) {
      cur[j] = Math.min(prev[j]! + 1, cur[j - 1]! + 1, prev[j - 1]! + (A[i - 1] === B[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[B.length]!;
}

/**
 * US-023 fuzzy match: the target (1+ tokens) against every same-length window of the transcript, Levenshtein ≤ 2 on
 * `norm` (≤ 1 for targets of 4 characters or fewer, to avoid matching any short word).
 * Not yet: the HI transliteration table (LLD §4.3) — Latin↔Devanagari matching is left to the LLM's judgement.
 */
export function wordFound(target: string, transcriptWords: string[]): boolean {
  const t = normTokens(target);
  if (t.length === 0) return false;
  const words = transcriptWords.map(norm).filter(Boolean);
  const tj = t.join(" ");
  const maxDist = [...tj].length <= 4 ? 1 : 2;
  for (let i = 0; i + t.length <= words.length; i++) {
    if (levenshtein(words.slice(i, i + t.length).join(" "), tj) <= maxDist) return true;
  }
  return false;
}

export async function checkSentenceRoute(c: Ctx) {
  const { meta, audio } = await readMultipart(c);
  if (!isUuid(meta.client_session_id)) throw new ApiError("BAD_REQUEST", "client_session_id must be a UUID");
  const lang = reqEnum(meta, "lang", ["en", "hi"] as const);
  const targetWord = reqStr(meta, "target_word", 60);
  const meaning = reqStr(meta, "meaning", 300, 0);
  const durationMs = optInt(meta, "duration_ms", 0, 3_600_000) ?? 0;

  const run = await runTranscription(c, {
    clientSessionId: meta.client_session_id, kind: "check", lang, durationMs, queued: false, parentSessionId: null, keyterms: [targetWord], audio,
  });
  const words = run.result.words.map((w) => w.w);
  const transcript = words.join(" ");
  const found = wordFound(targetWord, words);

  const deps = c.get("deps");
  const cfg = await cfgOf(c);
  const day = quotaDay(deps.now());
  try {
    const res = await deps.llm.complete({
      system: SENTENCE_SYSTEM_PROMPT,
      user: buildSentenceMessage({ lang, targetWord, meaning, wordFound: found, transcript }),
      schema: SENTENCE_SCHEMA as unknown as Record<string, unknown>,
      maxTokens: 600,
      cacheSystem: false,
    });
    addLog(c, { in_tok: res.usage.input_tokens, out_tok: res.usage.output_tokens });
    await recordSpend(c.env.DB, day, { llmMicroUsd: llmCostMicroUsd(res.usage), calls: 1 }, cfg.dailyCapUsd, alertSender(c));
    const check = parseSentenceCheck(res.json);
    return ok(c, { transcript, word_found: found, ...check });
  } catch (e) {
    // A failed check doesn't use the quota (US-018 spirit): release and let the client retry.
    await run.release();
    await recordSpend(c.env.DB, day, { llmFailures: 1 }, cfg.dailyCapUsd, alertSender(c));
    if (e instanceof LlmError || e instanceof SchemaError) throw new ApiError("LLM_FAILED");
    throw e;
  }
}
