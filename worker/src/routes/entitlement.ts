import { ok, type Ctx } from "../app.ts";
import { deleteEntitlement, upsertEntitlement } from "../db/queries.ts";
import { ApiError } from "../errors.ts";
import { mapSubscription, PlayUnavailableError, PurchaseInvalidError } from "../play/subscriptions.ts";
import { jsonBody, reqStr } from "./validate.ts";

/** POST /entitlement (US-061): verify with subscriptionsv2.get, bind the token to this uid (restore rebinds). */
export async function entitlementRoute(c: Ctx) {
  const deps = c.get("deps");
  const body = await jsonBody(c.req);
  const purchaseToken = reqStr(body, "purchase_token", 4096);
  const productId = reqStr(body, "product_id", 100);
  const now = deps.now();

  let e;
  try {
    e = mapSubscription(await deps.play.getSubscription(purchaseToken), productId, now);
  } catch (err) {
    if (err instanceof PurchaseInvalidError) throw new ApiError("PURCHASE_INVALID");
    if (err instanceof PlayUnavailableError) throw new ApiError("PLAY_UNAVAILABLE");
    throw new ApiError("PLAY_UNAVAILABLE");
  }

  if (e.linkedPurchaseToken && e.linkedPurchaseToken !== purchaseToken) await deleteEntitlement(c.env.DB, e.linkedPurchaseToken);
  await upsertEntitlement(c.env.DB, {
    purchase_token: purchaseToken,
    uid: c.get("uid"),
    product_id: e.productId,
    base_plan_id: e.basePlanId,
    offer_id: e.offerId,
    state: e.state,
    expiry_time: e.expiryTime,
    auto_renewing: e.autoRenewing ? 1 : 0,
    is_trial: e.isTrial ? 1 : 0,
    verified_at: now,
  });
  return ok(c, { premium: e.premium, expiry_time: e.expiryTime, is_trial: e.premium && e.isTrial, auto_renewing: e.autoRenewing });
}
