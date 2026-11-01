/**
 * Feedback eval (LLD §8, NFR-004, ADR-004). Run on every prompt_version change.
 *
 *   ANTHROPIC_API_KEY=… node scripts/feedback-eval.ts [--set data/eval/feedback-eval.json] [--limit N] [--thinking disabled]
 *
 * Each item goes through the real prompt (src/llm/prompt.ts), the real client and the real grounding validator.
 * Reports schema validity, PRE-filter grounding (≥ 95%), post-filter grounding (100% by construction), borrowing false
 * positives (must be 0), safety cases, stutter-friendly compliance, latency p50/p90, tokens and cost. Exits 1 on a
 * breach or on a > 2-point grounding regression vs reports/feedback-eval-last.json. ≈ $0.05 per 40-item run.
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { makeSpanChecker, validate } from "../src/grounding/validate.ts";
import { anthropicClient, LlmError } from "../src/llm/client.ts";
import { buildUserMessage, PROMPT_VERSION, SYSTEM_PROMPT, type FeedbackMetrics } from "../src/llm/prompt.ts";
import { FEEDBACK_SCHEMA, parseFeedback, type ModelFeedback } from "../src/llm/schema.ts";
import { llmCostMicroUsd } from "../src/spend/ledger.ts";
import { percentile } from "./lib/bench.ts";
import { borrowingFalsePositives, checkRegression, safetyPass, stutterCompliant, type EvalLabels, type EvalSummary } from "./lib/evalMetrics.ts";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

interface EvalItem {
  id: string;
  lang: "en" | "hi";
  track: string;
  prompt: string;
  stutter_friendly?: boolean;
  /** Either STT-style word tuples [w, start_ms, end_ms, conf] or plain text (all tokens confident). */
  words?: Array<[string, number, number, number]>;
  text?: string;
  metrics: FeedbackMetrics;
  labels?: EvalLabels;
}

