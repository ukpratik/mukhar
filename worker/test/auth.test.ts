import { exportJWK, exportPKCS8, generateKeyPair, createLocalJWKSet, SignJWT } from "jose";
import { describe, expect, it } from "vitest";
import { firebaseVerifier } from "../src/auth/firebase.ts";
import { checkVerdict, IntegrityError, requestHashFor } from "../src/auth/integrity.ts";
import { issueSessionToken, saltDeviceHash, SESSION_TTL_MS, verifySessionToken } from "../src/auth/session.ts";
import { googleAuth } from "../src/play/googleAuth.ts";
import { PKG, T0, validVerdict } from "./helpers.ts";

const PROJECT = "vaak-test";

async function firebaseSetup() {
  const { publicKey, privateKey } = await generateKeyPair("RS256", { extractable: true });
  const jwk = { ...(await exportJWK(publicKey)), kid: "k1", alg: "RS256", use: "sig" };
  const jwks = createLocalJWKSet({ keys: [jwk] });
  const sign = (claims: Record<string, unknown>, o: { iss?: string; aud?: string; exp?: number; sub?: string } = {}) =>
    new SignJWT(claims)
      .setProtectedHeader({ alg: "RS256", kid: "k1" })
      .setIssuer(o.iss ?? `https://securetoken.google.com/${PROJECT}`)
      .setAudience(o.aud ?? PROJECT)
      .setSubject(o.sub ?? "anon-uid-1")
      .setIssuedAt(Math.floor(T0 / 1000) - 60)
      .setExpirationTime(o.exp ?? Math.floor(T0 / 1000) + 3000)
      .sign(privateKey);
  return { jwks, sign };
}

describe("Firebase ID token verification (local JWKS, no network)", () => {
  it("accepts a valid anonymous token", async () => {
    const { jwks, sign } = await firebaseSetup();
    const v = firebaseVerifier(PROJECT, jwks, () => T0);
    expect(await v.verify(await sign({ firebase: { sign_in_provider: "anonymous" } }))).toEqual({ uid: "anon-uid-1" });
  });

  it.each([
    ["wrong audience", { aud: "other" }],
    ["wrong issuer", { iss: "https://evil.example" }],
    ["expired", { exp: Math.floor(T0 / 1000) - 10 }],
    ["empty subject", { sub: "" }],
  ])("rejects %s", async (_n, o) => {
    const { jwks, sign } = await firebaseSetup();
    await expect(firebaseVerifier(PROJECT, jwks, () => T0).verify(await sign({}, o))).rejects.toThrow();
  });

  it("rejects a token signed by another key", async () => {
    const a = await firebaseSetup();
    const b = await firebaseSetup();
    await expect(firebaseVerifier(PROJECT, a.jwks, () => T0).verify(await b.sign({}))).rejects.toThrow();
  });
});

describe("session token", () => {
  const key = "k".repeat(40);
  it("round-trips uid and device hash for 24 h", async () => {
    const { token, expiresAt } = await issueSessionToken(key, "u1", "dh1", T0);
    expect(expiresAt).toBe(T0 + SESSION_TTL_MS);
    expect(await verifySessionToken(key, token, T0 + 3600_000)).toMatchObject({ uid: "u1", dh: "dh1" });
  });

  it("reports expiry separately", async () => {
    const { token } = await issueSessionToken(key, "u1", "dh1", T0);
    await expect(verifySessionToken(key, token, T0 + SESSION_TTL_MS + 1000)).rejects.toMatchObject({ expired: true });
  });

  it("rejects tampering and a different key", async () => {
    const { token } = await issueSessionToken(key, "u1", "dh1", T0);
    await expect(verifySessionToken("x".repeat(40), token, T0)).rejects.toMatchObject({ expired: false });
    const [h, , s] = token.split(".");
    const forged = `${h}.${btoa(JSON.stringify({ sub: "u2", dh: "dh1", iss: "vaak-worker", exp: 9e9 })).replace(/=+$/, "")}.${s}`;
    await expect(verifySessionToken(key, forged, T0)).rejects.toThrow();
  });

  it("refuses a short HMAC key", async () => {
    await expect(issueSessionToken("short", "u1", "dh", T0)).rejects.toThrow();
  });

  it("salts the device hash deterministically per salt", async () => {
    const a = await saltDeviceHash("salt-a", "device");
    expect(a).toBe(await saltDeviceHash("salt-a", "device"));
    expect(a).not.toBe(await saltDeviceHash("salt-b", "device"));
    expect(a).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });
});

