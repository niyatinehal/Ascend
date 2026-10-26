# Ascend AI server (Cloudflare Worker)

A small server between the Ascend app and the Claude API. It exists so that:

- **The Anthropic API key never ships in the app.** It is stored as a Worker secret.
- **The server, not the phone, picks the model and output budget** for each operation (`src/operations.js`).
- **Each device has quotas** (per day and per month, plus a server-wide daily cap), so one user or a leaked token can't run up the bill.
- **Only the app's own prompts are accepted.** The server checks the SHA-256 of the system prompt against `src/prompt-hashes.json`, so it can't be used as a general Claude proxy.

It stores no plans, prompts or answers. It stores only device tokens and request/token counters in Workers KV. Counters expire after about 40 days. A network address is kept only as a truncated hash, for one day, to rate-limit new registrations.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/register` | Issues an anonymous device token (rate-limited per network) |
| POST | `/v1/ai` | One structured-output Claude call: `{op, system, messages, schema}` → `{output, model, usage}` |
| GET | `/v1/usage` | This device's usage and limits |
| DELETE | `/v1/device` | Forgets this device. The app calls this from **Settings → Delete all my data** |
| GET | `/v1/admin/usage?days=14` | Per-day totals for monitoring (`Authorization: Bearer <ADMIN_TOKEN>`) |

### Operations

| `op` | Model | Effort | Max output | Used for |
|---|---|---|---|---|
| `intake` | Claude Haiku 5.5 | low | 4,000 | Understanding the first message and asking up to 3 questions |
| `plan` | Claude Sonnet 5.5 | medium | 16,000 | Generating or regenerating a full draft plan |
| `week` | Claude Sonnet 5.5 | low | 8,000 | Planning next week's dated tasks |
| `adjust` | Claude Sonnet 5.5 | low | 6,000 | "Adjust my plan" requests |

All operations use adaptive thinking and JSON-schema structured outputs. The system prompt is sent with `cache_control`; it is identical for every user, so repeated requests can read it from the prompt cache. The Sonnet operations also send `fallbacks: "default"` (beta `server-side-fallback-2026-07-01`), so a safety-classifier refusal is retried server-side on Anthropic's recommended fallback model.

## Set up (about 10 minutes)

You need a free Cloudflare account and an Anthropic API key from console.anthropic.com.

```bash
cd worker
npm install
npx wrangler@4 login
npx wrangler@4 kv namespace create USAGE         # copy the printed id into wrangler.toml
npx wrangler@4 secret put ANTHROPIC_API_KEY      # paste your sk-ant-... key
npx wrangler@4 secret put ADMIN_TOKEN            # any long random string, for /v1/admin/usage
npx wrangler@4 deploy                            # prints https://ascend-ai.<you>.workers.dev
```

Then point the app at it, using either option:

- **Build it in:** add `ascendAiServerUrl=https://ascend-ai.<you>.workers.dev` to `local.properties` in the project root and rebuild the APK.
- **Per phone:** Settings → AI → *Ascend AI server* → *Server address*.

Check it's up: `curl https://ascend-ai.<you>.workers.dev/` returns `{"ok":true,...}`.

## Limits and cost control

Set these in `wrangler.toml` under `[vars]`, then redeploy:

| Variable | Default | Meaning |
|---|---|---|
| `DAILY_REQUEST_LIMIT` | 15 | AI requests per device per UTC day |
| `MONTHLY_REQUEST_LIMIT` | 120 | AI requests per device per calendar month |
| `MONTHLY_OUTPUT_TOKEN_LIMIT` | 400000 | Output tokens per device per month |
| `GLOBAL_DAILY_REQUEST_LIMIT` | 2000 | All devices together, per UTC day |
| `REGISTRATIONS_PER_IP_PER_DAY` | 5 | New devices per network per day |
| `ENFORCE_PROMPT_HASHES` | true | Reject system prompts not in `src/prompt-hashes.json` |

Also set a **monthly spend limit** in the Anthropic Console. It is the hard backstop.

A typical new user makes 2 onboarding requests (intake + plan), then perhaps one "plan next week" and a few adjustments a week. Ticking tasks, the widget, reminders and the daily plan never call the AI.

To monitor usage:
- `curl -H "Authorization: Bearer $ADMIN_TOKEN" https://…/v1/admin/usage?days=30` gives per-day requests and tokens.
- `npx wrangler@4 tail` streams one JSON log line per request (operation, model, tokens, latency; no content).
- The Anthropic Console shows cost.

## When you change a prompt

The app's prompts live in `app/src/main/java/app/levelup2027/ai/Prompts.kt`. After editing them:

```bash
WRITE_PROMPT_HASHES=1 ./gradlew :app:testDebugUnitTest --tests '*PromptHashesTest*'   # rewrites src/prompt-hashes.json
cd worker && npx wrangler@4 deploy
```

Deploy the server **before** releasing the app. Old app versions keep working only if you keep their hashes in the file. The file is a map from operation to hash, so you would need to extend it to a list per operation.

## Tests

```bash
npm test     # node --test, with a fake KV and a fake Claude client
```

These tests cover routing, validation, the prompt allowlist, every quota, error mapping, refusal/truncation, and the usage, forget and admin endpoints.

## Not production-hardened yet

- **Device tokens are anonymous.** Anyone can register a token, limited per network and by the global cap. For a public launch, add Play Integrity checks or user accounts.
- **KV is eventually consistent**, so quotas can be exceeded by a few requests under concurrent use. Durable Objects would make them exact.
- **This Worker has not been deployed yet.** Its logic is covered by `npm test`, and it bundles with esbuild using `nodejs_compat`, but it has not run on Cloudflare.
