import { createExecutionContext, env, waitOnExecutionContext } from "cloudflare:test";
import { requestHashFor, type IntegrityVerdict } from "../src/auth/integrity.ts";
import { issueSessionToken, saltDeviceHash } from "../src/auth/session.ts";
import type { Deps } from "../src/deps.ts";
import type { Env } from "../src/env.ts";
import { createApp } from "../src/index.ts";
import type { LlmRequest, LlmResponse } from "../src/llm/client.ts";
import type { PlaySubscription } from "../src/play/subscriptions.ts";
import deepgramFixture from "./fixtures/deepgram-en.json";

export const T0 = Date.parse("2026-11-02T05:00:00Z"); // quota day 2026-11-02, resets 21:30Z
export const DEVICE = "QW5kcm9pZElkSGFzaEJhc2U2NA==";
export const PKG = "io.github.ukpratik.mukhar";

type FetchHandler = (req: Request) => Response | Promise<Response>;

export interface Fakes {
  now: number;
  uuidSeq: number;
  /** URL prefix → handler. Anything else throws: tests never reach the real network. */
  routes: Array<[string, FetchHandler]>;
  calls: Request[];
  llm: Array<LlmResponse | Error>;
  llmCalls: LlmRequest[];
  verdict: IntegrityVerdict | Error;
  play: Record<string, PlaySubscription | Error>;
  ga4Deleted: string[];
  deps: Deps;
}

export function deepgramOk(body: unknown = deepgramFixture): FetchHandler {
  return () => Response.json(body);
}

export function makeFakes(): Fakes {
  const f: Fakes = {
    now: T0,
    uuidSeq: 0,
    routes: [
      ["https://api.deepgram.com/", deepgramOk()],
      ["https://alerts.test/", () => new Response("ok")],
    ],
    calls: [],
    llm: [],
    llmCalls: [],
    verdict: new Error("no verdict configured"),
    play: {},
    ga4Deleted: [],
    deps: undefined as unknown as Deps,
  };
  const fakeFetch: typeof fetch = async (input, init) => {
    const req = new Request(input as RequestInfo, init);
    f.calls.push(req.clone());
    const route = f.routes.find(([prefix]) => req.url.startsWith(prefix));
    if (!route) throw new Error(`unexpected network call to ${new URL(req.url).host}`);
    return route[1](req);
  };
  f.deps = {
    now: () => f.now,
    uuid: () => `00000000-0000-4000-8000-${String(++f.uuidSeq).padStart(12, "0")}`,
    fetch: fakeFetch,
    firebase: {
      async verify(token: string) {
        if (!token.startsWith("fb:")) throw new Error("bad token");
        return { uid: token.slice(3) };
      },
    },
    integrity: {
      async decode() {
        if (f.verdict instanceof Error) throw f.verdict;
        return f.verdict;
      },
    },
    llm: {
      async complete(req: LlmRequest) {
        f.llmCalls.push(req);
        const next = f.llm.shift();
        if (!next) throw new Error("no LLM response queued");
        if (next instanceof Error) throw next;
        return next;
      },
    },
    play: {
      async getSubscription(token: string) {
        const r = f.play[token];
        if (!r) throw new Error("no play fixture");
        if (r instanceof Error) throw r;
        return r;
      },
    },
    async ga4Delete(id: string) {
      f.ga4Deleted.push(id);
      return true;
    },
  };
  return f;
}

export function testEnv(over: Partial<Env> = {}): Env {
  return { ...(env as unknown as Env), ...over };
}

export async function call(f: Fakes, path: string, init: RequestInit = {}, envOver: Partial<Env> = {}): Promise<Response> {
  const app = createApp(() => f.deps);
  const ctx = createExecutionContext();
  const res = await app.request(`https://api.test${path}`, init, testEnv(envOver), ctx);
  await waitOnExecutionContext(ctx);
  return res;
}

export async function sessionToken(uid: string, nowMs = T0, device = DEVICE): Promise<string> {
  const dh = await saltDeviceHash(env.DEVICE_SALT, device);
  return (await issueSessionToken(env.SESSION_HMAC_KEY, uid, dh, nowMs)).token;
}

export async function authHeaders(uid: string, extra: Record<string, string> = {}, device = DEVICE): Promise<Record<string, string>> {
  return { Authorization: `Bearer fb:${uid}`, "X-Mukhar-Session": await sessionToken(uid, T0, device), "X-App-Version": "12", ...extra };
}

export async function validVerdict(uid: string, device: string, utcMinute: number, nowMs: number): Promise<IntegrityVerdict> {
  return {
    requestDetails: { requestPackageName: PKG, requestHash: await requestHashFor(uid, device, utcMinute), timestampMillis: String(nowMs - 1000) },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY"] },
  };
}

export function audioForm(meta: Record<string, unknown>, opts: { type?: string; bytes?: number } = {}): FormData {
  const form = new FormData();
  form.append("meta", JSON.stringify(meta));
  form.append("audio", new File([new Uint8Array(opts.bytes ?? 2048)], "talk.aac", { type: opts.type ?? "audio/aac" }));
  return form;
}

export function llmOk(json: unknown, usage = { input_tokens: 900, output_tokens: 600, cache_read_input_tokens: 1400, cache_creation_input_tokens: 0 }): LlmResponse {
  return { json, usage, stopReason: "end_turn", model: "claude-haiku-5-5", ms: 1200 };
}

export const uuid = (n: number) => `11111111-1111-4111-8111-${String(n).padStart(12, "0")}`;

export async function rows<T = Record<string, unknown>>(sql: string, ...binds: unknown[]): Promise<T[]> {
  return (await env.DB.prepare(sql).bind(...binds).all<T>()).results;
}
