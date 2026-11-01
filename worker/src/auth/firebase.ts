import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

/**
 * Firebase ID token verification (ADR-003/006): RS256 against Google's securetoken JWKS (cached by jose per isolate),
 * issuer https://securetoken.google.com/<project>, audience <project>, `sub` = Firebase UID (anonymous allowed).
 */
export interface FirebaseVerifier {
  verify(idToken: string): Promise<{ uid: string }>;
}

export const FIREBASE_JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

export class AuthError extends Error {}

let remoteJwks: JWTVerifyGetKey | null = null;

export function firebaseVerifier(projectId: string, jwks?: JWTVerifyGetKey, now?: () => number): FirebaseVerifier {
  return {
    async verify(idToken: string) {
      if (!projectId) throw new AuthError("FIREBASE_PROJECT_ID is not configured");
      const keys = jwks ?? (remoteJwks ??= createRemoteJWKSet(new URL(FIREBASE_JWKS_URL)));
      try {
        const { payload } = await jwtVerify(idToken, keys, {
          issuer: `https://securetoken.google.com/${projectId}`,
          audience: projectId,
          algorithms: ["RS256"],
          ...(now ? { currentDate: new Date(now()) } : {}),
        });
        if (typeof payload.sub !== "string" || payload.sub.length === 0 || payload.sub.length > 128) throw new AuthError("invalid sub");
        if (typeof payload.auth_time === "number" && payload.auth_time * 1000 > (now?.() ?? Date.now()) + 60_000) throw new AuthError("auth_time in the future");
        return { uid: payload.sub };
      } catch (e) {
        if (e instanceof AuthError) throw e;
        throw new AuthError("invalid Firebase ID token");
      }
    },
  };
}
