/** Error codes and HTTP statuses (LLD §3). The envelope is `{"error":{code,message,retry_after_s?},"request_id"}`. */
export const ERRORS = {
  BAD_REQUEST: 400,
  PURCHASE_INVALID: 400,
  CONSENT_REQUIRED: 400,
  AUTH_INVALID: 401,
  SESSION_EXPIRED: 401,
  INTEGRITY_FAILED: 403,
  NOT_FOUND: 404,
  SESSION_NOT_FOUND: 404,
  SESSION_STATE: 409,
  AUDIO_TOO_LONG: 413,
  UNSUPPORTED_MEDIA: 415,
  TOO_LITTLE_SPEECH: 422,
  UPDATE_REQUIRED: 426,
  QUOTA_EXHAUSTED: 429,
  RATE_LIMITED: 429,
  INTERNAL: 500,
  STT_FAILED: 502,
  LLM_FAILED: 502,
  PLAY_UNAVAILABLE: 502,
  BUSY: 503,
  STT_TIMEOUT: 504,
} as const;

export type ErrorCode = keyof typeof ERRORS;

const DEFAULT_MESSAGES: Record<ErrorCode, string> = {
  BAD_REQUEST: "Invalid request",
  PURCHASE_INVALID: "Purchase could not be verified",
  CONSENT_REQUIRED: "Consent is required to send a report",
  AUTH_INVALID: "Sign-in is invalid",
  SESSION_EXPIRED: "Session expired",
  INTEGRITY_FAILED: "This app or device could not be verified",
  NOT_FOUND: "Not found",
  SESSION_NOT_FOUND: "Session not found",
  SESSION_STATE: "Session is not in a valid state for this request",
  AUDIO_TOO_LONG: "Recording is too long",
  UNSUPPORTED_MEDIA: "Unsupported audio format",
  TOO_LITTLE_SPEECH: "Too little speech to analyse",
  UPDATE_REQUIRED: "Please update the app",
  QUOTA_EXHAUSTED: "Daily limit reached",
  RATE_LIMITED: "Too many requests",
  INTERNAL: "Something went wrong",
  STT_FAILED: "Transcription failed",
  LLM_FAILED: "Feedback failed",
  PLAY_UNAVAILABLE: "Google Play is unavailable",
  BUSY: "Busy, try again later",
  STT_TIMEOUT: "Transcription timed out",
};

export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly retryAfterS: number | undefined;
  constructor(code: ErrorCode, message?: string, retryAfterS?: number) {
    super(message ?? DEFAULT_MESSAGES[code]);
    this.code = code;
    this.status = ERRORS[code];
    this.retryAfterS = retryAfterS;
  }
}

export function errorBody(err: ApiError, requestId: string) {
  return {
    error: {
      code: err.code,
      message: err.message,
      ...(err.retryAfterS !== undefined ? { retry_after_s: err.retryAfterS } : {}),
    },
    request_id: requestId,
  };
}
