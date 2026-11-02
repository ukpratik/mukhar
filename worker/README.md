# Vaak Worker (API v1)

Cloudflare Worker (TypeScript + Hono) behind the Vaak Android app: Firebase + Play Integrity auth, quotas, the spend cap,
STT relay (Deepgram / Sarvam / ElevenLabs behind one `transcribe()`), grounded feedback from `claude-haiku-5-5`,
Play subscription verification, consented reports and data deletion. Design: `product/05-architecture/LLD.md` (§2.2, §3,
§4.3, §4.4, §4.7, §10), HLD, ADR-002 to ADR-006.

## Layout
```
src/index.ts            Hono app (createApp, injectable deps) + cron purge
src/app.ts, middleware.ts, deps.ts, env.ts, errors.ts, log.ts
src/auth/               firebase.ts (ID token, jose + Google JWKS), session.ts (HS256 24 h token, device-hash salting), integrity.ts
src/routes/             config, session, me (GET/DELETE), transcribe, feedback, checkSentence, entitlement, report
src/stt/                index.ts transcribe(), deepgram.ts, sarvam.ts, elevenlabs.ts → words[{w,s,e,c}]
src/llm/                prompt.ts (system prompt + few-shots, PROMPT_VERSION), schema.ts, client.ts (@anthropic-ai/sdk)
src/grounding/          normalize.ts (shared norm()), validate.ts (LLD §4.4), fallback.ts, safety.ts
src/quota/              quotaDay.ts (03:00 IST), decide.ts (pure, mirrors Kotlin QuotaMirror)
src/spend/ledger.ts     cost estimates, $5/day cap → 503 BUSY, 80 % / 100 % webhook alerts
src/play/               googleAuth.ts (service-account JWT), subscriptions.ts (subscriptionsv2, GA4 user deletion)
src/db/queries.ts       all D1 SQL
migrations/0001_init.sql, 0002_baseline.sql
test/                   Vitest in workerd (@cloudflare/vitest-pool-workers) with a local D1; consumes ../shared/test-vectors
scripts/                stt-bench.ts, feedback-eval.ts (+ lib/ with pure, unit-tested metrics)
data/golden/            benchmark manifest format and recording/consent guide (audio never committed)
data/eval/              feedback eval set (starter items)
reports/                benchmark and eval markdown output
```

## Run and test
Node 22.18+ (Node 26 runs the `.ts` scripts directly).
```sh
cd worker
npm ci
npm run typecheck          # tsc for the Worker + tests, and for the scripts
npm test                   # Vitest inside workerd; no network: STT, LLM and Google APIs are faked
cp .dev.vars.example .dev.vars   # fill in your own values (git-ignored)
npm run db:migrate:local
npm run dev                # wrangler dev --env dev on http://localhost:8787/v1/config
```

## Deploy (nothing has been deployed or created yet)
```sh
npx wrangler login
npx wrangler d1 create vaak-dev      # paste the database_id into [env.dev] in wrangler.toml
npx wrangler d1 create vaak-prod     # paste into [env.production]; also set FIREBASE_PROJECT_ID there
npm run db:migrate:dev               # wrangler d1 migrations apply DB --remote --env dev
for s in ANTHROPIC_API_KEY DEEPGRAM_API_KEY SARVAM_API_KEY ELEVENLABS_API_KEY GOOGLE_SA_JSON SESSION_HMAC_KEY DEVICE_SALT ALERT_WEBHOOK_URL; do
  npx wrangler secret put $s --env dev
done
npx wrangler secret put DEBUG_ALLOWLIST_UIDS --env dev   # optional: debug-build UIDs that skip Integrity (ignored in production)
npm run deploy:dev
# production: same with --env production (npm run db:migrate:prod, then npm run deploy:prod), from a clean tagged commit (worker-v<semver>)
```
- `SESSION_HMAC_KEY` and `DEVICE_SALT`: 32+ random characters each (`openssl rand -base64 48`). Never rotate `DEVICE_SALT`
  casually: it resets the per-device free quota.
