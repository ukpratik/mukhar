import { applyD1Migrations, env } from "cloudflare:test";
import { beforeEach } from "vitest";

await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);

const SEED_CONFIG: Record<string, string> = {
  kill_switch_analysis: "0", daily_cap_usd: "5", min_app_version: "1", prompt_version: "fb-2026-11-01a",
  stt_provider_en: "deepgram", stt_provider_hi: "deepgram", low_conf_threshold: "0.6", free_talks_per_day: "1",
  free_talk_max_s: "120", premium_talks_per_day: "20", premium_talk_max_s: "180", free_checks_per_day: "10",
  premium_checks_per_day: "200",
};

beforeEach(async () => {
  await env.DB.batch([
    ...["quota_usage", "quota_device", "sessions", "entitlements", "reports", "spend_daily", "baseline_used", "config"].map((t) => env.DB.prepare(`DELETE FROM ${t}`)),
    ...Object.entries(SEED_CONFIG).map(([k, v]) => env.DB.prepare("INSERT INTO config (key, value) VALUES (?, ?)").bind(k, v)),
  ]);
});
