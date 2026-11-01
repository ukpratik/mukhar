import type { GoogleAuth } from "./googleAuth.ts";

/**
 * Play Developer API purchases.subscriptionsv2.get (US-061, HLD §5.3). No RTDN in R1: the Worker re-verifies lazily
 * when the cached expiry has passed and when the app calls /entitlement.
 */
export const ANDROIDPUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

export interface PlaySubscription {
  subscriptionState?: string;
  linkedPurchaseToken?: string;
  testPurchase?: unknown;
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
    offerDetails?: { basePlanId?: string; offerId?: string };
    offerPhase?: { freeTrial?: unknown; introductoryPrice?: unknown; basePrice?: unknown; prorationPeriod?: unknown };
  }>;
}

export interface Entitlement {
  premium: boolean;
  state: string;
  productId: string | null;
  basePlanId: string | null;
  offerId: string | null;
  expiryTime: number | null;
  autoRenewing: boolean;
  isTrial: boolean;
  linkedPurchaseToken: string | null;
}

export class PurchaseInvalidError extends Error {}
export class PlayUnavailableError extends Error {}

export interface PlayClient {
  getSubscription(purchaseToken: string): Promise<PlaySubscription>;
}

const ENTITLED_STATES = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);

/** Maps a subscriptionsv2 resource to our entitlement. A cancelled subscription stays Premium until its expiry. */
export function mapSubscription(sub: PlaySubscription, expectedProductId: string | null, nowMs: number): Entitlement {
  const items = (sub.lineItems ?? []).filter((li) => !expectedProductId || li.productId === expectedProductId);
  if (items.length === 0) throw new PurchaseInvalidError("no line item for the product");
  const li = items.reduce((a, b) => (Date.parse(b.expiryTime ?? "") > Date.parse(a.expiryTime ?? "") ? b : a));
  const expiry = li.expiryTime ? Date.parse(li.expiryTime) : NaN;
  const expiryTime = Number.isFinite(expiry) ? expiry : null;
  const state = sub.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED";
  const premium = ENTITLED_STATES.has(state) && expiryTime !== null && expiryTime > nowMs;
  return {
    premium,
    state,
    productId: li.productId ?? null,
    basePlanId: li.offerDetails?.basePlanId ?? null,
    offerId: li.offerDetails?.offerId ?? null,
    expiryTime,
    autoRenewing: li.autoRenewingPlan?.autoRenewEnabled === true,
    isTrial: li.offerPhase?.freeTrial !== undefined,
    linkedPurchaseToken: sub.linkedPurchaseToken ?? null,
  };
}

export function googlePlayClient(packageName: string, auth: GoogleAuth, fetchFn: typeof fetch): PlayClient {
  return {
    async getSubscription(purchaseToken: string) {
      let token: string;
      try { token = await auth.accessToken(ANDROIDPUBLISHER_SCOPE); } catch { throw new PlayUnavailableError("google auth failed"); }
      const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
      let res: Response;
      try {
        res = await fetchFn(url, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(8000) });
      } catch {
        throw new PlayUnavailableError("play request failed");
      }
      if (res.status === 400 || res.status === 404 || res.status === 410) {
        await res.body?.cancel();
        throw new PurchaseInvalidError(`play returned ${res.status}`);
      }
      if (!res.ok) {
        await res.body?.cancel();
        throw new PlayUnavailableError(`play returned ${res.status}`);
      }
      return (await res.json()) as PlaySubscription;
    },
  };
}

export const ANALYTICS_DELETION_SCOPE = "https://www.googleapis.com/auth/analytics.user.deletion";

/** GA4 User Deletion API for the app instance (US-063). Best effort; skipped if no property is configured. */
export async function deleteGa4AppInstance(propertyId: string | undefined, appInstanceId: string, auth: GoogleAuth, fetchFn: typeof fetch): Promise<boolean> {
  if (!propertyId || !appInstanceId) return false;
  const token = await auth.accessToken(ANALYTICS_DELETION_SCOPE);
  const res = await fetchFn(`https://analyticsadmin.googleapis.com/v1alpha/properties/${encodeURIComponent(propertyId)}:submitUserDeletion`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify({ appInstanceId }),
    signal: AbortSignal.timeout(8000),
  });
  await res.body?.cancel();
  return res.ok;
}
