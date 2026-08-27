import { onRequest } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

/**
 * Secret authorization header sent by RevenueCat webhooks.
 * Configure in RevenueCat Dashboard -> Project -> Integrations -> Webhooks -> Authorization header: "Bearer <REVENUECAT_WEBHOOK_SECRET>"
 */
const REVENUECAT_WEBHOOK_SECRET = process.env.REVENUECAT_WEBHOOK_SECRET || "preamble_rc_secret_key";

export const revenueCatWebhook = onRequest(
  {
    cors: false,
    timeoutSeconds: 30,
    memory: "256MiB",
    maxInstances: 20,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    // 1. Verify Authorization header if configured
    const authHeader = req.headers.authorization;
    if (
      REVENUECAT_WEBHOOK_SECRET &&
      authHeader &&
      authHeader !== `Bearer ${REVENUECAT_WEBHOOK_SECRET}` &&
      authHeader !== REVENUECAT_WEBHOOK_SECRET
    ) {
      res.status(401).json({ error: "Unauthorized webhook" });
      return;
    }

    try {
      const body = req.body || {};
      const event = body.event || body;
      const type: string = event.type || "";
      const appUserId: string = event.app_user_id || "";
      const productId: string = event.product_id || "";
      const expirationAtMs: number = Number(event.expiration_at_ms || event.expires_date_ms || 0);

      if (!appUserId) {
        res.status(200).json({ status: "ignored_no_user_id" });
        return;
      }

      // Ignore anonymous non-firebase UIDs if any (must be valid uid)
      if (appUserId.startsWith("$RCAnonymousID")) {
        res.status(200).json({ status: "ignored_anonymous_user" });
        return;
      }

      const db = getFirestore("preamble");
      const userRef = db.doc(`users/${appUserId}`);

      let isPremium = false;
      let entitlementTier = "FREE";
      let expiresAt = 0;

      switch (type) {
        case "INITIAL_PURCHASE":
        case "RENEWAL":
        case "UNCANCELLATION":
        case "PRODUCT_CHANGE":
        case "NON_RENEWING_PURCHASE": // Lifetime purchase
          isPremium = true;
          entitlementTier = "PRO";
          expiresAt =
            expirationAtMs > 0
              ? expirationAtMs
              : type === "NON_RENEWING_PURCHASE"
              ? 253402300799000 // Far future for lifetime (year 9999)
              : Date.now() + 32 * 86400000;
          break;

        case "CANCELLATION":
          // User cancelled auto-renew, but still retains access until period expires
          isPremium = expirationAtMs > Date.now();
          entitlementTier = isPremium ? "PRO" : "FREE";
          expiresAt = expirationAtMs;
          break;

        case "EXPIRATION":
        case "BILLING_ISSUE":
          isPremium = false;
          entitlementTier = "FREE";
          expiresAt = 0;
          break;

        default:
          res.status(200).json({ status: "ignored_event_type", type });
          return;
      }

      await userRef.set(
        {
          is_premium: isPremium,
          entitlement_tier: entitlementTier,
          expires_at: expiresAt,
          plan_id: productId || null,
          revenuecat_last_event: type,
          revenuecat_updated_at: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      console.log(
        `[RevenueCat] User ${appUserId} updated: is_premium=${isPremium}, tier=${entitlementTier}, event=${type}`
      );
      res.status(200).json({ success: true, user: appUserId, is_premium: isPremium });
    } catch (err: any) {
      console.error("[RevenueCat] Webhook processing failed:", err);
      res.status(500).json({ error: err.message || "Internal error" });
    }
  }
);
