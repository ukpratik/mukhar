import { DEFAULT_LIMITS, type Limits, type Usage } from "../quota/decide.ts";

/** All D1 access (LLD §2.2). Column names for counters come from fixed unions, never from input. */

// ---------------------------------------------------------------- config
export interface RuntimeConfig {
  killSwitch: boolean;
  dailyCapUsd: number;
  minAppVersion: number;
  promptVersion: string;
  sttProviderEn: string;
  sttProviderHi: string;
  lowConfThreshold: number;
  limits: Limits;
}

export const DEFAULT_CONFIG: RuntimeConfig = {
  killSwitch: false,
  dailyCapUsd: 5,
  minAppVersion: 1,
  promptVersion: "fb-2026-11-01a",
  sttProviderEn: "deepgram",
  sttProviderHi: "deepgram",
  lowConfThreshold: 0.6,
  limits: DEFAULT_LIMITS,
};

function num(v: string | undefined, fallback: number): number {
  if (v === undefined) return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

export async function loadConfig(db: D1Database): Promise<RuntimeConfig> {
  const { results } = await db.prepare("SELECT key, value FROM config").all<{ key: string; value: string }>();
  const m = new Map(results.map((r) => [r.key, r.value]));
  const d = DEFAULT_CONFIG;
  return {
    killSwitch: m.get("kill_switch_analysis") === "1" || m.get("kill_switch_analysis") === "true",
    dailyCapUsd: num(m.get("daily_cap_usd"), d.dailyCapUsd),
    minAppVersion: num(m.get("min_app_version"), d.minAppVersion),
    promptVersion: m.get("prompt_version") ?? d.promptVersion,
    sttProviderEn: m.get("stt_provider_en") ?? d.sttProviderEn,
    sttProviderHi: m.get("stt_provider_hi") ?? d.sttProviderHi,
    lowConfThreshold: num(m.get("low_conf_threshold"), d.lowConfThreshold),
    limits: {
      ...DEFAULT_LIMITS,
      free_talks_per_day: num(m.get("free_talks_per_day"), DEFAULT_LIMITS.free_talks_per_day),
      free_talk_max_s: num(m.get("free_talk_max_s"), DEFAULT_LIMITS.free_talk_max_s),
      premium_talks_per_day: num(m.get("premium_talks_per_day"), DEFAULT_LIMITS.premium_talks_per_day),
      premium_talk_max_s: num(m.get("premium_talk_max_s"), DEFAULT_LIMITS.premium_talk_max_s),
      free_checks_per_day: num(m.get("free_checks_per_day"), DEFAULT_LIMITS.free_checks_per_day),
      premium_checks_per_day: num(m.get("premium_checks_per_day"), DEFAULT_LIMITS.premium_checks_per_day),
    },
  };
}

// ---------------------------------------------------------------- quota
export type Counter = "talks" | "checks";

export async function readUsage(db: D1Database, uid: string, deviceHash: string, day: string): Promise<Usage> {
  const [u, d] = await db.batch<{ talks: number; checks: number }>([
    db.prepare("SELECT talks, checks FROM quota_usage WHERE uid = ? AND quota_day = ?").bind(uid, day),
    db.prepare("SELECT talks, checks FROM quota_device WHERE device_hash = ? AND quota_day = ?").bind(deviceHash, day),
  ]);
  const ur = u?.results[0];
  const dr = d?.results[0];
  return {
    uidTalks: ur?.talks ?? 0,
    uidChecks: ur?.checks ?? 0,
    deviceTalks: dr?.talks ?? 0,
    deviceChecks: dr?.checks ?? 0,
  };
}

/** Reserve one unit on both keys (uid and device). Committed by default; `releaseQuota` undoes it on failure. */
export async function reserveQuota(db: D1Database, uid: string, deviceHash: string, day: string, counter: Counter): Promise<void> {
  const t = counter === "talks" ? 1 : 0;
  const c = counter === "checks" ? 1 : 0;
  await db.batch([
    db.prepare(
      `INSERT INTO quota_usage (uid, quota_day, talks, checks) VALUES (?, ?, ?, ?)
       ON CONFLICT (uid, quota_day) DO UPDATE SET ${counter} = ${counter} + 1`,
    ).bind(uid, day, t, c),
    db.prepare(
      `INSERT INTO quota_device (device_hash, quota_day, talks, checks) VALUES (?, ?, ?, ?)
       ON CONFLICT (device_hash, quota_day) DO UPDATE SET ${counter} = ${counter} + 1`,
    ).bind(deviceHash, day, t, c),
  ]);
}

export async function releaseQuota(db: D1Database, uid: string, deviceHash: string, day: string, counter: Counter): Promise<void> {
  await db.batch([
    db.prepare(`UPDATE quota_usage SET ${counter} = MAX(${counter} - 1, 0) WHERE uid = ? AND quota_day = ?`).bind(uid, day),
    db.prepare(`UPDATE quota_device SET ${counter} = MAX(${counter} - 1, 0) WHERE device_hash = ? AND quota_day = ?`).bind(deviceHash, day),
  ]);
}

// ---------------------------------------------------------------- baseline (D-031)
/** Session IDs holding the baseline marker for this uid and this device (null = unused). */
export async function readBaseline(db: D1Database, uid: string, deviceHash: string): Promise<{ uid: string | null; device: string | null }> {
  const [u, d] = await db.batch<{ session_id: string }>([
    db.prepare("SELECT session_id FROM baseline_used WHERE key_type = 'uid' AND key = ?").bind(uid),
    db.prepare("SELECT session_id FROM baseline_used WHERE key_type = 'device' AND key = ?").bind(deviceHash),
  ]);
  return { uid: u?.results[0]?.session_id ?? null, device: d?.results[0]?.session_id ?? null };
}

/** Claims both markers atomically-enough: INSERT OR IGNORE, then confirm both belong to this session. */
export async function claimBaseline(db: D1Database, uid: string, deviceHash: string, sessionId: string, nowMs: number): Promise<boolean> {
  await db.batch([
    db.prepare("INSERT OR IGNORE INTO baseline_used (key_type, key, session_id, created_at) VALUES ('uid', ?, ?, ?)").bind(uid, sessionId, nowMs),
    db.prepare("INSERT OR IGNORE INTO baseline_used (key_type, key, session_id, created_at) VALUES ('device', ?, ?, ?)").bind(deviceHash, sessionId, nowMs),
  ]);
  const now = await readBaseline(db, uid, deviceHash);
  if (now.uid === sessionId && now.device === sessionId) return true;
  await releaseBaseline(db, sessionId);
  return false;
}

export async function releaseBaseline(db: D1Database, sessionId: string): Promise<void> {
  await db.prepare("DELETE FROM baseline_used WHERE session_id = ?").bind(sessionId).run();
}

// ---------------------------------------------------------------- sessions
export type SessionStatus = "pending" | "transcribed" | "failed" | "feedback_ready" | "feedback_failed";

export interface SessionRow {
  id: string;
  uid: string;
  client_session_id: string;
  kind: string;
  parent_id: string | null;
  quota_day: string | null;
  audio_ms: number | null;
  word_count: number | null;
  transcript_sha256: string | null;
  status: SessionStatus;
  feedback_attempts: number;
  created_at: number;
}

export async function getSessionByClientId(db: D1Database, uid: string, clientSessionId: string): Promise<SessionRow | null> {
  return db.prepare("SELECT * FROM sessions WHERE uid = ? AND client_session_id = ?").bind(uid, clientSessionId).first<SessionRow>();
}

export async function getSession(db: D1Database, id: string): Promise<SessionRow | null> {
  return db.prepare("SELECT * FROM sessions WHERE id = ?").bind(id).first<SessionRow>();
}

export async function retakeUsed(db: D1Database, parentId: string, exceptId: string | null): Promise<boolean> {
  const row = await db
    .prepare("SELECT 1 AS x FROM sessions WHERE parent_id = ? AND kind = 'retake' AND status != 'failed' AND id != ? LIMIT 1")
    .bind(parentId, exceptId ?? "")
    .first<{ x: number }>();
  return row !== null;
}

export async function upsertPendingSession(
  db: D1Database,
  s: { id: string; uid: string; clientSessionId: string; kind: string; parentId: string | null; quotaDay: string | null; nowMs: number },
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO sessions (id, uid, client_session_id, kind, parent_id, quota_day, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)
       ON CONFLICT (uid, client_session_id) DO UPDATE SET status = 'pending', quota_day = excluded.quota_day,
         kind = excluded.kind, parent_id = excluded.parent_id`,
    )
    .bind(s.id, s.uid, s.clientSessionId, s.kind, s.parentId, s.quotaDay, s.nowMs)
    .run();
}

export async function markTranscribed(db: D1Database, id: string, audioMs: number, wordCount: number, sha256: string): Promise<void> {
  await db
    .prepare("UPDATE sessions SET status = 'transcribed', audio_ms = ?, word_count = ?, transcript_sha256 = ? WHERE id = ?")
    .bind(audioMs, wordCount, sha256, id)
    .run();
}

export async function setSessionStatus(db: D1Database, id: string, status: SessionStatus): Promise<void> {
  await db.prepare("UPDATE sessions SET status = ? WHERE id = ?").bind(status, id).run();
}

/** Atomically count a feedback attempt; returns false if the cap was already reached. */
export async function claimFeedbackAttempt(db: D1Database, id: string, maxAttempts: number): Promise<boolean> {
  const r = await db
    .prepare("UPDATE sessions SET feedback_attempts = feedback_attempts + 1 WHERE id = ? AND feedback_attempts < ?")
    .bind(id, maxAttempts)
    .run();
  return (r.meta.changes ?? 0) > 0;
}

// ---------------------------------------------------------------- entitlements
export interface EntitlementRow {
  purchase_token: string;
  uid: string;
  product_id: string | null;
  base_plan_id: string | null;
  offer_id: string | null;
  state: string | null;
  expiry_time: number | null;
  auto_renewing: number | null;
  is_trial: number | null;
  verified_at: number | null;
}

/** The entitlement with the latest expiry for this uid (premium or not). */
export async function latestEntitlement(db: D1Database, uid: string): Promise<EntitlementRow | null> {
  return db
    .prepare("SELECT * FROM entitlements WHERE uid = ? ORDER BY expiry_time DESC LIMIT 1")
    .bind(uid)
    .first<EntitlementRow>();
}

export async function upsertEntitlement(db: D1Database, e: EntitlementRow): Promise<void> {
  await db
    .prepare(
      `INSERT INTO entitlements (purchase_token, uid, product_id, base_plan_id, offer_id, state, expiry_time, auto_renewing, is_trial, verified_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT (purchase_token) DO UPDATE SET uid = excluded.uid, product_id = excluded.product_id,
         base_plan_id = excluded.base_plan_id, offer_id = excluded.offer_id, state = excluded.state,
         expiry_time = excluded.expiry_time, auto_renewing = excluded.auto_renewing, is_trial = excluded.is_trial,
         verified_at = excluded.verified_at`,
    )
    .bind(e.purchase_token, e.uid, e.product_id, e.base_plan_id, e.offer_id, e.state, e.expiry_time, e.auto_renewing, e.is_trial, e.verified_at)
    .run();
}

export async function deleteEntitlement(db: D1Database, purchaseToken: string): Promise<void> {
  await db.prepare("DELETE FROM entitlements WHERE purchase_token = ?").bind(purchaseToken).run();
}

// ---------------------------------------------------------------- reports
export async function insertReport(
  db: D1Database,
  r: { id: string; uid: string; reason: string; lang: string; promptVersion: string; payloadJson: string; nowMs: number; ttlMs: number },
): Promise<void> {
  await db
    .prepare("INSERT INTO reports (id, uid, reason, lang, prompt_version, payload_json, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
    .bind(r.id, r.uid, r.reason, r.lang, r.promptVersion, r.payloadJson, r.nowMs, r.nowMs + r.ttlMs)
    .run();
}

// ---------------------------------------------------------------- deletion and purge
/**
 * US-063: delete every row keyed by this uid. The device counter for the *current* quota day is kept (a salted hash and
 * a number, purged by the cron) so "delete my data" can't be used to reset the free quota; older device rows are deleted.
 */
export async function deleteUserData(db: D1Database, uid: string, deviceHash: string | null, today: string): Promise<void> {
  const stmts = [
    db.prepare("DELETE FROM quota_usage WHERE uid = ?").bind(uid),
    db.prepare("DELETE FROM sessions WHERE uid = ?").bind(uid),
    db.prepare("DELETE FROM entitlements WHERE uid = ?").bind(uid),
    db.prepare("DELETE FROM reports WHERE uid = ?").bind(uid),
    // D-031: the uid marker goes; the device marker (salted hash) stays so a reinstall can't get a second free baseline.
    db.prepare("DELETE FROM baseline_used WHERE key_type = 'uid' AND key = ?").bind(uid),
  ];
  if (deviceHash) stmts.push(db.prepare("DELETE FROM quota_device WHERE device_hash = ? AND quota_day < ?").bind(deviceHash, today));
  await db.batch(stmts);
}

/** Daily cron (LLD §2.2): reports past TTL, sessions > 7 d, quota rows > 35 d, stale entitlements, spend > 400 d. */
export async function purge(db: D1Database, nowMs: number, quotaCutoffDay: string, spendCutoffDay: string): Promise<void> {
  await db.batch([
    db.prepare("DELETE FROM reports WHERE expires_at < ?").bind(nowMs),
    db.prepare("DELETE FROM sessions WHERE created_at < ?").bind(nowMs - 7 * 86_400_000),
    db.prepare("DELETE FROM quota_usage WHERE quota_day < ?").bind(quotaCutoffDay),
    db.prepare("DELETE FROM quota_device WHERE quota_day < ?").bind(quotaCutoffDay),
    db.prepare("DELETE FROM entitlements WHERE expiry_time IS NOT NULL AND expiry_time < ?").bind(nowMs - 30 * 86_400_000),
    db.prepare("DELETE FROM spend_daily WHERE day < ?").bind(spendCutoffDay),
  ]);
}
