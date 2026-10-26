import Anthropic from "@anthropic-ai/sdk";
import { LIMITS, OPERATIONS } from "./operations.js";
import PROMPT_HASHES from "./prompt-hashes.json" with { type: "json" };

/**
 * Ascend AI server.
 *
 *   POST   /v1/register       → { token }               anonymous device token (rate-limited per network)
 *   POST   /v1/ai             → { output, model, usage } one structured-output Claude call for an allowed operation
 *   GET    /v1/usage          → this device's usage and limits
 *   DELETE /v1/device         → forget this device (token and counters)
 *   GET    /v1/admin/usage    → per-day totals (Bearer ADMIN_TOKEN)
 *
 * Stores no prompts, plans or responses: only counters keyed by device token and day/month.
 */

const json = (status, body) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

const fail = (status, error, message) => json(status, { error, message });

const intVar = (env, name, fallback) => {
  const n = Number.parseInt(env[name] ?? "", 10);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
};

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const day = (now) => new Date(now).toISOString().slice(0, 10);
const month = (now) => new Date(now).toISOString().slice(0, 7);
const TTL_DAY = 2 * 24 * 3600;
const TTL_MONTH = 40 * 24 * 3600;

async function getJson(kv, key, fallback) {
  const v = await kv.get(key, "json");
  return v ?? fallback;
}

/** Validates the request body; returns an error Response or the cleaned request. */
export async function validateAiRequest(body, env) {
  if (!body || typeof body !== "object") return { error: fail(400, "bad_request", "Expected a JSON object.") };
  const op = OPERATIONS[body.op];
  if (!op) return { error: fail(400, "bad_request", "Unknown operation.") };
  if (typeof body.system !== "string" || body.system.length === 0 || body.system.length > LIMITS.maxSystemChars) {
    return { error: fail(400, "bad_request", "Invalid system prompt.") };
  }
  if (env.ENFORCE_PROMPT_HASHES !== "false") {
    const hash = await sha256(body.system);
    if (PROMPT_HASHES[body.op] !== hash) {
      return { error: fail(400, "unknown_prompt", "This app version's prompt isn't accepted by the server. Update the app.") };
    }
  }
  const messages = body.messages;
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > LIMITS.maxMessages) {
    return { error: fail(400, "bad_request", "Invalid messages.") };
  }
  let total = 0;
  for (const [i, m] of messages.entries()) {
    const expected = i % 2 === 0 ? "user" : "assistant";
    if (!m || m.role !== expected || typeof m.content !== "string" || m.content.length === 0 || m.content.length > LIMITS.maxMessageChars) {
      return { error: fail(400, "bad_request", "Messages must alternate user/assistant and start with user.") };
    }
    total += m.content.length;
  }
  if (messages.length % 2 === 0) return { error: fail(400, "bad_request", "The last message must be from the user.") };
  if (total > LIMITS.maxTotalChars) return { error: fail(413, "too_large", "The request is too large.") };
  const schema = body.schema;
  if (!schema || typeof schema !== "object" || schema.type !== "object" || JSON.stringify(schema).length > LIMITS.maxSchemaChars) {
    return { error: fail(400, "bad_request", "A JSON schema is required.") };
  }
  return { op: body.op, config: op, system: body.system, messages, schema };
}

