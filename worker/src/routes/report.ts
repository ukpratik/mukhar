import { ok, type Ctx } from "../app.ts";
import { insertReport } from "../db/queries.ts";
import { ApiError } from "../errors.ts";
import { asObj, jsonBody, reqEnum, reqStr } from "./validate.ts";

export const REPORT_TTL_MS = 90 * 86_400_000;
export const MAX_REPORT_BYTES = 64 * 1024;
export const REPORT_REASONS = ["wrong_transcript", "wrong_advice", "offensive", "other"] as const;

/** POST /report (US-016): stored only with explicit consent, deleted after 90 days by the cron. */
export async function reportRoute(c: Ctx) {
  const deps = c.get("deps");
  const body = await jsonBody(c.req);
  if (body.consent !== true) throw new ApiError("CONSENT_REQUIRED");
  reqStr(body, "session_id", 64);
  const reason = reqEnum(body, "reason", REPORT_REASONS);
  const lang = reqEnum(body, "lang", ["en", "hi"] as const);
  const promptVersion = reqStr(body, "prompt_version", 40, 0);
  const payloadJson = JSON.stringify(asObj(body.payload, "payload"));
  if (new TextEncoder().encode(payloadJson).length > MAX_REPORT_BYTES) throw new ApiError("BAD_REQUEST", "payload is too large");
  const id = deps.uuid();
  await insertReport(c.env.DB, { id, uid: c.get("uid"), reason, lang, promptVersion, payloadJson, nowMs: deps.now(), ttlMs: REPORT_TTL_MS });
  return ok(c, { report_id: id });
}