- `GOOGLE_SA_JSON`: one service account with Play Developer API access (Play Console → API access, "View financial data"
  and "Manage orders and subscriptions"), the Play Integrity API enabled in the linked Cloud project, and, if you set
  `GA4_PROPERTY_ID`, the Analytics user-deletion permission.
- `ALERT_WEBHOOK_URL`: ntfy.sh topic, Discord or Slack webhook (the body carries `content` and `text`).
- Set the Anthropic workspace spend limit and Deepgram balance alerts as the outer ceiling (D-025).

## Runbook
```sh
npx wrangler d1 execute DB --remote --env production --command "UPDATE config SET value='1' WHERE key='kill_switch_analysis'"   # pause analysis
npx wrangler d1 execute DB --remote --env production --command "UPDATE config SET value='deepgram:multi' WHERE key='stt_provider_hi'"
npx wrangler d1 execute DB --remote --env production --command "SELECT * FROM spend_daily ORDER BY day DESC LIMIT 7"
```
Provider specs are `name` or `name:variant` (Deepgram language, Sarvam mode, ElevenLabs language code).

## Contract notes for the app (beyond LLD §3)
- **Integrity request hash:** lowercase hex SHA-256 of the UTF-8 string `"<uid>|<device_hash>|<utc_minute>"`, where
  `device_hash` is exactly the string sent to `/session` and `utc_minute = floor(epochMillis / 60000)` (± 10 min).
- **Device hash:** the app sends a base64 SHA-256 of ANDROID_ID; the Worker salts it again (HMAC with `DEVICE_SALT`).
- **Feedback binding:** `/feedback` tokens must be the `/transcribe` words unchanged (`w` strings, same order); the Worker
  compares SHA-256 of the JSON array of `w` strings. `lc` marks tokens below `low_conf_threshold`.
- **Fallback items** in `/feedback` have `start`, `end`, `quote` = `null` and `fallback: true`.
- **Errors** use the LLD envelope; extra codes: `NOT_FOUND` 404, `INTERNAL` 500, `STT_TIMEOUT` 504, `UNSUPPORTED_MEDIA`
  415, `BASELINE_USED` 409, `PLAY_UNAVAILABLE` 502 (also from `/session` when Google can't decode the token). `Retry-After` is set when known.
- `kind` on `/transcribe` is `talk`, `retake` or `baseline`; a re-take needs `parent_session_id` = the parent's `session_id`.
- **Baseline (D-031):** free (no quota debit) once per uid AND once per device hash (`baseline_used` table, migration
  0002); a second one is 409 `BASELINE_USED`. `/feedback` accepts baseline sessions. Read-aloud never calls the API.
  `DELETE /me` removes the uid marker but keeps the device marker, so a reinstall can't get a second free baseline.

## Gate-zero benchmark and feedback eval
See `data/golden/README.md`. Summary: record and consent clips, write `data/golden/manifest.json`, then
`DEEPGRAM_API_KEY=… SARVAM_API_KEY=… ELEVENLABS_API_KEY=… node scripts/stt-bench.ts` → `reports/stt-benchmark-<date>.md`.
Feedback: `ANTHROPIC_API_KEY=… node scripts/feedback-eval.ts [--thinking disabled]` → `reports/feedback-eval-<date>-<prompt_version>.md`
(set: `data/eval/eval-set.json`, 50 synthetic labelled items, M6; exits 1 on an NFR-004 breach or a regression).
`node scripts/feedback-eval.ts --dry-run` validates the set offline (shape, timings, confidences, label spans, safety
labels vs the keyword backstop, coverage) with no key and no API call; `npm test` runs the same check.

## Known limitations
- Per-uid rate limit (30/min) is per isolate, best effort; quotas and the spend cap are the hard guards.
- `/check-sentence` matches the target word by Levenshtein only; the HI transliteration table is not built yet.
- Sarvam gives segment-level timestamps and no word confidence (interpolated, `c = 1`); its sync endpoint is for short
  audio, so long clips may fail until the batch API is wired (only if Sarvam wins gate zero).
- `@cloudflare/vitest-pool-workers` is deprecated in favour of `@cloudflare/vitest-plugin` (works; migrate later).
  `npm audit` reports advisories in dev-only transitive deps of wrangler/miniflare (undici, sharp); none ship in the Worker.
