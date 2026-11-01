import { describe, expect, it } from "vitest";
import { formatLog } from "../src/log.ts";
import { ApiError, ERRORS, errorBody } from "../src/errors.ts";

describe("PII-free logging", () => {
  it("keeps only allow-listed fields", () => {
    const line = JSON.parse(formatLog({ route: "POST /v1/feedback", status: 200, ms: 12, request_id: "r", uid: "secret-uid", transcript: "hello", words: ["a"] } as never));
    expect(Object.keys(line).sort()).toEqual(["ms", "request_id", "route", "status"]);
  });
});

describe("error envelope and mapping", () => {
  it("matches LLD §3", () => {
    expect(errorBody(new ApiError("QUOTA_EXHAUSTED", "Daily talk used", 31500), "rid")).toEqual({
      error: { code: "QUOTA_EXHAUSTED", message: "Daily talk used", retry_after_s: 31500 },
      request_id: "rid",
    });
  });

  it.each([
    ["AUTH_INVALID", 401], ["SESSION_EXPIRED", 401], ["INTEGRITY_FAILED", 403], ["SESSION_NOT_FOUND", 404], ["SESSION_STATE", 409], ["BASELINE_USED", 409],
    ["AUDIO_TOO_LONG", 413], ["UNSUPPORTED_MEDIA", 415], ["TOO_LITTLE_SPEECH", 422], ["UPDATE_REQUIRED", 426], ["QUOTA_EXHAUSTED", 429],
    ["RATE_LIMITED", 429], ["STT_FAILED", 502], ["LLM_FAILED", 502], ["PLAY_UNAVAILABLE", 502], ["BUSY", 503], ["STT_TIMEOUT", 504],
    ["PURCHASE_INVALID", 400], ["CONSENT_REQUIRED", 400],
  ] as const)("%s → %i", (code, status) => {
    expect(ERRORS[code]).toBe(status);
    expect(new ApiError(code).status).toBe(status);
  });
});
