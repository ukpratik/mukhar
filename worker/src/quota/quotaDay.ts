/** Quota day (LLD §4.7, US-060): resets at 03:00 IST = 21:30 UTC, independent of any device clock. */
const SHIFT_MS = 2.5 * 3600_000;
const DAY_MS = 86_400_000;

export const quotaDay = (nowMs: number): string => new Date(nowMs + SHIFT_MS).toISOString().slice(0, 10);

/** End of `day`: 21:30Z on that calendar date (= 03:00 IST on the next date). */
export const resetsAt = (day: string): string => `${day}T21:30:00Z`;

export const resetsAtMs = (day: string): number => Date.parse(resetsAt(day));

export function addDays(day: string, n: number): string {
  return new Date(Date.parse(`${day}T00:00:00Z`) + n * DAY_MS).toISOString().slice(0, 10);
}
