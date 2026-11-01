import { ok, type Ctx } from "../app.ts";
import { saltDeviceHash } from "../auth/session.ts";
import { deleteUserData } from "../db/queries.ts";
import { quotaDay } from "../quota/quotaDay.ts";
import { writeEvent } from "../log.ts";
import { cfgOf, entitlementFor, quotaStatus, tierOf } from "./common.ts";
import { DEVICE_HASH, optStr, type Obj } from "./validate.ts";

export async function getMeRoute(c: Ctx) {
  const cfg = await cfgOf(c);
  const ent = await entitlementFor(c);
  return ok(c, { ...ent, quota: await quotaStatus(c, tierOf(ent), cfg) });
}

/**
 * DELETE /me (US-063): idempotent. Needs only the Firebase ID token (a user must be able to delete even if Integrity
 * fails). Deletes every row keyed by the uid; device rows for past days; GA4 user deletion runs after the response.
 */
export async function deleteMeRoute(c: Ctx) {
  const deps = c.get("deps");
  let body: Obj = {};
  try { body = ((await c.req.json()) as Obj) ?? {}; } catch { body = {}; }
  const rawDh = typeof body === "object" ? optStr(body, "device_hash", 128) : undefined;
  const appInstanceId = typeof body === "object" ? optStr(body, "app_instance_id", 128) : undefined;
  const dh = rawDh && DEVICE_HASH.test(rawDh) && c.env.DEVICE_SALT ? await saltDeviceHash(c.env.DEVICE_SALT, rawDh) : null;

  await deleteUserData(c.env.DB, c.get("uid"), dh, quotaDay(deps.now()));

  if (appInstanceId) {
    const p = deps.ga4Delete(appInstanceId)
      .then((done) => writeEvent("ga4_deletion", { submitted: done }))
      .catch(() => writeEvent("ga4_deletion", { submitted: false }));
    try { c.executionCtx.waitUntil(p); } catch { await p; }
  }
  return ok(c, { deleted: true });
}
