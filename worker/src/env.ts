/** Worker bindings (wrangler.toml vars + D1) and secrets (`wrangler secret put`, LLD §7). */
export interface Env {
  DB: D1Database;
  ENVIRONMENT: "local" | "dev" | "production" | string;
  FIREBASE_PROJECT_ID: string;
  PLAY_PACKAGE_NAME: string;
  GA4_PROPERTY_ID?: string;
  // secrets
  ANTHROPIC_API_KEY?: string;
  DEEPGRAM_API_KEY?: string;
  SARVAM_API_KEY?: string;
  ELEVENLABS_API_KEY?: string;
  GOOGLE_SA_JSON?: string;
  SESSION_HMAC_KEY?: string;
  DEVICE_SALT?: string;
  ALERT_WEBHOOK_URL?: string;
  /** dev only: comma-separated Firebase UIDs whose debug builds may skip the Integrity verdict (ADR-006). */
  DEBUG_ALLOWLIST_UIDS?: string;
}
