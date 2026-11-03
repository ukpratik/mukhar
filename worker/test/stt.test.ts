import { describe, expect, it } from "vitest";
import { deepgramUrl, parseDeepgram } from "../src/stt/deepgram.ts";
import { parseElevenLabs } from "../src/stt/elevenlabs.ts";
import { parseProviderSpec, SttError, transcribe } from "../src/stt/index.ts";
import { parseSarvam } from "../src/stt/sarvam.ts";
import deepgramFixture from "./fixtures/deepgram-en.json";
import elevenFixture from "./fixtures/elevenlabs.json";
import sarvamFixture from "./fixtures/sarvam.json";

const audio = new Uint8Array([0xff, 0xf1, 0x50, 0x80]);
const keys = { deepgram: "dg", sarvam: "sv", elevenlabs: "el" };

function recordingFetch(handler: (req: Request) => Response | Promise<Response>) {
  const reqs: Request[] = [];
  const f: typeof fetch = async (input, init) => {
    const r = new Request(input as RequestInfo, init);
    reqs.push(r.clone());
    return handler(r);
  };
  return { f, reqs };
}

describe("Deepgram adapter", () => {
  it("asks for Nova-3 verbatim with fillers, punctuation and keyterms", () => {
    const u = new URL(deepgramUrl({ lang: "en", keyterms: ["meticulous", "Mukhar"], mimeType: "audio/aac" }));
    expect(u.searchParams.get("model")).toBe("nova-3");
    expect(u.searchParams.get("filler_words")).toBe("true");
    expect(u.searchParams.get("smart_format")).toBe("false");
    expect(u.searchParams.get("language")).toBe("en");
    expect(u.searchParams.getAll("keyterm")).toEqual(["meticulous", "Mukhar"]);
    expect(new URL(deepgramUrl({ lang: "hi", keyterms: [], mimeType: "audio/aac" })).searchParams.get("language")).toBe("multi");
    expect(new URL(deepgramUrl({ lang: "en", keyterms: [], mimeType: "audio/aac", variant: "en-IN" })).searchParams.get("language")).toBe("en-IN");
  });

  it("normalises words to {w,s,e,c} in ms, keeping fillers and punctuation", () => {
    const r = parseDeepgram(deepgramFixture);
    expect(r.words).toHaveLength(19);
    expect(r.words[0]).toEqual({ w: "So", s: 80, e: 320, c: 0.97 });
    expect(r.words[1]!.w).toBe("um");
    expect(r.words.at(-1)).toEqual({ w: "months.", s: 5200, e: 5700, c: 0.55 });
    expect(r.durationMs).toBe(92_400);
    expect(r.hasConfidence).toBe(true);
  });

  it("sends the audio with a Token header via transcribe()", async () => {
    const { f, reqs } = recordingFetch(() => Response.json(deepgramFixture));
    const r = await transcribe(audio, { lang: "en", keyterms: [], mimeType: "audio/aac", providerSpec: "deepgram" }, { fetch: f, keys });
    expect(r.provider).toBe("deepgram");
    expect(r.model).toBe("nova-3");
    expect(reqs[0]!.headers.get("authorization")).toBe("Token dg");
    expect(reqs[0]!.headers.get("content-type")).toBe("audio/aac");
    expect(new Uint8Array(await reqs[0]!.arrayBuffer())).toEqual(audio);
  });

  it("uses the variant from the config spec", async () => {
    const { f, reqs } = recordingFetch(() => Response.json(deepgramFixture));
    await transcribe(audio, { lang: "hi", keyterms: [], mimeType: "audio/aac", providerSpec: "deepgram:hi" }, { fetch: f, keys });
    expect(new URL(reqs[0]!.url).searchParams.get("language")).toBe("hi");
  });

  it("maps non-2xx to an upstream SttError without leaking the body", async () => {
    const { f } = recordingFetch(() => new Response("transcript text here", { status: 500 }));
    const err = await transcribe(audio, { lang: "en", keyterms: [], mimeType: "audio/aac", providerSpec: "deepgram" }, { fetch: f, keys }).catch((e) => e);
    expect(err).toBeInstanceOf(SttError);
    expect(err.kind).toBe("upstream");
    expect(err.upstreamStatus).toBe(500);
    expect(String(err.message)).not.toContain("transcript");
  });

  it("maps a timeout to a timeout SttError", async () => {
    const f: typeof fetch = async () => { throw new DOMException("timed out", "TimeoutError"); };
    const err = await transcribe(audio, { lang: "en", keyterms: [], mimeType: "audio/aac", providerSpec: "deepgram" }, { fetch: f, keys }).catch((e) => e);
    expect(err.kind).toBe("timeout");
  });
});

