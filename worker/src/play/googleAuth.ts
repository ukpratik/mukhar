import { importPKCS8, SignJWT } from "jose";

/**
 * Google service-account OAuth (ADR-003): sign a JWT assertion with the SA key (RS256), exchange it at the token
 * endpoint, cache the access token per scope for ~55 min per isolate.
 */
export interface GoogleAuth {
  accessToken(scope: string): Promise<string>;
}

interface ServiceAccount { client_email: string; private_key: string; token_uri?: string }

export class GoogleAuthError extends Error {}

export function parseServiceAccount(json: string | undefined): ServiceAccount {
  if (!json) throw new GoogleAuthError("GOOGLE_SA_JSON is not configured");
  let sa: Partial<ServiceAccount>;
  try { sa = JSON.parse(json) as Partial<ServiceAccount>; } catch { throw new GoogleAuthError("GOOGLE_SA_JSON is not valid JSON"); }
  if (!sa.client_email || !sa.private_key) throw new GoogleAuthError("GOOGLE_SA_JSON lacks client_email or private_key");
  return sa as ServiceAccount;
}

export function googleAuth(saJson: string | undefined, fetchFn: typeof fetch, now: () => number = Date.now): GoogleAuth {
  const cache = new Map<string, { token: string; exp: number }>();
  return {
    async accessToken(scope: string) {
      const hit = cache.get(scope);
      if (hit && hit.exp > now()) return hit.token;
      const sa = parseServiceAccount(saJson);
      const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
      const key = await importPKCS8(sa.private_key, "RS256");
      const iat = Math.floor(now() / 1000);
      const assertion = await new SignJWT({ scope })
        .setProtectedHeader({ alg: "RS256", typ: "JWT" })
        .setIssuer(sa.client_email)
        .setAudience(tokenUri)
        .setIssuedAt(iat)
        .setExpirationTime(iat + 3600)
        .sign(key);
      const res = await fetchFn(tokenUri, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
        signal: AbortSignal.timeout(8000),
      });
      if (!res.ok) throw new GoogleAuthError(`token endpoint returned ${res.status}`);
      const body = (await res.json()) as { access_token?: string; expires_in?: number };
      if (!body.access_token) throw new GoogleAuthError("token endpoint returned no access_token");
      cache.set(scope, { token: body.access_token, exp: now() + Math.min(55 * 60, (body.expires_in ?? 3600) - 60) * 1000 });
      return body.access_token;
    },
  };
}
