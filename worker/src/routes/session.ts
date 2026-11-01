import { addLog, ok, type Ctx } from "../app.ts";
import { checkVerdict, IntegrityError } from "../auth/integrity.ts";
import { issueSessionToken, saltDeviceHash } from "../auth/session.ts";
import { ApiError } from "../errors.ts";
import { DEVICE_HASH, jsonBody, reqInt, reqStr } from "./validate.ts";

/** POST /session (US-064, ADR-006): Firebase ID token + Play Integrity verdict → 24 h session token. */
export async function sessionRoute(c: Ctx) {
  const deps = c.get("deps");
  const body = await jsonBody(c.req);
  const integrityToken = reqStr(body, "integrity_token", 16_384, 0);
  const deviceHash = reqStr(body, "device_hash", 128, 16);
  if (!DEVICE_HASH.test(deviceHash)) throw new ApiError("BAD_REQUEST", "device_hash must be base64");
  const utcMinute = reqInt(body, "utc_minute", 0, 2 ** 31);
  const uid = c.get("uid");
  const now = deps.now();

  const allowlisted = c.env.ENVIRONMENT !== "production" &&
    (c.env.DEBUG_ALLOWLIST_UIDS ?? "").split(",").map((s) => s.trim()).filter(Boolean).includes(uid);

  if (!allowlisted) {
    if (!integrityToken) throw new ApiError("INTEGRITY_FAILED");
    try {
      const verdict = await deps.integrity.decode(integrityToken);
      await checkVerdict(verdict, { packageName: c.env.PLAY_PACKAGE_NAME, uid, deviceHash, utcMinute, nowMs: now });
    } catch (e) {
      if (e instanceof IntegrityError) {
        addLog(c, { code: `INTEGRITY_FAILED:${e.reason}` });
        throw new ApiError("INTEGRITY_FAILED");
      }
      throw new ApiError("PLAY_UNAVAILABLE", "Integrity service unavailable");
    }
  }

  let dh: string;
  try { dh = await saltDeviceHash(c.env.DEVICE_SALT, deviceHash); } catch { throw new ApiError("INTERNAL"); }
  let issued;
  try { issued = await issueSessionToken(c.env.SESSION_HMAC_KEY, uid, dh, now); } catch { throw new ApiError("INTERNAL"); }
  return ok(c, { session_token: issued.token, expires_at: issued.expiresAt });
}
