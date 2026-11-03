import type { MiddlewareHandler } from "hono";
import { SessionError, verifySessionToken } from "./auth/session.ts";
import { cfgOf, type AppEnv } from "./app.ts";
import { ApiError } from "./errors.ts";

const bearer = (h: string | undefined) => (h && h.startsWith("Bearer ") ? h.slice(7).trim() : "");

/** 426 when X-App-Version (versionCode) is below config.min_app_version (LLD §9). Missing header is tolerated. */
export const appVersionGate: MiddlewareHandler<AppEnv> = async (c, next) => {
  const v = c.req.header("X-App-Version");
  if (v !== undefined && v !== "") {
    const n = Number(v);
    const cfg = await cfgOf(c);
    if (!Number.isInteger(n) || n < cfg.minAppVersion) throw new ApiError("UPDATE_REQUIRED");
  }
  await next();
};

/** Firebase ID token → uid (401 AUTH_INVALID). */
export const requireFirebase: MiddlewareHandler<AppEnv> = async (c, next) => {
  const token = bearer(c.req.header("Authorization"));
  if (!token) throw new ApiError("AUTH_INVALID");
  try {
    const { uid } = await c.get("deps").firebase.verify(token);
    c.set("uid", uid);
  } catch {
    throw new ApiError("AUTH_INVALID");
  }
  await next();
};

/** X-Mukhar-Session bound to the same uid (401 SESSION_EXPIRED → the client refreshes once via /session). */
export const requireSession: MiddlewareHandler<AppEnv> = async (c, next) => {
  const token = c.req.header("X-Mukhar-Session");
  if (!token) throw new ApiError("SESSION_EXPIRED", "Session token missing");
  try {
    const claims = await verifySessionToken(c.env.SESSION_HMAC_KEY, token, c.get("deps").now());
    if (claims.uid !== c.get("uid")) throw new SessionError("session token belongs to another user");
    c.set("dh", claims.dh);
  } catch (e) {
    if (e instanceof SessionError) throw new ApiError("SESSION_EXPIRED", e.expired ? "Session expired" : "Session invalid");
    throw e;
  }
  await next();
};

/**
 * Per-uid request limit, 30 req/min (HLD §7). Best effort per isolate (no extra D1 writes on the hot path); the hard
 * guards are the D1 quotas and the spend cap. Scale trigger: move to the Workers rate-limit binding on abuse.
 */
const WINDOW_MS = 60_000;
export const RATE_LIMIT_PER_MIN = 30;
const hits = new Map<string, number[]>();

/** Test hook: clears the per-isolate counters. */
export function resetRateLimit(): void {
  hits.clear();
}

export function rateLimitCheck(uid: string, nowMs: number, limit = RATE_LIMIT_PER_MIN): number | null {
  const arr = (hits.get(uid) ?? []).filter((t) => nowMs - t < WINDOW_MS);
  if (arr.length >= limit) {
    hits.set(uid, arr);
    return Math.ceil((WINDOW_MS - (nowMs - arr[0]!)) / 1000);
  }
  arr.push(nowMs);
  hits.set(uid, arr);
  if (hits.size > 10_000) hits.clear();
  return null;
}

export const rateLimit: MiddlewareHandler<AppEnv> = async (c, next) => {
  const retry = rateLimitCheck(c.get("uid"), c.get("deps").now());
  if (retry !== null) throw new ApiError("RATE_LIMITED", undefined, retry);
  await next();
};
