import { jwtVerify, SignJWT, errors as joseErrors } from "jose";

/** Session token (ADR-006): HS256 JWT, 24 h, bound to uid + salted device hash. Issued by POST /session. */
export const SESSION_TTL_MS = 24 * 3600_000;
const ISSUER = "mukhar-worker";

export interface SessionClaims { uid: string; dh: string; exp: number }

export class SessionError extends Error {
  readonly expired: boolean;
  constructor(message: string, expired = false) {
    super(message);
    this.expired = expired;
  }
}

const keyBytes = (secret: string | undefined): Uint8Array => {
  if (!secret || secret.length < 32) throw new SessionError("SESSION_HMAC_KEY missing or shorter than 32 characters");
  return new TextEncoder().encode(secret);
};

export async function issueSessionToken(secret: string | undefined, uid: string, dh: string, nowMs: number): Promise<{ token: string; expiresAt: number }> {
  const expiresAt = nowMs + SESSION_TTL_MS;
  const token = await new SignJWT({ dh })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setSubject(uid)
    .setIssuer(ISSUER)
    .setIssuedAt(Math.floor(nowMs / 1000))
    .setExpirationTime(Math.floor(expiresAt / 1000))
    .sign(keyBytes(secret));
  return { token, expiresAt };
}

export async function verifySessionToken(secret: string | undefined, token: string, nowMs: number): Promise<SessionClaims> {
  try {
    const { payload } = await jwtVerify(token, keyBytes(secret), { issuer: ISSUER, algorithms: ["HS256"], currentDate: new Date(nowMs) });
    if (typeof payload.sub !== "string" || typeof payload.dh !== "string" || typeof payload.exp !== "number") throw new SessionError("malformed session token");
    return { uid: payload.sub, dh: payload.dh, exp: payload.exp };
  } catch (e) {
    if (e instanceof SessionError) throw e;
    if (e instanceof joseErrors.JWTExpired) throw new SessionError("session token expired", true);
    throw new SessionError("invalid session token");
  }
}

/** Server-side salting of the client's ANDROID_ID hash (DEVICE_SALT secret), so stored keys can't be linked elsewhere. */
export async function saltDeviceHash(salt: string | undefined, clientHash: string): Promise<string> {
  if (!salt) throw new SessionError("DEVICE_SALT is not configured");
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(salt), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(clientHash)));
  return btoa(String.fromCharCode(...mac)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function sha256Hex(s: string): Promise<string> {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
  return Array.from(d, (b) => b.toString(16).padStart(2, "0")).join("");
}
