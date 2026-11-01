import { firebaseVerifier, type FirebaseVerifier } from "./auth/firebase.ts";
import { googleIntegrityDecoder, type IntegrityDecoder } from "./auth/integrity.ts";
import type { Env } from "./env.ts";
import { anthropicClient, type LlmClient } from "./llm/client.ts";
import { googleAuth } from "./play/googleAuth.ts";
import { deleteGa4AppInstance, googlePlayClient, type PlayClient } from "./play/subscriptions.ts";

/**
 * Everything that talks to the outside world, behind small interfaces so routes are testable with fakes
 * (no real network in tests). `fetch` is used by the STT adapters and the alert webhook.
 */
export interface Deps {
  now(): number;
  uuid(): string;
  fetch: typeof fetch;
  firebase: FirebaseVerifier;
  integrity: IntegrityDecoder;
  llm: LlmClient;
  play: PlayClient;
  ga4Delete(appInstanceId: string): Promise<boolean>;
}

const cache = new WeakMap<object, Deps>();

/** Production wiring; memoised per env object so JWKS and Google OAuth tokens are cached per isolate. */
export function defaultDeps(env: Env): Deps {
  const hit = cache.get(env);
  if (hit) return hit;
  const f: typeof fetch = (input, init) => fetch(input, init);
  const auth = googleAuth(env.GOOGLE_SA_JSON, f);
  const deps: Deps = {
    now: () => Date.now(),
    uuid: () => crypto.randomUUID(),
    fetch: f,
    firebase: firebaseVerifier(env.FIREBASE_PROJECT_ID),
    integrity: googleIntegrityDecoder(env.PLAY_PACKAGE_NAME, auth, f),
    llm: anthropicClient({ apiKey: env.ANTHROPIC_API_KEY, fetch: f }),
    play: googlePlayClient(env.PLAY_PACKAGE_NAME, auth, f),
    ga4Delete: (id) => deleteGa4AppInstance(env.GA4_PROPERTY_ID, id, auth, f),
  };
  cache.set(env, deps);
  return deps;
}
