/**
 * One JSON line per request (LLD §6). Only these allow-listed, PII-free fields are ever written:
 * never uid, device hash, tokens, transcript text, words or audio.
 */
export interface RequestLog {
  route: string;
  status: number;
  ms: number;
  code?: string;
  provider?: string;
  audio_ms?: number;
  in_tok?: number;
  out_tok?: number;
  cache_read_tok?: number;
  request_id: string;
}

const ALLOWED: ReadonlyArray<keyof RequestLog> = [
  "route", "status", "ms", "code", "provider", "audio_ms", "in_tok", "out_tok", "cache_read_tok", "request_id",
];

export function formatLog(fields: Partial<RequestLog> & Record<string, unknown>): string {
  const out: Record<string, unknown> = {};
  for (const k of ALLOWED) {
    const v = fields[k];
    if (v !== undefined && v !== null && (typeof v === "string" || typeof v === "number")) out[k] = v;
  }
  return JSON.stringify(out);
}

export function writeLog(fields: Partial<RequestLog>): void {
  console.log(formatLog(fields));
}

/** Operational events outside a request (cron, alerts). Same rule: counts and codes only. */
export function writeEvent(event: string, data: Record<string, string | number | boolean> = {}): void {
  console.log(JSON.stringify({ event, ...data }));
}
