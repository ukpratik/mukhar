import { describe, expect, it } from "vitest";
import { googlePlayClient, mapSubscription, PlayUnavailableError, PurchaseInvalidError } from "../src/play/subscriptions.ts";
import { T0 } from "./helpers.ts";

const iso = (ms: number) => new Date(ms).toISOString();
const trial = {
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  lineItems: [{ productId: "premium", expiryTime: iso(T0 + 7 * 86_400_000), autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: "monthly", offerId: "trial7" }, offerPhase: { freeTrial: {} } }],
};

describe("subscriptionsv2 mapping", () => {
  it("active trial → premium, trial, auto-renewing", () => {
    expect(mapSubscription(trial, "premium", T0)).toMatchObject({ premium: true, isTrial: true, autoRenewing: true, expiryTime: T0 + 7 * 86_400_000, basePlanId: "monthly", offerId: "trial7" });
  });

  it("cancelled but unexpired stays premium without auto-renew", () => {
    const sub = { ...trial, subscriptionState: "SUBSCRIPTION_STATE_CANCELED", lineItems: [{ ...trial.lineItems[0]!, autoRenewingPlan: { autoRenewEnabled: false }, offerPhase: { basePrice: {} } }] };
    expect(mapSubscription(sub, "premium", T0)).toMatchObject({ premium: true, autoRenewing: false, isTrial: false });
  });

  it.each(["SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_PENDING"])("%s → not premium", (state) => {
    expect(mapSubscription({ ...trial, subscriptionState: state }, "premium", T0).premium).toBe(false);
  });

  it("expired line item → not premium even if ACTIVE", () => {
    expect(mapSubscription({ ...trial, lineItems: [{ ...trial.lineItems[0]!, expiryTime: iso(T0 - 1) }] }, "premium", T0).premium).toBe(false);
  });

  it("wrong product → invalid", () => {
    expect(() => mapSubscription(trial, "other", T0)).toThrow(PurchaseInvalidError);
  });
});

describe("Play client HTTP mapping", () => {
  const auth = { accessToken: async () => "tok" };
  const client = (status: number, body: unknown = {}) => googlePlayClient("io.github.ukpratik.mukhar", auth, async () => Response.json(body, { status }));

  it("calls subscriptionsv2 with a bearer token", async () => {
    let url = "";
    let authz = "";
    const c = googlePlayClient("io.github.ukpratik.mukhar", auth, async (input, init) => {
      const r = new Request(input as RequestInfo, init);
      url = r.url;
      authz = r.headers.get("authorization") ?? "";
      return Response.json(trial);
    });
    expect(await c.getSubscription("tok/en")).toEqual(trial);
    expect(url).toBe("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/io.github.ukpratik.mukhar/purchases/subscriptionsv2/tokens/tok%2Fen");
    expect(authz).toBe("Bearer tok");
  });

  it.each([400, 404, 410])("%i → PurchaseInvalid", async (s) => {
    await expect(client(s).getSubscription("t")).rejects.toBeInstanceOf(PurchaseInvalidError);
  });

  it.each([401, 500, 503])("%i → PlayUnavailable", async (s) => {
    await expect(client(s).getSubscription("t")).rejects.toBeInstanceOf(PlayUnavailableError);
  });
});
