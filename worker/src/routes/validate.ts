import { ApiError } from "../errors.ts";

/** Tiny input validators (explicit 400 BAD_REQUEST with the field name; never echoes values). */
export type Obj = Record<string, unknown>;

export function asObj(v: unknown, what = "body"): Obj {
  if (typeof v !== "object" || v === null || Array.isArray(v)) throw new ApiError("BAD_REQUEST", `${what} must be a JSON object`);
  return v as Obj;
}

export function reqStr(o: Obj, k: string, max: number, min = 1): string {
  const v = o[k];
  if (typeof v !== "string" || v.length < min || v.length > max) throw new ApiError("BAD_REQUEST", `${k} must be a string of ${min}-${max} characters`);
  return v;
}

export function optStr(o: Obj, k: string, max: number): string | undefined {
  return o[k] === undefined || o[k] === null ? undefined : reqStr(o, k, max, 0);
}

export function reqEnum<T extends string>(o: Obj, k: string, allowed: readonly T[]): T {
  const v = o[k];
  if (typeof v !== "string" || !(allowed as readonly string[]).includes(v)) throw new ApiError("BAD_REQUEST", `${k} must be one of ${allowed.join(", ")}`);
  return v as T;
}

export function reqInt(o: Obj, k: string, min: number, max: number): number {
  const v = o[k];
  if (typeof v !== "number" || !Number.isInteger(v) || v < min || v > max) throw new ApiError("BAD_REQUEST", `${k} must be an integer in ${min}..${max}`);
  return v;
}

export function optInt(o: Obj, k: string, min: number, max: number): number | undefined {
  return o[k] === undefined || o[k] === null ? undefined : reqInt(o, k, min, max);
}

export function reqBool(o: Obj, k: string): boolean {
  const v = o[k];
  if (typeof v !== "boolean") throw new ApiError("BAD_REQUEST", `${k} must be a boolean`);
  return v;
}

export function optBool(o: Obj, k: string, fallback: boolean): boolean {
  return o[k] === undefined ? fallback : reqBool(o, k);
}

export async function jsonBody(req: { json(): Promise<unknown> }): Promise<Obj> {
  let v: unknown;
  try { v = await req.json(); } catch { throw new ApiError("BAD_REQUEST", "body must be valid JSON"); }
  return asObj(v);
}

export const DEVICE_HASH = /^[A-Za-z0-9+/=_-]{16,128}$/;
