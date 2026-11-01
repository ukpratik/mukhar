import { addDays, quotaDay, resetsAtMs } from "./quotaDay.ts";

/** Pure quota decision (LLD §4.7). Mirrors the Kotlin `QuotaMirror`; both run shared/test-vectors/quota.json. */
export type Tier = "free" | "premium";
export type QuotaKind = "talk" | "retake" | "baseline" | "check";

export interface Limits {
  free_talks_per_day: number;
  free_talk_max_s: number;
  premium_talks_per_day: number;
  premium_talk_max_s: number;
  free_checks_per_day: number;
  premium_checks_per_day: number;
  check_max_s: number;
  grace_ms: number;
}

export const DEFAULT_LIMITS: Limits = {
  free_talks_per_day: 1,
  free_talk_max_s: 120,
  premium_talks_per_day: 20,
  premium_talk_max_s: 180,
  free_checks_per_day: 10,
  premium_checks_per_day: 200,
  check_max_s: 15,
  grace_ms: 5000,
};

export interface Usage {
  uidTalks: number;
  deviceTalks: number;
  uidChecks: number;
  deviceChecks: number;
}

export const EMPTY_USAGE: Usage = { uidTalks: 0, deviceTalks: 0, uidChecks: 0, deviceChecks: 0 };

export interface ParentSession {
  uid: string;
  kind: string;
  retakeUsed: boolean;
}

export interface QuotaRequest {
  uid: string;
  tier: Tier;
  nowMs: number;
  kind: QuotaKind;
  durationMs: number;
  queued: boolean;
  parent?: ParentSession | undefined;
  /** D-031: whether a baseline was already analysed for this uid / this device hash. */
  baselineUsed?: { uid: boolean; device: boolean } | undefined;
}

export type Decision =
  | { allow: true; debitDay: string | null }
  | { allow: false; code: "QUOTA_EXHAUSTED" | "AUDIO_TOO_LONG" | "SESSION_STATE" | "BASELINE_USED"; retryAfterS?: number };

const talks = (u: Usage) => Math.max(u.uidTalks, u.deviceTalks);
const checks = (u: Usage) => Math.max(u.uidChecks, u.deviceChecks);

export function maxDurationMs(kind: QuotaKind, tier: Tier, limits: Limits): number {
  const capS = kind === "check" ? limits.check_max_s : tier === "premium" ? limits.premium_talk_max_s : limits.free_talk_max_s;
  return capS * 1000 + limits.grace_ms;
}

export function dailyLimit(kind: "talk" | "check", tier: Tier, limits: Limits): number {
  if (kind === "check") return tier === "premium" ? limits.premium_checks_per_day : limits.free_checks_per_day;
  return tier === "premium" ? limits.premium_talks_per_day : limits.free_talks_per_day;
}

export function decide(req: QuotaRequest, usage: (day: string) => Usage, limits: Limits = DEFAULT_LIMITS): Decision {
  if (req.durationMs > maxDurationMs(req.kind, req.tier, limits)) return { allow: false, code: "AUDIO_TOO_LONG" };

  const today = quotaDay(req.nowMs);
  const retryAfterS = Math.max(0, Math.floor((resetsAtMs(today) - req.nowMs) / 1000));

  switch (req.kind) {
    case "retake": {
      const p = req.parent;
      if (p && p.uid === req.uid && p.kind === "talk" && !p.retakeUsed) return { allow: true, debitDay: null };
      return { allow: false, code: "SESSION_STATE" };
    }
    case "baseline": {
      // D-031: free (no quota debit), once per uid and once per device hash.
      const u = req.baselineUsed;
      if (u?.uid || u?.device) return { allow: false, code: "BASELINE_USED" };
      return { allow: true, debitDay: null };
    }
    case "talk": {
      const limit = dailyLimit("talk", req.tier, limits);
      if (talks(usage(today)) < limit) return { allow: true, debitDay: today };
      const tomorrow = addDays(today, 1);
      if (req.queued && talks(usage(tomorrow)) < limit) return { allow: true, debitDay: tomorrow };
      return { allow: false, code: "QUOTA_EXHAUSTED", retryAfterS };
    }
    case "check": {
      const limit = dailyLimit("check", req.tier, limits);
      if (checks(usage(today)) < limit) return { allow: true, debitDay: today };
      return { allow: false, code: "QUOTA_EXHAUSTED", retryAfterS };
    }
  }
}
