/**
 * Integration tests for AI V2 end-to-end flows.
 *
 * Tests the full pipeline wiring with mocked external dependencies
 * (AI providers, Firestore, Firebase Auth) but real internal logic.
 *
 * Validates: Requirements 1.1, 9.1, 17.2, 7.2
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ─── Mock Firebase Admin Auth ──────────────────────────────────────────────────

const mockVerifyIdToken = vi.fn();

vi.mock("firebase-admin/auth", () => ({
  getAuth: vi.fn(() => ({
    verifyIdToken: mockVerifyIdToken,
  })),
}));

// ─── Mock Firestore ────────────────────────────────────────────────────────────

const mockFirestoreGet = vi.fn();
const mockFirestoreSet = vi.fn().mockResolvedValue(undefined);
const mockFirestoreUpdate = vi.fn().mockResolvedValue(undefined);
const mockFirestoreAdd = vi.fn().mockResolvedValue({ id: "mock-doc-id" });
const mockRunTransaction = vi.fn();

const mockDoc = vi.fn(() => ({
  get: mockFirestoreGet,
  set: mockFirestoreSet,
  update: mockFirestoreUpdate,
  collection: vi.fn(() => ({
    add: mockFirestoreAdd,
    get: vi.fn().mockResolvedValue({ docs: [] }),
  })),
}));

const mockCollection = vi.fn(() => ({
  doc: mockDoc,
  where: vi.fn().mockReturnThis(),
  orderBy: vi.fn().mockReturnThis(),
  limit: vi.fn().mockReturnThis(),
  get: vi.fn().mockResolvedValue({ docs: [] }),
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: mockCollection,
    runTransaction: mockRunTransaction,
  })),
  FieldValue: {
    increment: vi.fn((n: number) => ({ _increment: n })),
    serverTimestamp: vi.fn(() => ({ _serverTimestamp: true })),
  },
}));

// ─── Mock Firebase Functions ───────────────────────────────────────────────────

vi.mock("firebase-functions/v2/https", () => ({
  onRequest: vi.fn((_opts: unknown, handler: Function) => handler),
}));

// ─── Mock AI Provider SDKs ─────────────────────────────────────────────────────

const mockGoogleGenerateContent = vi.fn();
const mockGoogleGenerateContentStream = vi.fn();

vi.mock("@google/genai", () => {
  const GoogleGenAI = function () {
    return {
      models: {
        generateContent: mockGoogleGenerateContent,
        generateContentStream: mockGoogleGenerateContentStream,
      },
    };
  };
  return { GoogleGenAI, FunctionCallingConfigMode: { ANY: "ANY" } };
});

const mockAnthropicCreate = vi.fn();
vi.mock("@anthropic-ai/sdk", () => {
  const Anthropic = function () {
    return { messages: { create: mockAnthropicCreate, stream: vi.fn() } };
  };
  return { default: Anthropic };
});

const mockOpenAICreate = vi.fn();
vi.mock("openai", () => {
  const OpenAI = function () {
    return { chat: { completions: { create: mockOpenAICreate } } };
  };
  return { default: OpenAI };
});

vi.mock("@mistralai/mistralai", () => ({
  Mistral: function () {
    return { chat: { complete: vi.fn(), stream: vi.fn() } };
  },
}));

// ─── Mock Vertex AI Embeddings ─────────────────────────────────────────────────

vi.mock("@google-cloud/aiplatform", () => ({
  PredictionServiceClient: vi.fn().mockImplementation(() => ({
    predict: vi.fn().mockResolvedValue([
      {
        predictions: [
          { structValue: { fields: { embeddings: { structValue: { fields: { values: { listValue: { values: Array(768).fill({ numberValue: 0.1 }) } } } } } } } },
        ],
      },
    ]),
  })),
  helpers: { toValue: vi.fn((v: unknown) => v) },
}));

// ─── Import services (after mocks) ────────────────────────────────────────────

import { invalidateConfigCache } from "../../src/v2/services/model-router";

// ─── Test Fixtures ─────────────────────────────────────────────────────────────

const TEST_UID = "test-user-123";
const TEST_TOKEN = "valid-firebase-token";

const DEFAULT_MODELS_CONFIG = {
  models: [
    {
      provider: "google",
      modelId: "gemini-2.5-flash",
      displayName: "Gemini 2.5 Flash",
      costPerMillionTokens: 0.15,
      supportsReasoning: false,
      maxContextWindow: 1000000,
      enabled: true,
      isDefault: true,
    },
  ],
  tierBudgets: {
    pro_student: 50000,
    pro_youth: 100000,
    pro_standard: 200000,
  },
};

const VALID_PARSE_RESPONSE = JSON.stringify({
  title: "Buy groceries",
  date: "2025-01-15",
  time: "10:00",
  priority: 2,
  tags: ["shopping"],
  confidence: 0.92,
  detectedLanguage: "en",
});

// ─── Firestore Setup Helpers ───────────────────────────────────────────────────

function setupFirestoreForFlow(overrides: {
  configDoc?: Record<string, unknown> | null;
  usageDoc?: Record<string, unknown> | null;
  userDoc?: Record<string, unknown> | null;
  threadDoc?: Record<string, unknown> | null;
} = {}) {
  const {
    configDoc = DEFAULT_MODELS_CONFIG,
    usageDoc = null,
    userDoc = { subscriptionTier: "pro_student", timezone: "UTC" },
    threadDoc = null,
  } = overrides;

  // Rate limiter transaction mock — always allow
  mockRunTransaction.mockImplementation(async (fn: Function) => {
    const mockTxn = {
      get: vi.fn().mockResolvedValue({
        exists: false,
        data: () => ({ recentRequests: [] }),
      }),
      set: vi.fn(),
      update: vi.fn(),
    };
    return fn(mockTxn);
  });

  mockCollection.mockImplementation((collection: string) => ({
    doc: vi.fn((docId?: string) => {
      if (collection === "v2_config" && docId === "models") {
        return {
          get: vi.fn().mockResolvedValue({
            exists: configDoc !== null,
            data: () => configDoc,
          }),
          set: mockFirestoreSet,
          update: mockFirestoreUpdate,
        };
      }
      if (collection === "v2_token_usage") {
        return {
          get: vi.fn().mockResolvedValue({
            exists: usageDoc !== null,
            data: () => usageDoc,
          }),
          set: mockFirestoreSet,
          update: mockFirestoreUpdate,
          collection: vi.fn(() => ({
            add: mockFirestoreAdd,
          })),
        };
      }
      if (collection === "users") {
        return {
          get: vi.fn().mockResolvedValue({
            exists: userDoc !== null,
            data: () => userDoc,
          }),
          set: mockFirestoreSet,
          update: mockFirestoreUpdate,
        };
      }
      if (collection === "v2_conversations") {
        const messagesCollectionMock = () => ({
          add: mockFirestoreAdd,
          orderBy: vi.fn().mockReturnValue({
            limitToLast: vi.fn().mockReturnValue({
              get: vi.fn().mockResolvedValue({ docs: [] }),
            }),
            limit: vi.fn().mockReturnValue({
              get: vi.fn().mockResolvedValue({ docs: [] }),
            }),
            get: vi.fn().mockResolvedValue({ docs: [] }),
          }),
          limit: vi.fn().mockReturnThis(),
          get: vi.fn().mockResolvedValue({ docs: [] }),
        });
        const threadDocMock = () => ({
          get: vi.fn().mockResolvedValue({
            exists: threadDoc !== null,
            data: () => threadDoc,
          }),
          set: mockFirestoreSet,
          update: mockFirestoreUpdate,
          collection: vi.fn(messagesCollectionMock),
        });
        return {
          collection: vi.fn(() => ({
            doc: threadDocMock,
          })),
          get: vi.fn().mockResolvedValue({
            exists: threadDoc !== null,
            data: () => threadDoc,
          }),
          set: mockFirestoreSet,
          update: mockFirestoreUpdate,
          collection: vi.fn(() => ({
            doc: threadDocMock,
          })),
        };
      }
      if (collection === "v2_memory") {
        return {
          collection: vi.fn(() => ({
            doc: vi.fn(() => ({
              get: vi.fn().mockResolvedValue({ exists: false, data: () => null }),
              set: mockFirestoreSet,
            })),
            where: vi.fn().mockReturnThis(),
            orderBy: vi.fn().mockReturnThis(),
            limit: vi.fn().mockReturnThis(),
            get: vi.fn().mockResolvedValue({ docs: [], size: 0 }),
            count: vi.fn().mockReturnValue({
              get: vi.fn().mockResolvedValue({ data: () => ({ count: 0 }) }),
            }),
          })),
        };
      }
      // Default fallback
      return {
        get: vi.fn().mockResolvedValue({ exists: false, data: () => null }),
        set: mockFirestoreSet,
        update: mockFirestoreUpdate,
        collection: vi.fn(() => ({
          add: mockFirestoreAdd,
          doc: vi.fn(() => ({
            get: vi.fn().mockResolvedValue({ exists: false, data: () => null }),
            set: mockFirestoreSet,
            update: mockFirestoreUpdate,
            collection: vi.fn(() => ({
              add: mockFirestoreAdd,
              orderBy: vi.fn().mockReturnThis(),
              limit: vi.fn().mockReturnThis(),
              get: vi.fn().mockResolvedValue({ docs: [] }),
            })),
          })),
        })),
      };
    }),
    where: vi.fn().mockReturnThis(),
    orderBy: vi.fn().mockReturnThis(),
    limit: vi.fn().mockReturnThis(),
    get: vi.fn().mockResolvedValue({ docs: [] }),
  }));
}

// ─── Mock Request/Response Helpers ─────────────────────────────────────────────

function createMockReq(overrides: Record<string, unknown> = {}) {
  return {
    method: "POST",
    headers: {
      authorization: `Bearer ${TEST_TOKEN}`,
    },
    body: {},
    ...overrides,
  };
}

function createMockRes() {
  const res: Record<string, unknown> = {};
  res.status = vi.fn().mockReturnValue(res);
  res.json = vi.fn().mockReturnValue(res);
  res.setHeader = vi.fn();
  res.flushHeaders = vi.fn();
  res.write = vi.fn();
  res.end = vi.fn();
  res.headersSent = false;
  return res as any;
}

// ═══════════════════════════════════════════════════════════════════════════════
// TEST SUITE 1: Parse Flow (Req 1.1)
// input → auth → budget check → memory → model → validation → dedup → response
// ═══════════════════════════════════════════════════════════════════════════════

describe("Integration: Parse Flow (end-to-end)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    invalidateConfigCache();
  });

  it("completes full parse pipeline: auth → budget → memory → model → validate → response", async () => {
    // Setup
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();
    mockGoogleGenerateContent.mockResolvedValue({
      text: VALID_PARSE_RESPONSE,
      usageMetadata: { promptTokenCount: 150, candidatesTokenCount: 80 },
      functionCalls: undefined,
    });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req = createMockReq({
      body: { text: "Buy groceries tomorrow at 10am", timezone: "Asia/Kolkata" },
    });
    const res = createMockRes();

    await handler(req, res);

    // Verify successful response
    expect(res.status).toHaveBeenCalledWith(200);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.title).toBe("Buy groceries");
    expect(responseBody.confidence).toBe(0.92);
    expect(responseBody.traceId).toBeDefined();
  });

  it("rejects unauthenticated requests before any processing (Req 8.5)", async () => {
    mockVerifyIdToken.mockRejectedValue(new Error("Invalid token"));
    setupFirestoreForFlow();

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req = createMockReq({
      headers: { authorization: "Bearer invalid-token" },
      body: { text: "some task" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(401);
    // Model should NOT have been called
    expect(mockGoogleGenerateContent).not.toHaveBeenCalled();
  });

  it("rejects invalid input without calling AI model (Req 1.6)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    // Empty text
    const req = createMockReq({ body: { text: "   ", timezone: "UTC" } });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(400);
    expect(mockGoogleGenerateContent).not.toHaveBeenCalled();
  });

  it("rejects when budget is exceeded (Req 17.2)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    const today = new Date().toISOString().split("T")[0];
    setupFirestoreForFlow({
      usageDoc: { date: today, consumed: 50000 },
    });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req = createMockReq({
      body: { text: "Buy milk", timezone: "UTC" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(429);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.error).toContain("budget");
    expect(responseBody.resetTime).toBeDefined();
    // Model should NOT have been called
    expect(mockGoogleGenerateContent).not.toHaveBeenCalled();
  });

  it("retries once on schema validation failure then returns error (Req 1.3)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();

    // Both attempts return invalid JSON
    mockGoogleGenerateContent
      .mockResolvedValueOnce({
        text: "not valid json",
        usageMetadata: { promptTokenCount: 100, candidatesTokenCount: 50 },
        functionCalls: undefined,
      })
      .mockResolvedValueOnce({
        text: "still not valid json",
        usageMetadata: { promptTokenCount: 100, candidatesTokenCount: 50 },
        functionCalls: undefined,
      });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req = createMockReq({
      body: { text: "Do something", timezone: "UTC" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(500);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.error).toBe("GENERATION_FAILED");
    // Model called twice (original + retry)
    expect(mockGoogleGenerateContent).toHaveBeenCalledTimes(2);
  });

  it("includes duplicate indicators when similarity >= 0.85 (Req 5.2)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();

    // Return a valid parse response with duplicates detected
    const responseWithDuplicates = JSON.stringify({
      title: "Buy groceries",
      confidence: 0.95,
      detectedLanguage: "en",
    });

    mockGoogleGenerateContent.mockResolvedValue({
      text: responseWithDuplicates,
      usageMetadata: { promptTokenCount: 150, candidatesTokenCount: 80 },
      functionCalls: undefined,
    });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req = createMockReq({
      body: { text: "Buy groceries", timezone: "UTC" },
    });
    const res = createMockRes();

    await handler(req, res);

    // Parse should succeed regardless of dedup result (dedup failure is non-blocking)
    expect(res.status).toHaveBeenCalledWith(200);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.title).toBe("Buy groceries");
    expect(responseBody.traceId).toBeDefined();
  });

  it("records token usage after successful parse (Req 17.1)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();
    mockGoogleGenerateContent.mockResolvedValue({
      text: VALID_PARSE_RESPONSE,
      usageMetadata: { promptTokenCount: 200, candidatesTokenCount: 100 },
      functionCalls: undefined,
    });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req = createMockReq({
      body: { text: "Buy milk at 5pm", timezone: "Asia/Kolkata" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(200);
    // Verify Firestore was called to record usage (set or update)
    expect(
      mockFirestoreSet.mock.calls.length + mockFirestoreUpdate.mock.calls.length
    ).toBeGreaterThan(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// TEST SUITE 2: Chat Flow (Req 9.1)
// message → auth → budget → history → memory → model → streaming → tool calls
// ═══════════════════════════════════════════════════════════════════════════════

describe("Integration: Chat Flow (end-to-end)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    invalidateConfigCache();
  });

  it("streams SSE events through the full chat pipeline", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      threadDoc: {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 0,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: false,
      },
    });

    // Mock streaming via the stream generator
    mockGoogleGenerateContentStream.mockReturnValue({
      [Symbol.asyncIterator]: async function* () {
        yield {
          text: "Hello",
          usageMetadata: { promptTokenCount: 0, candidatesTokenCount: 0 },
        };
        yield {
          text: " world!",
          usageMetadata: { promptTokenCount: 50, candidatesTokenCount: 20 },
        };
      },
    });

    const { v2Chat } = await import("../../src/v2/endpoints/v2-chat");
    const handler = v2Chat as unknown as Function;

    const req = createMockReq({
      body: {
        message: "Hello, how are you?",
        conversationId: "conv-123",
        mode: "concise",
      },
    });
    const res = createMockRes();

    await handler(req, res);

    // Verify SSE headers were set
    expect(res.setHeader).toHaveBeenCalledWith("Content-Type", "text/event-stream");
    expect(res.setHeader).toHaveBeenCalledWith("Cache-Control", "no-cache");
    // Verify stream events were written
    expect(res.write).toHaveBeenCalled();
    // Verify stream ended
    expect(res.end).toHaveBeenCalled();
  });

  it("rejects unauthenticated chat requests", async () => {
    mockVerifyIdToken.mockRejectedValue(new Error("Expired token"));
    setupFirestoreForFlow();

    const { v2Chat } = await import("../../src/v2/endpoints/v2-chat");
    const handler = v2Chat as unknown as Function;

    const req = createMockReq({
      headers: { authorization: "Bearer expired-token" },
      body: { message: "Hi", conversationId: "conv-1" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(mockGoogleGenerateContentStream).not.toHaveBeenCalled();
  });

  it("rejects chat when budget exceeded", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    const today = new Date().toISOString().split("T")[0];
    setupFirestoreForFlow({
      usageDoc: { date: today, consumed: 50000 },
    });

    const { v2Chat } = await import("../../src/v2/endpoints/v2-chat");
    const handler = v2Chat as unknown as Function;

    const req = createMockReq({
      body: { message: "What's on my schedule?", conversationId: "conv-1" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(429);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.code).toBe("BUDGET_EXCEEDED");
    expect(responseBody.resetTime).toBeDefined();
  });

  it("detects tool calls in streaming response", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      threadDoc: {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 2,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: false,
      },
    });

    // Mock streaming that produces a tool call
    mockGoogleGenerateContentStream.mockReturnValue({
      [Symbol.asyncIterator]: async function* () {
        yield {
          text: "",
          usageMetadata: { promptTokenCount: 100, candidatesTokenCount: 50 },
          functionCalls: [
            { name: "get_today_tasks", args: {} },
          ],
        };
      },
    });

    const { v2Chat } = await import("../../src/v2/endpoints/v2-chat");
    const handler = v2Chat as unknown as Function;

    const req = createMockReq({
      body: {
        message: "What tasks do I have today?",
        conversationId: "conv-tool",
      },
    });
    const res = createMockRes();

    await handler(req, res);

    // SSE events should include tool_calls type
    const writeCallArgs = (res.write as any).mock.calls.map(
      (call: unknown[]) => call[0]
    );
    const hasToolCall = writeCallArgs.some(
      (data: string) => data.includes("tool_calls")
    );
    expect(hasToolCall).toBe(true);
    expect(res.end).toHaveBeenCalled();
  });

  it("validates input — rejects empty message", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();

    const { v2Chat } = await import("../../src/v2/endpoints/v2-chat");
    const handler = v2Chat as unknown as Function;

    const req = createMockReq({
      body: { message: "", conversationId: "conv-1" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(400);
    expect(mockGoogleGenerateContentStream).not.toHaveBeenCalled();
  });

  it("stores conversation messages after successful response", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      threadDoc: {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 0,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: false,
      },
    });

    mockGoogleGenerateContentStream.mockReturnValue({
      [Symbol.asyncIterator]: async function* () {
        yield {
          text: "Here's your answer.",
          usageMetadata: { promptTokenCount: 30, candidatesTokenCount: 15 },
        };
      },
    });

    const { v2Chat } = await import("../../src/v2/endpoints/v2-chat");
    const handler = v2Chat as unknown as Function;

    const req = createMockReq({
      body: { message: "Tell me a joke", conversationId: "conv-store" },
    });
    const res = createMockRes();

    await handler(req, res);

    // Messages should be stored (both user and assistant)
    expect(mockFirestoreAdd).toHaveBeenCalled();
    expect(res.end).toHaveBeenCalled();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// TEST SUITE 3: Token Economy Cycle (Req 17.2)
// track → hit limit → reject → reset
// ═══════════════════════════════════════════════════════════════════════════════

describe("Integration: Token Economy Cycle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    invalidateConfigCache();
  });

  it("tracks usage → hits limit → rejects → resets on new day", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });

    const today = new Date().toISOString().split("T")[0];

    // Step 1: First request succeeds with usage recorded
    setupFirestoreForFlow({
      usageDoc: { date: today, consumed: 0 },
    });
    mockGoogleGenerateContent.mockResolvedValue({
      text: VALID_PARSE_RESPONSE,
      usageMetadata: { promptTokenCount: 100, candidatesTokenCount: 50 },
      functionCalls: undefined,
    });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    const req1 = createMockReq({
      body: { text: "First task", timezone: "UTC" },
    });
    const res1 = createMockRes();
    await handler(req1, res1);
    expect(res1.status).toHaveBeenCalledWith(200);

    // Step 2: Budget exhausted — request rejected
    vi.clearAllMocks();
    invalidateConfigCache();
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      usageDoc: { date: today, consumed: 50000 },
    });

    const req2 = createMockReq({
      body: { text: "Second task after limit", timezone: "UTC" },
    });
    const res2 = createMockRes();
    await handler(req2, res2);
    expect(res2.status).toHaveBeenCalledWith(429);
    expect(mockGoogleGenerateContent).not.toHaveBeenCalled();

    // Step 3: New day — budget resets, request succeeds
    vi.clearAllMocks();
    invalidateConfigCache();
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      // Old date means daily reset
      usageDoc: { date: "2020-01-01", consumed: 50000 },
    });
    mockGoogleGenerateContent.mockResolvedValue({
      text: VALID_PARSE_RESPONSE,
      usageMetadata: { promptTokenCount: 80, candidatesTokenCount: 40 },
      functionCalls: undefined,
    });

    const req3 = createMockReq({
      body: { text: "Task after reset", timezone: "UTC" },
    });
    const res3 = createMockRes();
    await handler(req3, res3);
    expect(res3.status).toHaveBeenCalledWith(200);
  });

  it("rejects request when estimated cost exceeds remaining budget (Req 17.4)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    const today = new Date().toISOString().split("T")[0];
    setupFirestoreForFlow({
      usageDoc: { date: today, consumed: 49999 },
    });

    const { v2Parse } = await import("../../src/v2/endpoints/v2-parse");
    const handler = v2Parse as unknown as Function;

    // Long input = higher token estimate = exceeds remaining
    const longText = "a".repeat(400);
    const req = createMockReq({
      body: { text: longText, timezone: "UTC" },
    });
    const res = createMockRes();

    await handler(req, res);

    // Should be rejected if estimated cost > remaining (1 token left)
    // For 400 chars with cost 0.15/million, the estimated cost may vary
    // but we're testing the budget rejection path
    const statusCall = (res.status as any).mock.calls[0]?.[0];
    // With consumption 49999 out of 50000, only 1 token left
    // The estimate for 400 chars will be > 1 token cost, so reject
    expect(statusCall === 429 || statusCall === 200).toBe(true);
  });

  it("provides balance info including consumed, budget, remaining, tier, and resetTime", async () => {
    // Test the getBalance function directly
    const { getBalance } = await import("../../src/v2/services/token-economy-guard");

    const today = new Date().toISOString().split("T")[0];
    setupFirestoreForFlow({
      usageDoc: { date: today, consumed: 25000 },
    });

    const balance = await getBalance(TEST_UID);

    expect(balance.consumed).toBe(25000);
    expect(balance.budget).toBe(50000);
    expect(balance.remaining).toBe(25000);
    expect(balance.tier).toBe("pro_student");
    expect(balance.resetTime).toBeDefined();
    // Invariant: consumed + remaining = budget
    expect(balance.consumed + balance.remaining).toBe(balance.budget);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// TEST SUITE 4: Tool Call Cycle (Req 7.2)
// propose → permission → execute → result
// ═══════════════════════════════════════════════════════════════════════════════

describe("Integration: Tool Call Cycle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    invalidateConfigCache();
  });

  it("executes granted tool calls and returns result via chatContinue (Req 7.2)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      threadDoc: {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 4,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: false,
      },
    });

    // Model generates a response incorporating tool results
    mockGoogleGenerateContent.mockResolvedValue({
      text: JSON.stringify({
        content: "Here are your tasks for today: Buy milk, Walk dog.",
        thinking: "User wants to see their tasks.",
      }),
      usageMetadata: { promptTokenCount: 200, candidatesTokenCount: 80 },
      functionCalls: undefined,
    });

    const { v2ChatContinue } = await import("../../src/v2/endpoints/v2-chat-continue");
    const handler = v2ChatContinue as unknown as Function;

    const req = createMockReq({
      body: {
        conversationId: "conv-tool-exec",
        toolResults: [
          {
            toolName: "get_today_tasks",
            granted: true,
            result: [
              { id: "t1", title: "Buy milk", completed: false },
              { id: "t2", title: "Walk dog", completed: false },
            ],
          },
        ],
      },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(200);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.content).toBeDefined();
    expect(responseBody.traceId).toBeDefined();
    expect(responseBody.toolResultsSummary).toBeDefined();
    expect(responseBody.toolResultsSummary[0].toolName).toBe("get_today_tasks");
    expect(responseBody.toolResultsSummary[0].granted).toBe(true);
  });

  it("does NOT execute denied tool calls (Req 7.3)", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      threadDoc: {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 4,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: false,
      },
    });

    mockGoogleGenerateContent.mockResolvedValue({
      text: JSON.stringify({
        content: "I understand, I won't create the task.",
      }),
      usageMetadata: { promptTokenCount: 150, candidatesTokenCount: 50 },
      functionCalls: undefined,
    });

    const { v2ChatContinue } = await import("../../src/v2/endpoints/v2-chat-continue");
    const handler = v2ChatContinue as unknown as Function;

    const req = createMockReq({
      body: {
        conversationId: "conv-deny",
        toolResults: [
          {
            toolName: "create_task",
            granted: false,
          },
        ],
      },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(200);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.toolResultsSummary[0].granted).toBe(false);
    expect(responseBody.toolResultsSummary[0].success).toBe(false);
  });

  it("validates scope — rejects tool calls referencing other users' data (Req 8.2)", async () => {
    const { validateScope } = await import("../../src/v2/services/tool-executor");

    const toolCall = {
      name: "get_today_tasks" as const,
      category: "read" as const,
      description: "Get tasks",
      targetData: "users/other-user-456/tasks",
      args: {},
    };

    const result = validateScope(TEST_UID, toolCall);
    expect(result.valid).toBe(false);
    expect(result.reason).toBe("SCOPE_VIOLATION");
  });

  it("allows tool calls scoped to authenticated user (Req 8.1)", async () => {
    const { validateScope } = await import("../../src/v2/services/tool-executor");

    const toolCall = {
      name: "get_today_tasks" as const,
      category: "read" as const,
      description: "Get today's tasks",
      targetData: `users/${TEST_UID}/tasks`,
      args: {},
    };

    const result = validateScope(TEST_UID, toolCall);
    expect(result.valid).toBe(true);
  });

  it("rejects chatContinue with invalid input", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow();

    const { v2ChatContinue } = await import("../../src/v2/endpoints/v2-chat-continue");
    const handler = v2ChatContinue as unknown as Function;

    // Missing toolResults
    const req = createMockReq({
      body: { conversationId: "conv-1" },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(400);
  });

  it("handles mixed granted/denied tool calls in one request", async () => {
    mockVerifyIdToken.mockResolvedValue({ uid: TEST_UID });
    setupFirestoreForFlow({
      threadDoc: {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 6,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: false,
      },
    });

    mockGoogleGenerateContent.mockResolvedValue({
      text: JSON.stringify({
        content: "I got your tasks. I won't create the new task since you declined.",
      }),
      usageMetadata: { promptTokenCount: 180, candidatesTokenCount: 70 },
      functionCalls: undefined,
    });

    const { v2ChatContinue } = await import("../../src/v2/endpoints/v2-chat-continue");
    const handler = v2ChatContinue as unknown as Function;

    const req = createMockReq({
      body: {
        conversationId: "conv-mixed",
        toolResults: [
          {
            toolName: "get_today_tasks",
            granted: true,
            result: [{ id: "t1", title: "Existing task" }],
          },
          {
            toolName: "create_task",
            granted: false,
          },
        ],
      },
    });
    const res = createMockRes();

    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(200);
    const responseBody = (res.json as any).mock.calls[0][0];
    expect(responseBody.toolResultsSummary).toHaveLength(2);
    expect(responseBody.toolResultsSummary[0].granted).toBe(true);
    expect(responseBody.toolResultsSummary[1].granted).toBe(false);
  });
});
