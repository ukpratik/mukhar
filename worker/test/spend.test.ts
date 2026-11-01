import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { DEFAULT_CONFIG } from "../src/db/queries.ts";
import { ApiError } from "../src/errors.ts";
import { assertBudget, getSpend, llmCostMicroUsd, recordSpend, sttCostMicroUsd } from "../src/spend/ledger.ts";

const DAY = "2026-11-02";

describe("cost estimates (micro-USD)", () => {
  it("STT per audio minute", () => {
    expect(sttCostMicroUsd("deepgram", 60_000)).toBe(5800);
    expect(sttCostMicroUsd("elevenlabs", 120_000)).toBe(7400);
    expect(sttCostMicroUsd("unknown", 60_000)).toBe(6000);
  });

  it("Haiku 5.5 usage incl. cache reads and writes", () => {
    expect(llmCostMicroUsd({ input_tokens: 1000, output_tokens: 1000, cache_read_input_tokens: 2000, cache_creation_input_tokens: 0 })).toBe(620);
    expect(llmCostMicroUsd({ input_tokens: 0, output_tokens: 0, cache_creation_input_tokens: 2000 })).toBe(250);
  });
});

describe("spend cap ($5/day) and alerts", () => {
  it("allows under the cap, 503 BUSY at the cap", async () => {
    await assertBudget(env.DB, DEFAULT_CONFIG, DAY);
    await recordSpend(env.DB, DAY, { sttMicroUsd: 4_999_999 }, 5, async () => undefined);
    await assertBudget(env.DB, DEFAULT_CONFIG, DAY);
    await recordSpend(env.DB, DAY, { llmMicroUsd: 1 }, 5, async () => undefined);
    const err = await assertBudget(env.DB, DEFAULT_CONFIG, DAY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe("BUSY");
    expect(err.status).toBe(503);
  });

  it("kill switch → BUSY regardless of spend", async () => {
    const err = await assertBudget(env.DB, { ...DEFAULT_CONFIG, killSwitch: true }, DAY).catch((e) => e);
    expect(err.code).toBe("BUSY");
  });

  it("alerts once at 80% and once at 100% per day", async () => {
    const alerts: string[] = [];
    const send = async (m: string) => { alerts.push(m); };
    await recordSpend(env.DB, DAY, { sttMicroUsd: 3_000_000, calls: 1 }, 5, send);
    expect(alerts).toHaveLength(0);
    await recordSpend(env.DB, DAY, { llmMicroUsd: 1_000_000, calls: 1 }, 5, send);
    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toContain("80%");
    await recordSpend(env.DB, DAY, { sttMicroUsd: 100, calls: 1 }, 5, send);
    expect(alerts).toHaveLength(1);
    await recordSpend(env.DB, DAY, { sttMicroUsd: 1_000_000, calls: 1 }, 5, send);
    expect(alerts).toHaveLength(2);
    expect(alerts[1]).toContain("100%");
    await recordSpend(env.DB, "2026-11-03", { sttMicroUsd: 4_500_000 }, 5, send);
    expect(alerts).toHaveLength(3);
    const row = await getSpend(env.DB, DAY);
    expect(row).toMatchObject({ calls: 4, alert80: 1, alert100: 1, stt_micro_usd: 4_000_100, llm_micro_usd: 1_000_000 });
  });

  it("a failing webhook never breaks the request", async () => {
    await expect(recordSpend(env.DB, DAY, { sttMicroUsd: 5_000_000 }, 5, async () => { throw new Error("down"); })).resolves.toBeUndefined();
  });
});