export function createHandler({ anthropicFactory = (env) => new Anthropic({ apiKey: env.ANTHROPIC_API_KEY }), now = () => Date.now() } = {}) {
  async function authDevice(request, env) {
    const auth = request.headers.get("Authorization") ?? "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
    if (!token || token.length > 128) return null;
    const dev = await env.USAGE.get(`dev:${token}`, "json");
    return dev ? token : null;
  }

  async function register(request, env) {
    const t = now();
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    // Only a hash of the network address is stored, and only for a day.
    const ipKey = `reg:${(await sha256(ip)).slice(0, 32)}:${day(t)}`;
    const count = Number((await env.USAGE.get(ipKey)) ?? 0);
    if (count >= intVar(env, "REGISTRATIONS_PER_IP_PER_DAY", 5)) return fail(429, "rate_limited", "Too many new devices from this network today.");
    await env.USAGE.put(ipKey, String(count + 1), { expirationTtl: TTL_DAY });
    const bytes = crypto.getRandomValues(new Uint8Array(24));
    const token = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
    await env.USAGE.put(`dev:${token}`, JSON.stringify({ created: t }));
    return json(200, { token });
  }

  async function usageOf(env, token, t) {
    const today = Number((await env.USAGE.get(`u:${token}:${day(t)}`)) ?? 0);
    const m = await getJson(env.USAGE, `m:${token}:${month(t)}`, { requests: 0, input: 0, output: 0 });
    return { today, month: m };
  }

  async function ai(request, env, ctx) {
    const t = now();
    const token = await authDevice(request, env);
    if (!token) return fail(401, "unauthorized", "Unknown device. Register again.");
    let body;
    try {
      body = await request.json();
    } catch {
      return fail(400, "bad_request", "Invalid JSON.");
    }
    const v = await validateAiRequest(body, env);
    if (v.error) return v.error;

    // Quotas are checked and the request counted before calling the model.
    const { today, month: m } = await usageOf(env, token, t);
    const global = await getJson(env.USAGE, `g:${day(t)}`, { requests: 0, input: 0, output: 0 });
    if (today >= intVar(env, "DAILY_REQUEST_LIMIT", 15)) {
      return fail(429, "quota_exceeded", "You've used today's AI requests. Your plan keeps working; try again tomorrow.");
    }
    if (m.requests >= intVar(env, "MONTHLY_REQUEST_LIMIT", 120) || m.output >= intVar(env, "MONTHLY_OUTPUT_TOKEN_LIMIT", 400000)) {
      return fail(429, "quota_exceeded", "You've used this month's AI allowance. Your plan keeps working without AI.");
    }
    if (global.requests >= intVar(env, "GLOBAL_DAILY_REQUEST_LIMIT", 2000)) {
      return fail(429, "rate_limited", "The AI server is at today's capacity. Please try again tomorrow.");
    }
    await env.USAGE.put(`u:${token}:${day(t)}`, String(today + 1), { expirationTtl: TTL_DAY });
    m.requests += 1;
    global.requests += 1;
    await env.USAGE.put(`m:${token}:${month(t)}`, JSON.stringify(m), { expirationTtl: TTL_MONTH });
    await env.USAGE.put(`g:${day(t)}`, JSON.stringify(global), { expirationTtl: TTL_MONTH });

    const { config } = v;
    const client = anthropicFactory(env);
    const started = Date.now();
    let message;
    try {
      message = await client.messages.create(
        {
          model: config.model,
          max_tokens: config.maxTokens,
          // Identical for every device, so it's cached across requests.
          system: [{ type: "text", text: v.system, cache_control: { type: "ephemeral" } }],
          messages: v.messages,
          thinking: { type: "adaptive" },
          output_config: { effort: config.effort, format: { type: "json_schema", schema: v.schema } },
          ...(config.fallback ? { fallbacks: "default" } : {}),
        },
        config.fallback ? { headers: { "anthropic-beta": "server-side-fallback-2026-07-01" } } : undefined,
      );
    } catch (error) {
      console.log(JSON.stringify({ op: v.op, ok: false, status: error?.status ?? 0, ms: Date.now() - started }));
      if (error instanceof Anthropic.RateLimitError) return fail(429, "rate_limited", "The AI service is busy. Try again in a minute.");
      if (error instanceof Anthropic.AuthenticationError || error instanceof Anthropic.PermissionDeniedError) {
        return fail(502, "server_misconfigured", "The AI server isn't configured correctly.");
      }
      if (error instanceof Anthropic.BadRequestError) return fail(502, "upstream_rejected", "The AI service rejected the request.");
      if (error instanceof Anthropic.APIConnectionError) return fail(502, "upstream_unreachable", "Couldn't reach the AI service.");
      if (error instanceof Anthropic.APIError) return fail(502, "upstream_error", "The AI service had a problem. Try again shortly.");
      return fail(500, "internal", "Something went wrong.");
    }

    const usage = message.usage ?? {};
    const record = async () => {
      const mm = await getJson(env.USAGE, `m:${token}:${month(t)}`, { requests: 0, input: 0, output: 0 });
      mm.input += usage.input_tokens ?? 0;
      mm.output += usage.output_tokens ?? 0;
      await env.USAGE.put(`m:${token}:${month(t)}`, JSON.stringify(mm), { expirationTtl: TTL_MONTH });
      const g = await getJson(env.USAGE, `g:${day(t)}`, { requests: 0, input: 0, output: 0 });
      g.input += usage.input_tokens ?? 0;
      g.output += usage.output_tokens ?? 0;
      await env.USAGE.put(`g:${day(t)}`, JSON.stringify(g), { expirationTtl: TTL_MONTH });
    };
    if (ctx?.waitUntil) ctx.waitUntil(record());
    else await record();
    console.log(JSON.stringify({ op: v.op, ok: true, model: message.model, in: usage.input_tokens, out: usage.output_tokens, cache: usage.cache_read_input_tokens ?? 0, ms: Date.now() - started }));

    if (message.stop_reason === "refusal") return fail(422, "refused", "The AI declined this request. Try rephrasing it.");
    if (message.stop_reason === "max_tokens") return fail(422, "truncated", "The AI's answer was cut off. Try again with fewer goals.");
    const text = (message.content ?? []).filter((b) => b.type === "text").map((b) => b.text).at(-1);
    if (!text) return fail(502, "empty", "The AI returned no plan text.");
    return json(200, {
      output: text,
      model: message.model,
      stop_reason: message.stop_reason,
      usage: { input_tokens: usage.input_tokens ?? 0, output_tokens: usage.output_tokens ?? 0, cache_read_tokens: usage.cache_read_input_tokens ?? 0 },
    });
  }

  async function myUsage(request, env) {
    const token = await authDevice(request, env);
    if (!token) return fail(401, "unauthorized", "Unknown device.");
    const t = now();
    const u = await usageOf(env, token, t);
    return json(200, {
      ...u,
      limits: {
        daily_requests: intVar(env, "DAILY_REQUEST_LIMIT", 15),
        monthly_requests: intVar(env, "MONTHLY_REQUEST_LIMIT", 120),
        monthly_output_tokens: intVar(env, "MONTHLY_OUTPUT_TOKEN_LIMIT", 400000),
      },
    });
  }

  async function forget(request, env) {
    const token = await authDevice(request, env);
    if (!token) return json(200, { deleted: false });
    const t = now();
    await Promise.all([
      env.USAGE.delete(`dev:${token}`),
      env.USAGE.delete(`u:${token}:${day(t)}`),
      env.USAGE.delete(`m:${token}:${month(t)}`),
    ]);
    return json(200, { deleted: true });
  }

  async function admin(request, env) {
    const auth = request.headers.get("Authorization") ?? "";
    if (!env.ADMIN_TOKEN || auth !== `Bearer ${env.ADMIN_TOKEN}`) return fail(401, "unauthorized", "Admin token required.");
    const days = Math.min(Math.max(Number(new URL(request.url).searchParams.get("days") ?? 14) || 14, 1), 60);
    const t = now();
    const out = [];
    for (let i = 0; i < days; i++) {
      const d = day(t - i * 86400000);
      out.push({ day: d, ...(await getJson(env.USAGE, `g:${d}`, { requests: 0, input: 0, output: 0 })) });
    }
    return json(200, { days: out, operations: OPERATIONS });
  }

  return {
    async fetch(request, env, ctx) {
      const { pathname } = new URL(request.url);
      try {
        if (request.method === "POST" && pathname === "/v1/register") return await register(request, env);
        if (request.method === "POST" && pathname === "/v1/ai") return await ai(request, env, ctx);
        if (request.method === "GET" && pathname === "/v1/usage") return await myUsage(request, env);
        if (request.method === "DELETE" && pathname === "/v1/device") return await forget(request, env);
        if (request.method === "GET" && pathname === "/v1/admin/usage") return await admin(request, env);
        if (request.method === "GET" && pathname === "/") return json(200, { ok: true, service: "ascend-ai" });
        return fail(404, "not_found", "Not found.");
      } catch (e) {
        console.log(JSON.stringify({ path: pathname, error: String(e?.message ?? e).slice(0, 200) }));
        return fail(500, "internal", "Something went wrong.");
      }
    },
  };
}

const handler = createHandler();
export default { fetch: (request, env, ctx) => handler.fetch(request, env, ctx) };
