/**
 * The server decides model, effort and output budget per operation; the app can't choose.
 * Mirrors AiOperation in the Android app (app/src/main/java/app/levelup2027/ai/AiClient.kt).
 */
export const OPERATIONS = {
  intake: { model: "claude-haiku-5-5", effort: "low", maxTokens: 4000, fallback: false },
  plan: { model: "claude-sonnet-5-5", effort: "medium", maxTokens: 16000, fallback: true },
  week: { model: "claude-sonnet-5-5", effort: "low", maxTokens: 8000, fallback: true },
  adjust: { model: "claude-sonnet-5-5", effort: "low", maxTokens: 6000, fallback: true },
};

export const LIMITS = {
  maxSystemChars: 20000,
  maxMessages: 12,
  maxMessageChars: 30000,
  maxTotalChars: 60000,
  maxSchemaChars: 30000,
};
