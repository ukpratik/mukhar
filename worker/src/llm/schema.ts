/**
 * Feedback output schema (LLD §4.3). All fields required, `additionalProperties: false`. Counts (≤ 3 items, span length)
 * are enforced by the grounding validator, because structured outputs support only a subset of JSON Schema keywords.
 */
export const ISSUES = ["filler", "pace", "clarity", "weak_word", "repetition", "structure", "grammar"] as const;
export const REGISTERS = ["formal", "casual", "power"] as const;
export const SAFETY = ["none", "unsafe", "self_harm"] as const;

export type Issue = (typeof ISSUES)[number];
export type Register = (typeof REGISTERS)[number];
export type Safety = (typeof SAFETY)[number];

const span = { start: { type: "integer" }, end: { type: "integer" }, quote: { type: "string" } } as const;

export const FEEDBACK_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["summary", "glow", "grow", "better_versions", "mistakes", "safety"],
  properties: {
    summary: { type: "string" },
    glow: {
      type: "object",
      additionalProperties: false,
      required: ["start", "end", "quote", "note"],
      properties: { ...span, note: { type: "string" } },
    },
    grow: {
      type: "object",
      additionalProperties: false,
      required: ["start", "end", "quote", "issue", "why", "how"],
      properties: { ...span, issue: { type: "string", enum: [...ISSUES] }, why: { type: "string" }, how: { type: "string" } },
    },
    better_versions: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["start", "end", "quote", "rewrite", "upgraded_words"],
        properties: {
          ...span,
          rewrite: { type: "string" },
          upgraded_words: {
            type: "array",
            items: {
              type: "object",
              additionalProperties: false,
              required: ["word", "meaning", "register"],
              properties: { word: { type: "string" }, meaning: { type: "string" }, register: { type: "string", enum: [...REGISTERS] } },
            },
          },
        },
      },
    },
    mistakes: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["start", "end", "quote", "fix"],
        properties: { ...span, fix: { type: "string" } },
      },
    },
    safety: { type: "string", enum: [...SAFETY] },
  },
} as const;

export interface SpanItem { start: number; end: number; quote: string }
export interface Glow extends SpanItem { note: string }
export interface Grow extends SpanItem { issue: Issue; why: string; how: string }
export interface UpgradedWord { word: string; meaning: string; register: Register }
export interface BetterVersion extends SpanItem { rewrite: string; upgraded_words: UpgradedWord[] }
export interface Mistake extends SpanItem { fix: string }

export interface ModelFeedback {
  summary: string;
  glow: Glow;
  grow: Grow;
  better_versions: BetterVersion[];
  mistakes: Mistake[];
  safety: Safety;
}

export const SENTENCE_RESULTS = ["ok", "almost", "no"] as const;
export const SENTENCE_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["result", "corrected", "example", "note"],
  properties: {
    result: { type: "string", enum: [...SENTENCE_RESULTS] },
    corrected: { type: "string" },
    example: { type: "string" },
    note: { type: "string" },
  },
} as const;

export interface SentenceCheck { result: (typeof SENTENCE_RESULTS)[number]; corrected: string; example: string; note: string }

// ---------------------------------------------------------------- defensive structural parsing
export class SchemaError extends Error {}

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v);
const str = (o: Record<string, unknown>, k: string): string => {
  const v = o[k];
  if (typeof v !== "string") throw new SchemaError(`${k} must be a string`);
  return v;
};
const int = (o: Record<string, unknown>, k: string): number => {
  const v = o[k];
  if (typeof v !== "number" || !Number.isInteger(v)) throw new SchemaError(`${k} must be an integer`);
  return v;
};
const oneOf = <T extends string>(o: Record<string, unknown>, k: string, allowed: readonly T[]): T => {
  const v = str(o, k);
  if (!(allowed as readonly string[]).includes(v)) throw new SchemaError(`${k} has an unexpected value`);
  return v as T;
};
const spanOf = (o: Record<string, unknown>): SpanItem => ({ start: int(o, "start"), end: int(o, "end"), quote: str(o, "quote") });
const arr = (o: Record<string, unknown>, k: string): unknown[] => {
  const v = o[k];
  if (!Array.isArray(v)) throw new SchemaError(`${k} must be an array`);
  return v;
};

export function parseFeedback(v: unknown): ModelFeedback {
  if (!isObj(v) || !isObj(v.glow) || !isObj(v.grow)) throw new SchemaError("feedback must be an object with glow and grow");
  const g = v.glow;
  const r = v.grow;
  return {
    summary: str(v, "summary"),
    glow: { ...spanOf(g), note: str(g, "note") },
    grow: { ...spanOf(r), issue: oneOf(r, "issue", ISSUES), why: str(r, "why"), how: str(r, "how") },
    better_versions: arr(v, "better_versions").filter(isObj).map((b) => ({
      ...spanOf(b),
      rewrite: str(b, "rewrite"),
      upgraded_words: arr(b, "upgraded_words").filter(isObj).map((w) => ({
        word: str(w, "word"),
        meaning: str(w, "meaning"),
        register: oneOf(w, "register", REGISTERS),
      })),
    })),
    mistakes: arr(v, "mistakes").filter(isObj).map((m) => ({ ...spanOf(m), fix: str(m, "fix") })),
    safety: oneOf(v, "safety", SAFETY),
  };
}

export function parseSentenceCheck(v: unknown): SentenceCheck {
  if (!isObj(v)) throw new SchemaError("sentence check must be an object");
  return { result: oneOf(v, "result", SENTENCE_RESULTS), corrected: str(v, "corrected"), example: str(v, "example"), note: str(v, "note") };
}
