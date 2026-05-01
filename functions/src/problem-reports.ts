import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const MAX_ATTACHMENT_BYTES = 50 * 1024 * 1024;
const ACTIVE_STATUSES = new Set(["open", "in_progress"]);

async function verifyAuth(authHeader: string | undefined) {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    return await getAuth().verifyIdToken(authHeader.slice(7));
  } catch {
    return null;
  }
}

function isValidReportId(id: unknown): id is string {
  return typeof id === "string" && /^[A-Za-z0-9_-]{8,80}$/.test(id);
}

function cleanString(value: unknown, max: number): string {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

export const submitProblemReport = onRequest(
  { cors: true, timeoutSeconds: 30 },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const decoded = await verifyAuth(req.headers.authorization);
    if (!decoded?.uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const uid = decoded.uid;
    const reportId = req.body?.reportId;
    const title = cleanString(req.body?.title, 120);
    const description = cleanString(req.body?.description, 2000);

    if (!isValidReportId(reportId)) {
      res.status(400).json({ error: "Invalid report id" });
      return;
    }
    if (!title || !description) {
      res.status(400).json({ error: "Title and description are required" });
      return;
    }

    const rawAttachments = Array.isArray(req.body?.attachments) ? req.body.attachments : [];
    if (rawAttachments.length > 10) {
      res.status(400).json({ error: "Too many attachments" });
      return;
    }

    const attachments = rawAttachments.map((item: any) => ({
      name: cleanString(item?.name, 160) || "Attachment",
      contentType: cleanString(item?.contentType, 100),
      sizeBytes: Number(item?.sizeBytes) || 0,
      storagePath: cleanString(item?.storagePath, 500),
    }));

    const expectedPrefix = `users/${uid}/problem_reports/${reportId}/media/`;
    for (const item of attachments) {
      if (!item.contentType.match(/^(image|video)\//)) {
        res.status(400).json({ error: "Only image and video attachments are allowed" });
        return;
      }
      if (item.sizeBytes < 0 || item.sizeBytes > MAX_ATTACHMENT_BYTES) {
        res.status(400).json({ error: "Attachment exceeds 50 MB" });
        return;
      }
      if (!item.storagePath.startsWith(expectedPrefix)) {
        res.status(400).json({ error: "Invalid attachment path" });
        return;
      }
    }

    const now = Date.now();
    const report = {
      uid,
      userEmail: decoded.email || null,
      userName: decoded.name || null,
      title,
      description,
      status: "open",
      createdAt: now,
      updatedAt: now,
      attachments,
      appVersionName: cleanString(req.body?.appVersionName, 40),
      appVersionCode: Number(req.body?.appVersionCode) || 0,
      device: cleanString(req.body?.device, 160),
      androidSdk: Number(req.body?.androidSdk) || 0,
    };

    const db = getFirestore("preamble");
    try {
      await db.runTransaction(async (tx) => {
        const reportRef = db.collection("problemReports").doc(reportId);
        const existingReport = await tx.get(reportRef);
        if (existingReport.exists) {
          const error = new Error("Report already exists");
          (error as any).statusCode = 409;
          throw error;
        }

        const gateRef = db.collection("problemReportGates").doc(uid);
        const gate = await tx.get(gateRef);
        const gateStatus = gate.data()?.status || "open";
        if (gate.exists && ACTIVE_STATUSES.has(gateStatus)) {
          const error = new Error("Your current report is still in review. You can send another once it is resolved.");
          (error as any).statusCode = 429;
          throw error;
        }

        tx.set(reportRef, report);
        tx.set(gateRef, {
          uid,
          activeReportId: reportId,
          status: "open",
          createdAt: now,
          updatedAt: now,
        });
      });
    } catch (err: any) {
      res.status(err.statusCode || 500).json({ error: err.message || "Failed to submit report" });
      return;
    }

    res.json({ success: true, reportId, report });
  },
);
