import { ApiError } from "../errors.ts";
import type { RuntimeConfig } from "../db/queries.ts";

/**
 * Spend ledger and global cap (HLD §5.4, US-064, D-025): every paid call adds an estimated cost to `spend_daily`;
 * before any paid call, kill switch or spend ≥ cap → 503 BUSY. Webhook once per day at 80% and at 100%.
 * Amounts are integer micro-USD. The ledger day is the quota day (03:00 IST boundary), so ops and quotas line up.
 */

/** Estimated STT price per audio minute in USD (list prices, Nov 2026; see ADR-002). Unknown providers use the highest. */
export const STT_USD_PER_MIN: Record<string, number> = {
  deepgram: 0.0058,
  sarvam: 0.006,
  elevenlabs: 0.0037,
};

/** claude-haiku-5-5, prompts ≤ 100K tokens: $0.10 in, $0.50 out, cache read 0.1×, 5-min cache write 1.25× per MTok. */
export const HAIKU_USD_PER_MTOK = { input: 0.1, output: 0.5, cacheRead: 0.01, cacheWrite: 0.125 };

export interface LlmUsage {
  input_tokens: number;
  output_tokens: number;
  cache_read_input_tokens?: number | null;
  cache_creation_input_tokens?: number | null;
}

export function sttCostMicroUsd(provider: string, audioMs: number): number {
  const rate = STT_USD_PER_MIN[provider] ?? Math.max(...Object.values(STT_USD_PER_MIN));
  return Math.ceil((audioMs / 60_000) * rate * 1_000_000);
}

/** USD per MTok equals micro-USD per token. */
export function llmCostMicroUsd(u: LlmUsage): number {
  const p = HAIKU_USD_PER_MTOK;
  return Math.ceil(
    u.input_tokens * p.input +
      u.output_tokens * p.output +
      (u.cache_read_input_tokens ?? 0) * p.cacheRead +
      (u.cache_creation_input_tokens ?? 0) * p.cacheWrite,
  );
}

export interface SpendRow {
  day: string;
  stt_micro_usd: number;
  llm_micro_usd: number;
  calls: number;
  stt_failures: number;
  llm_failures: number;
  grounding_drops: number;
  alert80: number;
  alert100: number;
}

export async function getSpend(db: D1Database, day: string): Promise<SpendRow | null> {
  return db.prepare("SELECT * FROM spend_daily WHERE day = ?").bind(day).first<SpendRow>();
}

/** Throws 503 BUSY when the kill switch is on or today's estimated spend has reached the cap. */
export async function assertBudget(db: D1Database, cfg: RuntimeConfig, day: string): Promise<void> {
  if (cfg.killSwitch) throw new ApiError("BUSY", "Analysis is paused, try again later", 1800);
  const row = await getSpend(db, day);
  const spent = (row?.stt_micro_usd ?? 0) + (row?.llm_micro_usd ?? 0);
  if (spent >= cfg.dailyCapUsd * 1_000_000) throw new ApiError("BUSY", undefined, 1800);
}

export interface SpendDelta {
  sttMicroUsd?: number;
  llmMicroUsd?: number;
  calls?: number;
  sttFailures?: number;
  llmFailures?: number;
  groundingDrops?: number;
}

export type AlertSender = (message: string) => Promise<void>;

/** Adds to the ledger, then fires each threshold alert at most once per day (conditional UPDATE claims the flag). */
export async function recordSpend(
  db: D1Database,
  day: string,
  d: SpendDelta,
  capUsd: number,
  sendAlert: AlertSender,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO spend_daily (day, stt_micro_usd, llm_micro_usd, calls, stt_failures, llm_failures, grounding_drops)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
       ON CONFLICT (day) DO UPDATE SET stt_micro_usd = stt_micro_usd + ?2, llm_micro_usd = llm_micro_usd + ?3,
         calls = calls + ?4, stt_failures = stt_failures + ?5, llm_failures = llm_failures + ?6,
         grounding_drops = grounding_drops + ?7`,
    )
    .bind(day, d.sttMicroUsd ?? 0, d.llmMicroUsd ?? 0, d.calls ?? 0, d.sttFailures ?? 0, d.llmFailures ?? 0, d.groundingDrops ?? 0)
    .run();

  const capMicro = capUsd * 1_000_000;
  for (const [flag, pct] of [["alert80", 0.8], ["alert100", 1.0]] as const) {
    const claimed = await db
      .prepare(`UPDATE spend_daily SET ${flag} = 1 WHERE day = ? AND ${flag} = 0 AND stt_micro_usd + llm_micro_usd >= ?`)
      .bind(day, Math.floor(capMicro * pct))
      .run();
    if ((claimed.meta.changes ?? 0) > 0) {
      const label = pct >= 1 ? "reached 100% — new analyses now return BUSY" : "passed 80%";
      await sendAlert(`Mukhar spend ${label} of the $${capUsd}/day cap (quota day ${day}).`).catch(() => undefined);
    }
  }
}

/** Webhook sender: one JSON body that Discord (`content`), Slack (`text`) and ntfy (raw body) all display. */
export function webhookAlert(url: string | undefined, fetchFn: typeof fetch): AlertSender {
  return async (message: string) => {
    if (!url) return;
    await fetchFn(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ content: message, text: message }),
      signal: AbortSignal.timeout(5000),
    });
  };
}