describe("Play Integrity verdict checks", () => {
  const minute = Math.floor(T0 / 60_000);
  const base = { packageName: PKG, uid: "u1", deviceHash: "dev-hash-0123456789", utcMinute: minute, nowMs: T0 };

  it("accepts a fresh, recognised, basic-integrity verdict", async () => {
    expect(await checkVerdict(await validVerdict("u1", base.deviceHash, minute, T0), base)).toEqual({ deviceStrong: false });
  });

  it("request hash is sha256 hex of uid|device_hash|utc_minute", async () => {
    expect(await requestHashFor("u", "d", 1)).toMatch(/^[0-9a-f]{64}$/);
  });

  const cases: Array<[string, (v: any) => void, string]> = [
    ["hash for another uid", (v) => { v.requestDetails.requestHash = "0".repeat(64); }, "request_hash"],
    ["stale timestamp", (v) => { v.requestDetails.timestampMillis = String(T0 - 11 * 60_000); }, "stale"],
    ["other package", (v) => { v.requestDetails.requestPackageName = "com.evil"; }, "package"],
    ["unrecognised app", (v) => { v.appIntegrity.appRecognitionVerdict = "UNRECOGNIZED_VERSION"; }, "app_not_recognized"],
    ["no device integrity", (v) => { v.deviceIntegrity.deviceRecognitionVerdict = []; }, "device"],
  ];
  it.each(cases)("rejects %s", async (_n, mutate, reason) => {
    const v = await validVerdict("u1", base.deviceHash, minute, T0);
    mutate(v);
    await expect(checkVerdict(v, base)).rejects.toMatchObject({ reason });
  });

  it("rejects a client minute far from server time", async () => {
    const v = await validVerdict("u1", base.deviceHash, minute - 30, T0);
    await expect(checkVerdict(v, { ...base, utcMinute: minute - 30 })).rejects.toBeInstanceOf(IntegrityError);
  });
});

describe("Google service-account OAuth", () => {
  it("signs an RS256 assertion, exchanges it and caches the token per scope", async () => {
    const { privateKey } = await generateKeyPair("RS256", { extractable: true });
    const sa = JSON.stringify({ client_email: "svc@vaak.iam.gserviceaccount.com", private_key: await exportPKCS8(privateKey) });
    const bodies: string[] = [];
    const f: typeof fetch = async (input, init) => {
      const r = new Request(input as RequestInfo, init);
      bodies.push(await r.text());
      return Response.json({ access_token: "ya29.test", expires_in: 3599 });
    };
    const auth = googleAuth(sa, f, () => T0);
    expect(await auth.accessToken("scope-a")).toBe("ya29.test");
    expect(await auth.accessToken("scope-a")).toBe("ya29.test");
    expect(bodies).toHaveLength(1);
    const params = new URLSearchParams(bodies[0]);
    expect(params.get("grant_type")).toBe("urn:ietf:params:oauth:grant-type:jwt-bearer");
    const payload = JSON.parse(atob(params.get("assertion")!.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/")));
    expect(payload).toMatchObject({ iss: "svc@vaak.iam.gserviceaccount.com", aud: "https://oauth2.googleapis.com/token", scope: "scope-a" });
  });

  it("fails clearly without a service account", async () => {
    await expect(googleAuth(undefined, fetch).accessToken("s")).rejects.toThrow(/GOOGLE_SA_JSON/);
  });
});
