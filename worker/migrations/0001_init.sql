-- Vaak D1 schema v1 (LLD §2.2). Migrations are additive only.
CREATE TABLE quota_usage (uid TEXT, quota_day TEXT, talks INT DEFAULT 0, checks INT DEFAULT 0, PRIMARY KEY (uid, quota_day));
CREATE TABLE quota_device (device_hash TEXT, quota_day TEXT, talks INT DEFAULT 0, checks INT DEFAULT 0, PRIMARY KEY (device_hash, quota_day));
CREATE TABLE sessions (id TEXT PRIMARY KEY, uid TEXT NOT NULL, client_session_id TEXT NOT NULL, kind TEXT NOT NULL, parent_id TEXT,
  quota_day TEXT, audio_ms INT, word_count INT, transcript_sha256 TEXT, status TEXT NOT NULL, feedback_attempts INT DEFAULT 0,
  created_at INT NOT NULL, UNIQUE (uid, client_session_id));
CREATE INDEX sessions_parent ON sessions(parent_id);
CREATE TABLE entitlements (purchase_token TEXT PRIMARY KEY, uid TEXT NOT NULL, product_id TEXT, base_plan_id TEXT, offer_id TEXT,
  state TEXT, expiry_time INT, auto_renewing INT, is_trial INT, verified_at INT);
CREATE INDEX entitlements_uid ON entitlements(uid);
CREATE TABLE reports (id TEXT PRIMARY KEY, uid TEXT, reason TEXT, lang TEXT, prompt_version TEXT, payload_json TEXT, created_at INT, expires_at INT);
CREATE TABLE spend_daily (day TEXT PRIMARY KEY, stt_micro_usd INT DEFAULT 0, llm_micro_usd INT DEFAULT 0, calls INT DEFAULT 0,
  stt_failures INT DEFAULT 0, llm_failures INT DEFAULT 0, grounding_drops INT DEFAULT 0, alert80 INT DEFAULT 0, alert100 INT DEFAULT 0);
CREATE TABLE config (key TEXT PRIMARY KEY, value TEXT NOT NULL);

-- Runtime switches with their defaults (LLD §2.2). Seeded so the runbook's UPDATE statements always hit a row.
INSERT INTO config (key, value) VALUES
  ('kill_switch_analysis', '0'),
  ('daily_cap_usd', '5'),
  ('min_app_version', '1'),
  ('prompt_version', 'fb-2026-11-01a'),
  ('stt_provider_en', 'deepgram'),
  ('stt_provider_hi', 'deepgram'),
  ('low_conf_threshold', '0.6'),
  ('free_talks_per_day', '1'),
  ('free_talk_max_s', '120'),
  ('premium_talks_per_day', '20'),
  ('premium_talk_max_s', '180'),
  ('free_checks_per_day', '10'),
  ('premium_checks_per_day', '200');
