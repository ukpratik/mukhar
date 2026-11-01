import { describe, expect, it } from "vitest";
import vectors from "../../shared/test-vectors/quota.json";
import { decide, EMPTY_USAGE, type Limits, type QuotaKind, type Tier, type Usage } from "../src/quota/decide.ts";
import { addDays, quotaDay, resetsAt } from "../src/quota/quotaDay.ts";

const limits: Limits = vectors.limits;

describe("quota day (shared quota.json)", () => {
  it.each(vectors.quota_day.map((c) => [c.now, c] as const))("%s", (_n, c) => {
    const now = Date.parse(c.now);
    expect(quotaDay(now)).toBe(c.day);
    expect(resetsAt(quotaDay(now))).toBe(c.resets_at);
  });

  it("addDays crosses months and leap days", () => {
    expect(addDays("2028-02-28", 1)).toBe("2028-02-29");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2026-11-02", -35)).toBe("2026-09-28");
  });
});

describe("quota decisions (shared quota.json, same cases as the Kotlin QuotaMirrorTest)", () => {
  it.each(vectors.decide.map((c) => [c.name, c] as const))("%s", (_n, c) => {
    const usageMap = c.usage as Record<string, { uid_talks?: number; device_talks?: number; uid_checks?: number; device_checks?: number }>;
    const usage = (day: string): Usage => {
      const u = usageMap[day];
      return u ? { uidTalks: u.uid_talks ?? 0, deviceTalks: u.device_talks ?? 0, uidChecks: u.uid_checks ?? 0, deviceChecks: u.device_checks ?? 0 } : EMPTY_USAGE;
    };
    const p = (c as { parent?: { uid: string; kind: string; retake_used: boolean } }).parent;
    const d = decide(
      {
        uid: c.uid, tier: c.tier as Tier, nowMs: Date.parse(c.now), kind: c.kind as QuotaKind, durationMs: c.duration_ms, queued: c.queued,
        parent: p ? { uid: p.uid, kind: p.kind, retakeUsed: p.retake_used } : undefined,
      },
      usage,
      limits,
    );
    const e = c.expected as { allow: boolean; debit_day?: string | null; code?: string; retry_after_s?: number };
    if (e.allow) {
      expect(d).toEqual({ allow: true, debitDay: e.debit_day ?? null });
    } else {
      expect(d.allow).toBe(false);
      if (!d.allow) {
        expect(d.code).toBe(e.code);
        expect(d.retryAfterS).toBe(e.retry_after_s);
      }
    }
  });
});