describe("ElevenLabs adapter", () => {
  it("keeps words only and turns logprob into confidence", () => {
    const r = parseElevenLabs(elevenFixture);
    expect(r.words.map((w) => w.w)).toEqual(["तो", "आज", "मैं"]);
    expect(r.words[0]!.c).toBeCloseTo(Math.exp(-0.05), 5);
    expect(r.words[1]!.c).toBeCloseTo(Math.exp(-1.2), 5);
    expect(r.words[2]).toMatchObject({ s: 1200, e: 1400 });
  });

  it("posts multipart with model, language, word timestamps and keyterms", async () => {
    const { f, reqs } = recordingFetch(() => Response.json(elevenFixture));
    const r = await transcribe(audio, { lang: "hi", keyterms: ["ज़रूरी"], mimeType: "audio/aac", providerSpec: "elevenlabs" }, { fetch: f, keys });
    expect(r.provider).toBe("elevenlabs");
    expect(reqs[0]!.headers.get("xi-api-key")).toBe("el");
    const form = await reqs[0]!.formData();
    expect(form.get("model_id")).toBe("scribe_v2");
    expect(form.get("language_code")).toBe("hi");
    expect(form.get("timestamps_granularity")).toBe("word");
    expect(form.getAll("keyterms")).toEqual(["ज़रूरी"]);
  });
});

describe("Sarvam adapter", () => {
  it("interpolates word times inside segments and reports no confidence", () => {
    const r = parseSarvam(sarvamFixture);
    expect(r.words.map((w) => w.w)).toEqual(["तो", "आज", "मैं", "office", "गई"]);
    expect(r.words[0]).toEqual({ w: "तो", s: 0, e: 400, c: 1 });
    expect(r.words[4]).toEqual({ w: "गई", s: 2000, e: 2500, c: 1 });
    expect(r.hasConfidence).toBe(false);
  });

  it("posts codemix mode with the subscription key", async () => {
    const { f, reqs } = recordingFetch(() => Response.json(sarvamFixture));
    await transcribe(audio, { lang: "hi", keyterms: [], mimeType: "audio/aac", providerSpec: "sarvam" }, { fetch: f, keys });
    expect(reqs[0]!.headers.get("api-subscription-key")).toBe("sv");
    const form = await reqs[0]!.formData();
    expect(form.get("mode")).toBe("codemix");
    expect(form.get("language_code")).toBe("hi-IN");
  });
});

describe("provider selection", () => {
  it("parses name[:variant] and rejects unknown providers", () => {
    expect(parseProviderSpec("deepgram")).toEqual({ provider: "deepgram" });
    expect(parseProviderSpec("deepgram:multi")).toEqual({ provider: "deepgram", variant: "multi" });
    expect(() => parseProviderSpec("whisper")).toThrow(SttError);
  });

  it("fails with a config error when the key is missing", async () => {
    const err = await transcribe(audio, { lang: "en", keyterms: [], mimeType: "audio/aac", providerSpec: "sarvam" }, { fetch: fetch, keys: {} }).catch((e) => e);
    expect(err.kind).toBe("config");
  });
});
