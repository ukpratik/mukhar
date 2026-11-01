import { ok, type Ctx } from "../app.ts";
import { PROMPT_VERSION } from "../llm/prompt.ts";
import { cfgOf } from "./common.ts";

/** GET /config (public, cached 5 min). prompt_version comes from code: it names the prompt text actually deployed. */
export async function configRoute(c: Ctx) {
  const cfg = await cfgOf(c);
  c.header("Cache-Control", "public, max-age=300");
  return ok(c, {
    min_app_version: cfg.minAppVersion,
    kill_switch: cfg.killSwitch,
    prompt_version: PROMPT_VERSION,
    low_conf_threshold: cfg.lowConfThreshold,
    limits: { free_talk_max_s: cfg.limits.free_talk_max_s, premium_talk_max_s: cfg.limits.premium_talk_max_s },
  });
}
