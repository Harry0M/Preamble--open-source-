/**
 * v2Config — GET endpoint returning the model registry for client display.
 *
 * Returns an array of enabled models with: modelId, displayName, provider, supportsReasoning.
 * Strips cost rate from the client response (cost info is server-only).
 * Requires Firebase Auth bearer token.
 *
 * Requirements: 15.3
 */

import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getEnabledModels } from "../services/model-router";
import { enforceRateLimit } from "../utils/rate-limiter";

/**
 * Client-safe model info — excludes cost rate and internal fields.
 */
interface ClientModelInfo {
  modelId: string;
  displayName: string;
  provider: string;
  supportsReasoning: boolean;
  isDefault: boolean;
}

/**
 * Verifies the Firebase Auth bearer token and returns the user's UID.
 * Returns null if the token is missing, malformed, or expired.
 */
async function verifyAuth(
  authHeader: string | undefined
): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

/**
 * GET /v2Config
 *
 * Returns the list of enabled AI models for client display.
 * Each entry contains:
 * - modelId: the model identifier
 * - displayName: human-readable model name
 * - provider: model provider (google, anthropic, openai, mistral)
 * - supportsReasoning: whether the model supports reasoning/thinking display
 * - isDefault: whether this is the default model
 *
 * Cost rates are NOT exposed to the client.
 */
export const v2Config = onRequest(
  { cors: true, timeoutSeconds: 10 },
  async (req, res) => {
    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    // Rate limiting (max 10 req/min per user, Req 26.3)
    if (await enforceRateLimit(uid, res)) return;

    try {
      const enabledModels = await getEnabledModels();

      const clientModels: ClientModelInfo[] = enabledModels.map((model) => ({
        modelId: model.modelId,
        displayName: model.displayName,
        provider: model.provider,
        supportsReasoning: model.supportsReasoning,
        isDefault: model.isDefault,
      }));

      res.json({ models: clientModels });
    } catch (err) {
      if (
        err instanceof Error &&
        "statusCode" in err &&
        (err as { statusCode: number }).statusCode === 503
      ) {
        res.status(503).json({ error: "No AI models are currently available" });
        return;
      }
      const message =
        err instanceof Error ? err.message : "Internal server error";
      res.status(500).json({ error: message });
    }
  }
);
