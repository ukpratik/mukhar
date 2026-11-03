import { finishLog, handleError, newApp, requestIdFrom } from "./app.ts";
import { purge } from "./db/queries.ts";
import { defaultDeps, type Deps } from "./deps.ts";
import type { Env } from "./env.ts";
import { ApiError } from "./errors.ts";
import { writeEvent } from "./log.ts";
import { appVersionGate, rateLimit, requireFirebase, requireSession } from "./middleware.ts";
import { addDays, quotaDay } from "./quota/quotaDay.ts";
import { checkSentenceRoute } from "./routes/checkSentence.ts";
import { configRoute } from "./routes/config.ts";
import { entitlementRoute } from "./routes/entitlement.ts";
import { feedbackRoute } from "./routes/feedback.ts";
import { deleteMeRoute, getMeRoute } from "./routes/me.ts";
import { reportRoute } from "./routes/report.ts";
import { sessionRoute } from "./routes/session.ts";
import { transcribeRoute } from "./routes/transcribe.ts";

/** Mukhar Worker API v1 (LLD §3). `makeDeps` is injectable so tests run every route with fakes and a real D1. */
export function createApp(makeDeps: (env: Env) => Deps = defaultDeps) {
  const app = newApp();

  app.use("*", async (c, next) => {
    const deps = makeDeps(c.env);
    c.set("deps", deps);
    c.set("requestId", requestIdFrom(c.req.header("X-Request-Id"), deps));
    c.set("log", {});
    const started = deps.now();
    try {
      await next();
    } finally {
      finishLog(c, started, `${c.req.method} ${c.req.path}`);
    }
  });

  // Auth chains: /config public; /session and DELETE /me need only the Firebase ID token; everything else also needs
  // the Integrity-backed session token.
  const full = [appVersionGate, requireFirebase, requireSession, rateLimit] as const;
  for (const path of ["/v1/transcribe", "/v1/feedback", "/v1/check-sentence", "/v1/entitlement", "/v1/report"]) {
    for (const m of full) app.on("POST", path, m);
  }
  for (const m of full) app.on("GET", "/v1/me", m);
  app.on("POST", "/v1/session", appVersionGate, requireFirebase, rateLimit);
  app.on("DELETE", "/v1/me", requireFirebase);

  app.get("/v1/config", configRoute);
  app.post("/v1/session", sessionRoute);
  app.get("/v1/me", getMeRoute);
  app.delete("/v1/me", deleteMeRoute);
  app.post("/v1/transcribe", transcribeRoute);
  app.post("/v1/feedback", feedbackRoute);
  app.post("/v1/check-sentence", checkSentenceRoute);
  app.post("/v1/entitlement", entitlementRoute);
  app.post("/v1/report", reportRoute);

  app.notFound((c) => handleError(new ApiError("NOT_FOUND"), c));
  app.onError(handleError);
  return app;
}

/** Daily purge (cron 22:00 UTC, LLD §2.2). */
export async function runPurge(env: Env, nowMs: number): Promise<void> {
  const today = quotaDay(nowMs);
  await purge(env.DB, nowMs, addDays(today, -35), addDays(today, -400));
  writeEvent("purge_done");
}

const app = createApp();

export default {
  fetch: app.fetch,
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil(runPurge(env, Date.now()));
  },
} satisfies ExportedHandler<Env>;
