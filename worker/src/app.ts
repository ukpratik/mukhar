import { Hono, type Context } from "hono";
import type { RuntimeConfig } from "./db/queries.ts";
import { loadConfig } from "./db/queries.ts";
import type { Deps } from "./deps.ts";
import type { Env } from "./env.ts";
import { ApiError, errorBody } from "./errors.ts";
import { writeLog, type RequestLog } from "./log.ts";

export interface Vars {
  deps: Deps;
  requestId: string;
  log: Partial<RequestLog>;
  cfg?: RuntimeConfig;
  uid: string;
  dh: string;
}

export type AppEnv = { Bindings: Env; Variables: Vars };
export type Ctx = Context<AppEnv>;

export const newApp = () => new Hono<AppEnv>();

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const isUuid = (s: unknown): s is string => typeof s === "string" && UUID.test(s);

export function requestIdFrom(header: string | undefined, deps: Deps): string {
  return isUuid(header) ? header.toLowerCase() : deps.uuid();
}

export async function cfgOf(c: Ctx): Promise<RuntimeConfig> {
  const cached = c.get("cfg");
  if (cached) return cached;
  const cfg = await loadConfig(c.env.DB);
  c.set("cfg", cfg);
  return cfg;
}

/** JSON success response with request_id. */
export function ok<T extends Record<string, unknown>>(c: Ctx, body: T, status: 200 | 201 = 200) {
  return c.json({ ...body, request_id: c.get("requestId") }, status);
}

export function addLog(c: Ctx, fields: Partial<RequestLog>): void {
  c.set("log", { ...c.get("log"), ...fields });
}

export function handleError(err: Error, c: Ctx) {
  const apiErr = err instanceof ApiError ? err : new ApiError("INTERNAL");
  if (!(err instanceof ApiError)) addLog(c, { code: "INTERNAL" });
  else addLog(c, { code: apiErr.code });
  const headers: Record<string, string> = {};
  if (apiErr.retryAfterS !== undefined) headers["Retry-After"] = String(apiErr.retryAfterS);
  return c.json(errorBody(apiErr, c.get("requestId") ?? "unknown"), apiErr.status as 400, headers);
}

/** Emits the one PII-free JSON log line per request. */
export function finishLog(c: Ctx, startedMs: number, route: string): void {
  const deps = c.get("deps");
  writeLog({ ...c.get("log"), route, status: c.res.status, ms: deps.now() - startedMs, request_id: c.get("requestId") });
}
