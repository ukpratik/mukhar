import { addLog, isUuid, ok, type Ctx } from "../app.ts";
import { sha256Hex } from "../auth/session.ts";
import {
  getSession, getSessionByClientId, markTranscribed, readUsage, releaseQuota, reserveQuota, retakeUsed, setSessionStatus,
  upsertPendingSession, type Counter, type SessionRow,
} from "../db/queries.ts";
import { ApiError } from "../errors.ts";
import { dailyLimit, decide, maxDurationMs, type QuotaKind } from "../quota/decide.ts";
import { addDays, quotaDay } from "../quota/quotaDay.ts";
import { assertBudget, recordSpend, sttCostMicroUsd } from "../spend/ledger.ts";
import { SttError, transcribe, type TranscribeResult } from "../stt/index.ts";
import { alertSender, cfgOf, entitlementFor, quotaStatus, tierOf } from "./common.ts";
import { asObj, optBool, optInt, reqEnum, type Obj } from "./validate.ts";

/** Accepted upload types: ADTS AAC from the app (LLD §4.1); M4A/WAV etc. for remux fallback and the benchmark. */
export const AUDIO_TYPES = new Set(["audio/aac", "audio/aacp", "audio/x-aac", "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/wav", "audio/x-wav", "audio/mpeg", "audio/ogg", "audio/webm"]);
/** 64 kbps budget (2× the app's 32 kbps AAC) plus 64 KB of headroom. */
const bytesFor = (durationMs: number) => Math.ceil(durationMs / 1000) * 8000 + 65_536;
export const MIN_WORDS: Record<QuotaKind, number> = { talk: 5, retake: 5, check: 1 };
const DEBITED = new Set(["transcribed", "feedback_ready", "feedback_failed"]);

export interface Multipart { meta: Obj; audio: File }

export async function readMultipart(c: Ctx): Promise<Multipart> {
  const ct = c.req.header("content-type") ?? "";
  if (!ct.toLowerCase().startsWith("multipart/form-data")) throw new ApiError("BAD_REQUEST", "multipart/form-data expected");
  let form: FormData;
  try { form = await c.req.formData(); } catch { throw new ApiError("BAD_REQUEST", "invalid multipart body"); }
  const metaRaw = form.get("meta");
  const audio = form.get("audio");
  if (typeof metaRaw !== "string") throw new ApiError("BAD_REQUEST", "meta part missing");
  let meta: unknown;
  try { meta = JSON.parse(metaRaw); } catch { throw new ApiError("BAD_REQUEST", "meta must be JSON"); }
  if (!audio || typeof audio === "string") throw new ApiError("BAD_REQUEST", "audio part missing");
  const type = (audio.type || "").split(";")[0]!.trim().toLowerCase();
  if (!AUDIO_TYPES.has(type)) throw new ApiError("UNSUPPORTED_MEDIA");
  if (audio.size === 0) throw new ApiError("TOO_LITTLE_SPEECH");
  return { meta: asObj(meta, "meta"), audio };
}

export interface TranscriptionRun {
  session: { id: string; kind: QuotaKind };
  result: TranscribeResult;
  release(): Promise<void>;
}

/**
 * Shared by /transcribe and /check-sentence: idempotency on (uid, client_session_id), quota decision + reservation,
 * spend cap, STT, post-checks, session bookkeeping. Quota is reserved before STT and released on any failure.
 */
