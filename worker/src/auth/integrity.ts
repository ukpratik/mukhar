import type { GoogleAuth } from "../play/googleAuth.ts";
import { sha256Hex } from "./session.ts";

/**
 * Play Integrity Standard verdict (ADR-006, HLD §5.2). The app requests a token with
 * requestHash = lowercase hex SHA-256 of UTF-8 "<uid>|<device_hash>|<utc_minute>" and the Worker decodes it with
 * decodeIntegrityToken, then checks: requestHash matches, timestamp within 10 min, package name, PLAY_RECOGNIZED and
 * MEETS_BASIC_INTEGRITY (device integrity beyond basic is logged only, to not exclude budget phones).
 */
export interface IntegrityVerdict {
  requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string | number };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
  accountDetails?: { appLicensingVerdict?: string };
}

export interface IntegrityDecoder {
  decode(integrityToken: string): Promise<IntegrityVerdict>;
}

export class IntegrityError extends Error {
  readonly reason: string;
  constructor(reason: string) {
    super(`integrity check failed: ${reason}`);
    this.reason = reason;
  }
}

export const MAX_VERDICT_AGE_MS = 10 * 60_000;

export const requestHashFor = (uid: string, deviceHash: string, utcMinute: number) => sha256Hex(`${uid}|${deviceHash}|${utcMinute}`);

export async function checkVerdict(
  v: IntegrityVerdict,
  x: { packageName: string; uid: string; deviceHash: string; utcMinute: number; nowMs: number },
): Promise<{ deviceStrong: boolean }> {
  if (Math.abs(x.nowMs / 60_000 - x.utcMinute) > 10) throw new IntegrityError("utc_minute_skew");
  const expected = await requestHashFor(x.uid, x.deviceHash, x.utcMinute);
  if (v.requestDetails?.requestHash !== expected) throw new IntegrityError("request_hash");
  const ts = Number(v.requestDetails?.timestampMillis);
  if (!Number.isFinite(ts) || Math.abs(x.nowMs - ts) > MAX_VERDICT_AGE_MS) throw new IntegrityError("stale");
  if ((v.requestDetails?.requestPackageName ?? v.appIntegrity?.packageName) !== x.packageName) throw new IntegrityError("package");
  if (v.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") throw new IntegrityError("app_not_recognized");
  const device = v.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  if (!device.includes("MEETS_BASIC_INTEGRITY") && !device.includes("MEETS_DEVICE_INTEGRITY") && !device.includes("MEETS_STRONG_INTEGRITY")) {
    throw new IntegrityError("device");
  }
  return { deviceStrong: device.includes("MEETS_DEVICE_INTEGRITY") || device.includes("MEETS_STRONG_INTEGRITY") };
}

export const INTEGRITY_SCOPE = "https://www.googleapis.com/auth/playintegrity";

export function googleIntegrityDecoder(packageName: string, auth: GoogleAuth, fetchFn: typeof fetch): IntegrityDecoder {
  return {
    async decode(integrityToken: string) {
      const token = await auth.accessToken(INTEGRITY_SCOPE);
      const res = await fetchFn(`https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" },
        body: JSON.stringify({ integrity_token: integrityToken }),
        signal: AbortSignal.timeout(8000),
      });
      if (res.status === 400) throw new IntegrityError("token_invalid");
      if (!res.ok) throw new Error(`playintegrity ${res.status}`);
      const body = (await res.json()) as { tokenPayloadExternal?: IntegrityVerdict };
      if (!body.tokenPayloadExternal) throw new IntegrityError("empty_verdict");
      return body.tokenPayloadExternal;
    },
  };
}