function arg(name: string): string | undefined {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

async function main() {
  const setPath = resolve(ROOT, arg("set") ?? "data/eval/feedback-eval.json");
  const doc = JSON.parse(await readFile(setPath, "utf8")) as { low_conf?: number; items: EvalItem[] };
  const lowConf = doc.low_conf ?? 0.6;
  const items = doc.items.slice(0, Number(arg("limit") ?? Infinity));
  const thinking = arg("thinking") === "disabled" ? "disabled" : "adaptive";
  const client = anthropicClient({ apiKey: process.env.ANTHROPIC_API_KEY, thinking, timeoutMs: 30_000 });

  const rows: string[] = [];
  const latencies: number[] = [];
  let schemaValid = 0, groundSum = 0, groundN = 0, postOk = 0, postN = 0, bfp = 0, safetyFail = 0, stutterBad = 0, costMicro = 0;
  let inTok = 0, outTok = 0, cacheTok = 0;

  for (const item of items) {
    const tuples = item.words ?? (item.text ?? "").split(/\s+/u).filter(Boolean).map((w) => [w, 0, 0, 1] as [string, number, number, number]);
    const tokens = tuples.map(([w, , , c], i) => ({ i, w, lc: c < lowConf }));
    const stutter = item.stutter_friendly ?? false;
    let raw: ModelFeedback;
    try {
      const res = await client.complete({
        system: SYSTEM_PROMPT,
        user: buildUserMessage({ lang: item.lang, track: item.track, prompt: item.prompt, stutterFriendly: stutter, metrics: item.metrics, tokens }),
        schema: FEEDBACK_SCHEMA as unknown as Record<string, unknown>,
        maxTokens: 2000,
        cacheSystem: true,
      });
      latencies.push(res.ms);
      costMicro += llmCostMicroUsd(res.usage);
      inTok += res.usage.input_tokens; outTok += res.usage.output_tokens; cacheTok += res.usage.cache_read_input_tokens ?? 0;
      raw = parseFeedback(res.json);
      schemaValid++;
    } catch (e) {
      rows.push(`| ${item.id} | ${item.lang} | ERROR ${e instanceof LlmError ? e.kind : (e as Error).message} | | | | |`);
      continue;
    }
    const g = validate({ feedback: raw, tokens, lang: item.lang, stutterFriendly: stutter, metrics: item.metrics });
    groundSum += g.preFilterGroundingRate; groundN++;
    // post-filter: every kept item must re-validate exactly
    const check = makeSpanChecker(tokens);
    const kept = [g.feedback.glow, g.feedback.grow, ...g.feedback.better_versions, ...g.feedback.mistakes].filter((x) => x.start !== null);
    for (const k of kept) { postN++; if (check(k.start as number, k.end as number, k.quote as string)) postOk++; }
    const fp = borrowingFalsePositives(raw, item.labels ?? {});
    bfp += fp;
    const safeOk = safetyPass(g.safety, item.labels ?? {});
    if (!safeOk) safetyFail++;
    const stutterOk = !stutter || stutterCompliant(raw);
    if (!stutterOk) stutterBad++;
    rows.push(`| ${item.id} | ${item.lang} | ${(g.preFilterGroundingRate * 100).toFixed(0)}% | ${g.dropped} | ${fp} | ${safeOk ? "ok" : `FAIL (${g.safety})`} | ${stutter ? (stutterOk ? "ok" : "FAIL") : "–"} |`);
  }

  const summary: EvalSummary = {
    items: items.length,
    schemaValid,
    preFilterGrounding: groundN ? groundSum / groundN : null,
    postFilterGrounding: postN ? postOk / postN : null,
    borrowingFalsePositives: bfp,
    safetyFailures: safetyFail,
    stutterViolations: stutterBad,
    p50Ms: percentile(latencies, 50),
    p90Ms: percentile(latencies, 90),
    costUsd: costMicro / 1e6,
  };
  const lastPath = resolve(ROOT, "reports/feedback-eval-last.json");
  let last: EvalSummary | null = null;
  try { last = JSON.parse(await readFile(lastPath, "utf8")) as EvalSummary; } catch { last = null; }
  const reg = checkRegression(summary, last);

  const date = new Date().toISOString().slice(0, 10);
  const pct = (x: number | null) => (x === null ? "–" : `${(x * 100).toFixed(1)}%`);
  const md = [
    `# Feedback eval ${date} (${PROMPT_VERSION}, thinking ${thinking})`, "",
    `Set: \`${setPath.replace(ROOT + "/", "")}\`, ${items.length} items.`, "",
    "| Metric | Value | Target |", "|---|---|---|",
    `| Schema-valid outputs | ${schemaValid}/${items.length} | all |`,
    `| Pre-filter grounding (glow + grow) | ${pct(summary.preFilterGrounding)} | ≥ 95% |`,
    `| Post-filter grounding | ${pct(summary.postFilterGrounding)} | 100% |`,
    `| Borrowing false positives | ${bfp} | 0 |`,
    `| Safety failures | ${safetyFail} | 0 |`,
    `| Stutter-friendly violations | ${stutterBad} | 0 |`,
    `| LLM latency p50 / p90 | ${summary.p50Ms ?? "–"} / ${summary.p90Ms ?? "–"} ms | p90 ≤ 8 s |`,
    `| Tokens in / out / cache read | ${inTok} / ${outTok} / ${cacheTok} | |`,
    `| Cost | $${summary.costUsd.toFixed(4)} | |`,
    "", `**Result: ${reg.ok ? "PASS" : "FAIL"}**${reg.reasons.length ? ` — ${reg.reasons.join("; ")}` : ""}`, "",
    "| Item | Lang | Pre-filter grounding | Dropped | Borrowing FP | Safety | Stutter |", "|---|---|---|---|---|---|---|",
    ...rows, "",
  ].join("\n");
  await mkdir(resolve(ROOT, "reports"), { recursive: true });
  const out = resolve(ROOT, `reports/feedback-eval-${date}-${PROMPT_VERSION}.md`);
  await writeFile(out, md);
  if (reg.ok) await writeFile(lastPath, JSON.stringify(summary, null, 2)); // baseline only moves on a passing run
  console.log(md);
  console.log(`Wrote ${out}`);
  if (!reg.ok) process.exitCode = 1;
}

main().catch((e) => {
  console.error((e as Error).message);
  process.exit(1);
});
