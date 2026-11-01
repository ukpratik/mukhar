import { cfgOf, type Ctx } from "../app.ts";
import { latestEntitlement, readUsage, upsertEntitlement, type EntitlementRow, type RuntimeConfig } from "../db/queries.ts";
import { mapSubscription } from "../play/subscriptions.ts";
import { dailyLimit, type Tier } from "../quota/decide.ts";
import { quotaDay, resetsAt } from "../quota/quotaDay.ts";
import { webhookAlert, type AlertSender } from "../spend/ledger.ts";

export interface EntitlementView { premium: boolean; expiry_time: number | null; is_trial: boolean; auto_renewing: boolean }

const ENTITLED = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);

export function viewOf(row: EntitlementRow | null, nowMs: number): EntitlementView {
  const premium = !!row && ENTITLED.has(row.state ?? "") && (row.expiry_time ?? 0) > nowMs;
  return {
    premium,
    expiry_time: row?.expiry_time ?? null,
    is_trial: premium && row?.is_trial === 1,
    auto_renewing: row?.auto_renewing === 1,
  };
}

/**
 * Current tier from the cached entitlement. If the cache has expired but the subscription was auto-renewing, re-verify
 * with Play once (lazy refresh, no RTDN in R1). Play failures keep the cached view.
 */
export async function entitlementFor(c: Ctx): Promise<EntitlementView> {
  const deps = c.get("deps");
  const now = deps.now();
  const row = await latestEntitlement(c.env.DB, c.get("uid"));
  if (row && (row.expiry_time ?? 0) <= now && row.auto_renewing === 1) {
    try {
      const sub = await deps.play.getSubscription(row.purchase_token);
      const e = mapSubscription(sub, row.product_id, now);
      const updated: EntitlementRow = {
        ...row, state: e.state, expiry_time: e.expiryTime, auto_renewing: e.autoRenewing ? 1 : 0, is_trial: e.isTrial ? 1 : 0, verified_at: now,
      };
      await upsertEntitlement(c.env.DB, updated);
      return viewOf(updated, now);
    } catch {
      return viewOf(row, now);
    }
  }
  return viewOf(row, now);
}

export const tierOf = (e: EntitlementView): Tier => (e.premium ? "premium" : "free");

export async function quotaStatus(c: Ctx, tier: Tier, cfg: RuntimeConfig) {
  const day = quotaDay(c.get("deps").now());
  const dh = c.get("dh");
  const u = await readUsage(c.env.DB, c.get("uid"), dh ?? "", day);
  return {
    day,
    talks_used: Math.max(u.uidTalks, u.deviceTalks),
    talks_limit: dailyLimit("talk", tier, cfg.limits),
    checks_used: Math.max(u.uidChecks, u.deviceChecks),
    checks_limit: dailyLimit("check", tier, cfg.limits),
    resets_at: resetsAt(day),
  };
}

/** Alert sender that runs after the response (waitUntil) when an execution context exists. */
export function alertSender(c: Ctx): AlertSender {
  const send = webhookAlert(c.env.ALERT_WEBHOOK_URL, c.get("deps").fetch);
  return async (msg) => {
    const p = send(msg).catch(() => undefined);
    try { c.executionCtx.waitUntil(p); } catch { await p; }
  };
}

export { cfgOf };
