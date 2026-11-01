/**
 * Gate-zero STT benchmark (LLD §10, ADR-002, NFR-003).
 *
 *   DEEPGRAM_API_KEY=… SARVAM_API_KEY=… ELEVENLABS_API_KEY=… node scripts/stt-bench.ts [options]
 *
 * Options:
 *   --manifest data/golden/manifest.json   clips + verbatim references (see data/golden/README.md)
 *   --configs deepgram:multi,deepgram:en,deepgram:hi,sarvam,elevenlabs   provider specs (same syntax as the D1 config row)
 *   --sets en,hi,hinglish                  restrict to these sets
 *   --limit N                              first N clips per set (smoke run)
 *   --dry-run                              validate the manifest and audio files, call nothing
 *
 * Runs every clip through the real Worker `transcribe()` adapters, normalises with the shared `norm()`, and writes
 *   reports/stt-benchmark-<date>.md   (summary, safe to keep)
 *   reports/stt-benchmark-<date>.json (per clip incl. transcripts: git-ignored)
 * Costs real money (≈ $0.006/audio minute per provider config) and needs consented audio that is never committed.
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { sttCostMicroUsd } from "../src/spend/ledger.ts";
import { parseProviderSpec, transcribe, type TranscribeResult } from "../src/stt/index.ts";
import {
  falseCorrections, fillerScore, fmtPct, PASS_RULE, scriptAgnostic, summarize, timestampsPlausible, unambiguousSet, wer, werTokens,
  type ClipResult, type FillerLists, type GroupSummary,
} from "./lib/bench.ts";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
type SetName = "en" | "hi" | "hinglish";

interface Clip {
  id: string;
  audio: string;
  lang: "en" | "hi";
  set: SetName;
  reference: string;
  speaker?: string;
  noisy?: boolean;
  mime?: string;
  keyterms?: string[];
  verbatim_checks?: string[];
}

const MIME: Record<string, string> = { ".aac": "audio/aac", ".m4a": "audio/mp4", ".mp4": "audio/mp4", ".wav": "audio/wav", ".mp3": "audio/mpeg", ".ogg": "audio/ogg", ".webm": "audio/webm" };

function arg(name: string): string | undefined {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

function validateManifest(m: unknown): Clip[] {
  const clips = (m as { clips?: unknown[] }).clips;
  if (!Array.isArray(clips) || clips.length === 0) throw new Error("manifest.clips must be a non-empty array");
  const ids = new Set<string>();
  return clips.map((c, i) => {
    const x = c as Partial<Clip>;
    const where = `clips[${i}]`;
    if (!x.id || ids.has(x.id)) throw new Error(`${where}: missing or duplicate id`);
    ids.add(x.id);
    if (!x.audio) throw new Error(`${where}: audio path missing`);
    if (x.lang !== "en" && x.lang !== "hi") throw new Error(`${where}: lang must be en or hi`);
    if (x.set !== "en" && x.set !== "hi" && x.set !== "hinglish") throw new Error(`${where}: set must be en, hi or hinglish`);
    if (!x.reference || typeof x.reference !== "string") throw new Error(`${where}: reference transcript missing`);
    return x as Clip;
  });
}

const defaultConfigs = (set: SetName) => (set === "en" ? ["deepgram:en", "deepgram:multi", "sarvam", "elevenlabs"] : ["deepgram:hi", "deepgram:multi", "sarvam", "elevenlabs"]);

async function main() {
  const manifestPath = resolve(ROOT, arg("manifest") ?? "data/golden/manifest.json");
  const manifestDir = dirname(manifestPath);
  const clipsAll = validateManifest(JSON.parse(await readFile(manifestPath, "utf8")));
  const sets = (arg("sets")?.split(",") ?? ["en", "hi", "hinglish"]) as SetName[];
  const limit = Number(arg("limit") ?? Infinity);
  const configsArg = arg("configs")?.split(",");
  const dryRun = process.argv.includes("--dry-run");
  const fillers = JSON.parse(await readFile(resolve(ROOT, "../shared/test-vectors/fillers.json"), "utf8")).lists as FillerLists;
  const keys = { deepgram: process.env.DEEPGRAM_API_KEY, sarvam: process.env.SARVAM_API_KEY, elevenlabs: process.env.ELEVENLABS_API_KEY };

  const clips = sets.flatMap((s) => clipsAll.filter((c) => c.set === s).slice(0, limit));
  for (const c of clips) {
    try { await readFile(resolve(manifestDir, c.audio)); } catch {
      throw new Error(`audio missing for clip ${c.id}: ${c.audio} (relative to ${manifestDir}; see data/golden/README.md)`);
    }
  }
  console.log(`${clips.length} clips across ${sets.join(", ")}`);
  if (dryRun) { console.log("dry run: manifest and audio files OK"); return; }

  const perClip: Array<{ clip: string; set: SetName; config: string; result: ClipResult; hypothesis?: string; error?: string }> = [];
  const skipped = new Set<string>();

  for (const clip of clips) {
    const audio = await readFile(resolve(manifestDir, clip.audio));
    const mime = clip.mime ?? MIME[extname(clip.audio).toLowerCase()] ?? "audio/aac";
    const ref = werTokens(clip.reference);
    const allowed = unambiguousSet(fillers, clip.set);
    for (const spec of configsArg ?? defaultConfigs(clip.set)) {
      const { provider } = parseProviderSpec(spec);
      if (!keys[provider]) { skipped.add(`${spec} (no ${provider.toUpperCase()}_API_KEY)`); continue; }
      const started = Date.now();
      let r: TranscribeResult;
      try {
        r = await transcribe(audio, { lang: clip.lang, keyterms: clip.keyterms ?? [], mimeType: mime, providerSpec: spec, timeoutMs: 120_000 }, { fetch, keys });
      } catch (e) {
        perClip.push({ clip: clip.id, set: clip.set, config: spec, result: { ok: false, ms: Date.now() - started }, error: (e as Error).message });
        console.log(`  ${clip.id} ${spec}: ERROR ${(e as Error).message}`);
        continue;
      }
      const ms = Date.now() - started;
      const hyp = werTokens(r.words.map((w) => w.w).join(" "));
      const result: ClipResult = {
        ok: true,
        ms,
        werStrict: wer(ref, hyp),
        werAgnostic: wer(scriptAgnostic(ref), scriptAgnostic(hyp)),
        fillers: fillerScore(ref, hyp, allowed),
        falseCorrections: falseCorrections(hyp, clip.verbatim_checks ?? []),
        timestampsOk: timestampsPlausible(r.words, r.durationMs),
        hasConfidence: r.hasConfidence,
        costMicroUsd: sttCostMicroUsd(provider, r.durationMs),
      };
      perClip.push({ clip: clip.id, set: clip.set, config: spec, result, hypothesis: r.words.map((w) => w.w).join(" ") });
      console.log(`  ${clip.id} ${spec}: WER ${fmtPct(result.werStrict!.wer)}, ${ms} ms`);
    }
  }

  // Summaries per set × config
  const groups = new Map<string, { set: SetName; config: string; s: GroupSummary }>();
  for (const set of sets) {
    const configs = [...new Set(perClip.filter((p) => p.set === set).map((p) => p.config))];
    for (const config of configs) {
      groups.set(`${set}|${config}`, { set, config, s: summarize(perClip.filter((p) => p.set === set && p.config === config).map((p) => p.result)) });
    }
  }

  const date = new Date().toISOString().slice(0, 10);
  const lines: string[] = [];
  lines.push(`# STT benchmark (gate zero), ${date}`, "");
  lines.push(`Manifest: \`${manifestPath.replace(ROOT + "/", "")}\`, ${clips.length} clips. Pass rule per language (LLD §10): WER ≤ ${PASS_RULE.maxWer * 100}%, unambiguous filler recall ≥ ${PASS_RULE.minFillerRecall * 100}%, word timestamps + confidence, STT p90 ≤ ${PASS_RULE.maxP90Ms / 1000} s.`, "");
  lines.push("WER is corpus-level after `norm()` (NFC, case, punctuation, nukta, ZWJ/ZWNJ, candrabindu) plus Devanagari digits. The script-agnostic WER (consonant skeleton, so Hinglish written in either script can meet) is indicative only. Latency is measured from this machine, not from an Indian 4G phone.", "");
  lines.push("| Set | Config | Clips | WER | WER (script-agnostic) | Filler recall | Filler precision | False corrections | Timestamps | Confidence | p50 | p90 | Cost | Result |");
  lines.push("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|");
  for (const { set, config, s } of groups.values()) {
    lines.push(`| ${set} | ${config} | ${s.clips} | ${fmtPct(s.wer)} | ${fmtPct(s.werScriptAgnostic)} | ${fmtPct(s.fillerRecall)} | ${fmtPct(s.fillerPrecision)} | ${s.falseCorrections} | ${s.timestampsOk ? "yes" : "no"} | ${s.confidenceOk ? "yes" : "no"} | ${s.p50Ms ?? "–"} ms | ${s.p90Ms ?? "–"} ms | $${s.costUsd.toFixed(3)} | ${s.pass ? "**PASS**" : `fail: ${s.failReasons.join("; ")}`} |`);
  }
  lines.push("", "## Pick per language", "");
  for (const lang of ["en", "hi"] as const) {
    const langSets: SetName[] = lang === "en" ? ["en"] : ["hi", "hinglish"];
    const configs = [...new Set([...groups.values()].filter((g) => langSets.includes(g.set)).map((g) => g.config))];
    const passers = configs.filter((cfg) => langSets.every((st) => !sets.includes(st) || groups.get(`${st}|${cfg}`)?.s.pass));
    const werOf = (cfg: string) => {
      const gs = langSets.map((st) => groups.get(`${st}|${cfg}`)?.s.wer ?? 1);
      return gs.reduce((a, b) => a + b, 0) / gs.length;
    };
    passers.sort((a, b) => werOf(a) - werOf(b));
    lines.push(passers.length
      ? `- **${lang}**: \`${passers[0]}\` (lowest WER among passers: ${passers.map((p) => `${p} ${fmtPct(werOf(p))}`).join(", ")}). Tie-break on retention terms, then cost (manual).`
      : `- **${lang}**: no config passed. ${lang === "hi" ? "D-010 applies: Hindi ships as beta with on-device metrics." : "Stop and escalate to the user (Class A)."}`);
  }
  if (skipped.size) lines.push("", `Skipped: ${[...skipped].join(", ")}.`);
  const failures = perClip.filter((p) => p.error);
  if (failures.length) lines.push("", "## Failed calls", "", ...failures.map((p) => `- ${p.clip} / ${p.config}: ${p.error}`));
  lines.push("", "Manual checks still needed (LLD §10): spot-check timestamps and confidence on 5 clips, Hinglish script handling, ADTS vs M4A acceptance, cost per minute and retention/training opt-out terms per provider (Q-010).");

  await mkdir(resolve(ROOT, "reports"), { recursive: true });
  const md = resolve(ROOT, `reports/stt-benchmark-${date}.md`);
  await writeFile(md, lines.join("\n") + "\n");
  await writeFile(resolve(ROOT, `reports/stt-benchmark-${date}.json`), JSON.stringify({ date, perClip, groups: [...groups.values()] }, null, 2));
  console.log(`\nWrote ${md} (and a git-ignored .json with per-clip transcripts)`);
}

main().catch((e) => {
  console.error((e as Error).message);
  process.exit(1);
});