export async function runTranscription(
  c: Ctx,
  x: { clientSessionId: string; kind: QuotaKind; lang: "en" | "hi"; durationMs: number; queued: boolean; parentSessionId: string | null; keyterms: string[]; audio: File },
): Promise<TranscriptionRun> {
  const deps = c.get("deps");
  const db = c.env.DB;
  const uid = c.get("uid");
  const dh = c.get("dh");
  const now = deps.now();
  const cfg = await cfgOf(c);
  const tier = tierOf(await entitlementFor(c));
  const cap = maxDurationMs(x.kind, tier, cfg.limits);

  if (x.durationMs > cap) throw new ApiError("AUDIO_TOO_LONG");
  if (x.audio.size > bytesFor(cap)) throw new ApiError("AUDIO_TOO_LONG", "Recording file is too large");

  const today = quotaDay(now);
  const existing = await getSessionByClientId(db, uid, x.clientSessionId);
  if (existing && existing.kind !== x.kind) throw new ApiError("SESSION_STATE", "client_session_id was used for another kind");
  const alreadyDebited = existing !== null && DEBITED.has(existing.status);
  const counter: Counter = x.kind === "check" ? "checks" : "talks";

  // Spend cap / kill switch before any paid call, and before touching quota (no quota used when busy).
  await assertBudget(db, cfg, today);

  let debitDay: string | null = alreadyDebited ? existing.quota_day : null;
  let reserved = false;
  let parentId: string | null = existing?.parent_id ?? null;

  if (!alreadyDebited) {
    let parent: { uid: string; kind: string; retakeUsed: boolean } | undefined;
    if (x.kind === "retake" && x.parentSessionId) {
      const p: SessionRow | null = await getSession(db, x.parentSessionId);
      if (p) {
        parent = { uid: p.uid, kind: p.kind, retakeUsed: await retakeUsed(db, p.id, existing?.id ?? null) };
        parentId = p.id;
      }
    }
    const usageCache = new Map<string, Awaited<ReturnType<typeof readUsage>>>();
    for (const d of [today, addDays(today, 1)]) usageCache.set(d, await readUsage(db, uid, dh, d));
    const decision = decide(
      { uid, tier, nowMs: now, kind: x.kind, durationMs: x.durationMs, queued: x.queued, parent },
      (d) => usageCache.get(d)!,
      cfg.limits,
    );
    if (!decision.allow) throw new ApiError(decision.code, undefined, decision.retryAfterS);
    debitDay = decision.debitDay;
    if (debitDay) {
      await reserveQuota(db, uid, dh, debitDay, counter);
      reserved = true;
      // Concurrent requests: re-read after the reservation and back out if we went over.
      const after = await readUsage(db, uid, dh, debitDay);
      const used = counter === "talks" ? Math.max(after.uidTalks, after.deviceTalks) : Math.max(after.uidChecks, after.deviceChecks);
      if (used > dailyLimit(x.kind === "check" ? "check" : "talk", tier, cfg.limits)) {
        await releaseQuota(db, uid, dh, debitDay, counter);
        throw new ApiError("QUOTA_EXHAUSTED");
      }
    }
  }

  const sessionId = existing?.id ?? deps.uuid();
  await upsertPendingSession(db, { id: sessionId, uid, clientSessionId: x.clientSessionId, kind: x.kind, parentId, quotaDay: debitDay, nowMs: now });

  const release = async () => {
    if (reserved && debitDay) await releaseQuota(db, uid, dh, debitDay, counter);
    reserved = false;
    await setSessionStatus(db, sessionId, alreadyDebited ? existing.status : "failed");
  };

  const providerSpec = x.lang === "hi" ? cfg.sttProviderHi : cfg.sttProviderEn;
  let result: TranscribeResult;
  try {
    result = await transcribe(
      await x.audio.arrayBuffer(),
      { lang: x.lang, keyterms: x.keyterms, mimeType: (x.audio.type || "audio/aac").split(";")[0]!, providerSpec },
      {
        fetch: deps.fetch,
        keys: { deepgram: c.env.DEEPGRAM_API_KEY, sarvam: c.env.SARVAM_API_KEY, elevenlabs: c.env.ELEVENLABS_API_KEY },
      },
    );
  } catch (e) {
    await release();
    await recordSpend(db, today, { sttFailures: 1 }, cfg.dailyCapUsd, alertSender(c));
    addLog(c, { provider: providerSpec.split(":")[0] });
    if (e instanceof SttError && e.kind === "timeout") throw new ApiError("STT_TIMEOUT");
    throw new ApiError("STT_FAILED");
  }

  addLog(c, { provider: result.provider, audio_ms: result.durationMs });
  await recordSpend(db, today, { sttMicroUsd: sttCostMicroUsd(result.provider, result.durationMs), calls: 1 }, cfg.dailyCapUsd, alertSender(c));

  // Post-checks on what the provider actually heard (the client's duration is only a hint).
  if (result.durationMs > cap) { await release(); throw new ApiError("AUDIO_TOO_LONG"); }
  if (result.words.length < MIN_WORDS[x.kind]) { await release(); throw new ApiError("TOO_LITTLE_SPEECH"); }

  const hash = await transcriptHash(result.words.map((w) => w.w));
  await markTranscribed(db, sessionId, result.durationMs, result.words.length, hash);
  return { session: { id: sessionId, kind: x.kind }, result, release };
}

/** Binds /feedback to this exact transcript: SHA-256 hex of the JSON array of word strings. */
export const transcriptHash = (words: string[]) => sha256Hex(JSON.stringify(words));

export async function transcribeRoute(c: Ctx) {
  const { meta, audio } = await readMultipart(c);
  const clientSessionId = meta.client_session_id;
  if (!isUuid(clientSessionId)) throw new ApiError("BAD_REQUEST", "client_session_id must be a UUID");
  const kind = reqEnum(meta, "kind", ["talk", "retake"] as const);
  const lang = reqEnum(meta, "lang", ["en", "hi"] as const);
  const durationMs = optInt(meta, "duration_ms", 0, 3_600_000) ?? 0;
  const queued = optBool(meta, "queued", false);
  const parent = meta.parent_session_id;
  if (kind === "retake" && !isUuid(parent)) throw new ApiError("SESSION_STATE", "A re-take needs parent_session_id");
  const keyterms = Array.isArray(meta.keyterms)
    ? meta.keyterms.filter((k): k is string => typeof k === "string" && k.length > 0 && k.length <= 60).slice(0, 20)
    : [];

  const run = await runTranscription(c, {
    clientSessionId, kind, lang, durationMs, queued, parentSessionId: isUuid(parent) ? parent : null, keyterms, audio,
  });
  const cfg = await cfgOf(c);
  const tier = tierOf(await entitlementFor(c));
  return ok(c, {
    session_id: run.session.id,
    provider: run.result.provider,
    model: run.result.model,
    duration_ms: run.result.durationMs,
    words: run.result.words,
    quota: await quotaStatus(c, tier, cfg),
  });
}
