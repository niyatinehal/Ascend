import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import Anthropic from "@anthropic-ai/sdk";
import { createHandler, validateAiRequest } from "../src/index.js";
import HASHES from "../src/prompt-hashes.json" with { type: "json" };

class FakeKV {
  constructor() { this.map = new Map(); this.puts = []; }
  async get(key, type) {
    const v = this.map.get(key);
    if (v === undefined) return null;
    return type === "json" ? JSON.parse(v) : v;
  }
  async put(key, value, opts) { this.map.set(key, value); this.puts.push([key, opts]); }
  async delete(key) { this.map.delete(key); }
}

const env = (over = {}) => ({ USAGE: new FakeKV(), ENFORCE_PROMPT_HASHES: "false", ANTHROPIC_API_KEY: "sk-ant-test", ADMIN_TOKEN: "admin", ...over });

function fakeClient(response) {
  const calls = [];
  return {
    calls,
    factory: () => ({
      messages: {
        create: async (body, opts) => {
          calls.push({ body, opts });
          if (response instanceof Error) throw response;
          return response;
        },
      },
    }),
  };
}

const okMessage = (text = '{"summary":"ok"}', stop = "end_turn") => ({
  id: "msg_1", type: "message", role: "assistant", model: "claude-sonnet-5-5",
  content: [{ type: "text", text }], stop_reason: stop,
  usage: { input_tokens: 1000, output_tokens: 400, cache_read_input_tokens: 800 },
});

const schema = { type: "object", properties: { summary: { type: "string" } }, required: ["summary"], additionalProperties: false };
const aiBody = (over = {}) => ({ op: "plan", system: "SYSTEM PROMPT", messages: [{ role: "user", content: "{\"today\":\"2026-11-02\"}" }], schema, ...over });

