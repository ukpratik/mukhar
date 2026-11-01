import { describe, expect, it } from "vitest";
import vectors from "../../shared/test-vectors/normalize.json";
import { norm, normTokens } from "../src/grounding/normalize.ts";

describe("norm() shared vectors (same file as the Kotlin NormalizerTest)", () => {
  it.each(vectors.cases.map((c) => [c.name, c.in, c.out] as const))("%s", (_name, input, out) => {
    expect(norm(input)).toBe(out);
  });
});

describe("normTokens", () => {
  it("splits after normalising and drops empties", () => {
    expect(normTokens("  Matlab, हाँ!  ")).toEqual(["matlab", "हां"]);
    expect(normTokens("!!!")).toEqual([]);
  });
});
