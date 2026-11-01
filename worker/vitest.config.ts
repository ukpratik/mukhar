import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

/**
 * Tests run inside workerd via @cloudflare/vitest-pool-workers with a local D1 (Miniflare). Secrets below are
 * test-only dummies; every outbound call goes through injected fakes (no real network).
 */
export default defineConfig(async () => {
  const migrations = await readD1Migrations("./migrations");
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migrations,
            SESSION_HMAC_KEY: "test-session-hmac-key-0123456789abcdef",
            DEVICE_SALT: "test-device-salt",
            DEEPGRAM_API_KEY: "test-deepgram",
            SARVAM_API_KEY: "test-sarvam",
            ELEVENLABS_API_KEY: "test-elevenlabs",
            ANTHROPIC_API_KEY: "test-anthropic",
            ALERT_WEBHOOK_URL: "https://alerts.test/hook",
          },
        },
      }),
    ],
    test: {
      setupFiles: ["./test/setup.ts"],
    },
  };
});