async function call(handler, e, method, path, body, token) {
  const headers = { "Content-Type": "application/json", "CF-Connecting-IP": "203.0.113.7" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await handler.fetch(new Request(`https://ai.example.com${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined }), e, undefined);
  return { status: res.status, body: await res.json() };
}

async function registered(handler, e) {
  const r = await call(handler, e, "POST", "/v1/register");
  assert.equal(r.status, 200);
  return r.body.token;
}

test("register issues a token and rate-limits per network without storing the IP", async () => {
  const e = env({ REGISTRATIONS_PER_IP_PER_DAY: "2" });
  const h = createHandler({ anthropicFactory: fakeClient(okMessage()).factory });
  const t1 = await registered(h, e);
  await registered(h, e);
  assert.equal((await call(h, e, "POST", "/v1/register")).status, 429);
  assert.match(t1, /^[0-9a-f]{48}$/);
  assert.ok([...e.USAGE.map.keys()].every((k) => !k.includes("203.0.113.7")));
});

test("ai call: server picks model/effort, sends schema, caching and fallback, returns output and usage", async () => {
  const e = env();
  const c = fakeClient(okMessage());
  const h = createHandler({ anthropicFactory: c.factory });
  const token = await registered(h, e);
  const r = await call(h, e, "POST", "/v1/ai", aiBody({ model: "claude-fable-5-1", max_tokens: 999999 }), token);
  assert.equal(r.status, 200);
  assert.equal(r.body.output, '{"summary":"ok"}');
  assert.deepEqual(r.body.usage, { input_tokens: 1000, output_tokens: 400, cache_read_tokens: 800 });
  const { body, opts } = c.calls[0];
  assert.equal(body.model, "claude-sonnet-5-5"); // client's model ignored
  assert.equal(body.max_tokens, 16000);
  assert.deepEqual(body.output_config, { effort: "medium", format: { type: "json_schema", schema } });
  assert.deepEqual(body.thinking, { type: "adaptive" });
  assert.equal(body.fallbacks, "default");
  assert.equal(opts.headers["anthropic-beta"], "server-side-fallback-2026-07-01");
  assert.deepEqual(body.system, [{ type: "text", text: "SYSTEM PROMPT", cache_control: { type: "ephemeral" } }]);
  const month = new Date().toISOString().slice(0, 7);
  const m = JSON.parse(e.USAGE.map.get(`m:${token}:${month}`));
  assert.deepEqual(m, { requests: 1, input: 1000, output: 400 });
});

test("intake uses Haiku without fallback", async () => {
  const e = env();
  const c = fakeClient(okMessage());
  const h = createHandler({ anthropicFactory: c.factory });
  const token = await registered(h, e);
  await call(h, e, "POST", "/v1/ai", aiBody({ op: "intake" }), token);
  assert.equal(c.calls[0].body.model, "claude-haiku-5-5");
  assert.equal(c.calls[0].body.fallbacks, undefined);
  assert.equal(c.calls[0].opts, undefined);
});

test("unknown device, bad operations and malformed requests are rejected before any model call", async () => {
  const e = env();
  const c = fakeClient(okMessage());
  const h = createHandler({ anthropicFactory: c.factory });
  assert.equal((await call(h, e, "POST", "/v1/ai", aiBody(), "nope")).status, 401);
  const token = await registered(h, e);
  for (const bad of [
    aiBody({ op: "chat" }),
    aiBody({ system: "" }),
    aiBody({ messages: [] }),
    aiBody({ messages: [{ role: "assistant", content: "hi" }] }),
    aiBody({ messages: [{ role: "user", content: "a" }, { role: "assistant", content: "b" }] }),
    aiBody({ schema: undefined }),
    aiBody({ messages: [{ role: "user", content: "x".repeat(30001) }] }),
  ]) {
    const r = await call(h, e, "POST", "/v1/ai", bad, token);
    assert.equal(r.status, 400, JSON.stringify(r.body));
  }
  assert.equal(c.calls.length, 0);
});

test("only the app's own system prompts are accepted when enforcement is on", async () => {
  const plan = { op: "plan", system: "not the real prompt", messages: [{ role: "user", content: "x" }], schema };
  const r = await validateAiRequest(plan, { ENFORCE_PROMPT_HASHES: "true" });
  assert.equal(r.error.status, 400);
  // The allowlist has one hash per operation, all SHA-256.
  assert.deepEqual(Object.keys(HASHES).sort(), ["adjust", "intake", "plan", "week"]);
  Object.values(HASHES).forEach((h) => assert.match(h, /^[0-9a-f]{64}$/));
  const fake = createHash("sha256").update("not the real prompt").digest("hex");
  assert.ok(!Object.values(HASHES).includes(fake));
});

test("daily, monthly and global quotas stop requests with a clear error", async () => {
  const c = fakeClient(okMessage());
  const h = createHandler({ anthropicFactory: c.factory });
  const e = env({ DAILY_REQUEST_LIMIT: "2" });
  const token = await registered(h, e);
  assert.equal((await call(h, e, "POST", "/v1/ai", aiBody(), token)).status, 200);
  assert.equal((await call(h, e, "POST", "/v1/ai", aiBody(), token)).status, 200);
  const third = await call(h, e, "POST", "/v1/ai", aiBody(), token);
  assert.equal(third.status, 429);
  assert.equal(third.body.error, "quota_exceeded");
  assert.equal(c.calls.length, 2);

  const e2 = env({ MONTHLY_OUTPUT_TOKEN_LIMIT: "500" });
  const t2 = await registered(h, e2);
  assert.equal((await call(h, e2, "POST", "/v1/ai", aiBody(), t2)).status, 200);
  assert.equal((await call(h, e2, "POST", "/v1/ai", aiBody(), t2)).status, 200); // 400 tokens used < 500
  assert.equal((await call(h, e2, "POST", "/v1/ai", aiBody(), t2)).status, 429); // 800 >= 500

  const e3 = env({ GLOBAL_DAILY_REQUEST_LIMIT: "1" });
  const a = await registered(h, e3);
  const b = await registered(h, e3);
  assert.equal((await call(h, e3, "POST", "/v1/ai", aiBody(), a)).status, 200);
  const r = await call(h, e3, "POST", "/v1/ai", aiBody(), b);
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "rate_limited");
});

test("model errors, refusals and truncation map to app-readable errors", async () => {
  const cases = [
    [Object.assign(Object.create(Anthropic.RateLimitError.prototype), { status: 429 }), 429, "rate_limited"],
    [Object.assign(Object.create(Anthropic.AuthenticationError.prototype), { status: 401 }), 502, "server_misconfigured"],
    [Object.assign(Object.create(Anthropic.InternalServerError.prototype), { status: 500 }), 502, "upstream_error"],
    [okMessage("", "refusal"), 422, "refused"],
    [okMessage("{\"summ", "max_tokens"), 422, "truncated"],
  ];
  for (const [response, status, error] of cases) {
    const e = env();
    const h = createHandler({ anthropicFactory: fakeClient(response).factory });
    const token = await registered(h, e);
    const r = await call(h, e, "POST", "/v1/ai", aiBody(), token);
    assert.equal(r.status, status);
    assert.equal(r.body.error, error);
  }
});

test("usage, forget-device and admin endpoints", async () => {
  const e = env();
  const h = createHandler({ anthropicFactory: fakeClient(okMessage()).factory });
  const token = await registered(h, e);
  await call(h, e, "POST", "/v1/ai", aiBody(), token);
  const u = await call(h, e, "GET", "/v1/usage", null, token);
  assert.equal(u.body.today, 1);
  assert.equal(u.body.limits.daily_requests, 15);
  assert.equal((await call(h, e, "GET", "/v1/admin/usage", null, "wrong")).status, 401);
  const a = await call(h, e, "GET", "/v1/admin/usage?days=3", null, "admin");
  assert.equal(a.body.days.length, 3);
  assert.equal(a.body.days[0].requests, 1);
  assert.equal(a.body.days[0].output, 400);
  const f = await call(h, e, "DELETE", "/v1/device", null, token);
  assert.equal(f.body.deleted, true);
  assert.equal((await call(h, e, "GET", "/v1/usage", null, token)).status, 401);
});
