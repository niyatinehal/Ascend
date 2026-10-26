# Ascend

**Tell us what you want to achieve. AI turns it into a plan you can actually follow.**

Ascend is an Android app with a home-screen widget. You describe your goals in plain language, answer up to three quick questions, and review an AI-drafted plan: goals, milestones, a weekly routine and a first week of tasks. Each task has an **ideal** and a **minimum** version. Once you accept the plan, you follow it from the Today screen or the widget. When life changes, **Adjust my plan** proposes specific changes for you to approve.

Version 2.0 grew out of the personal *Level Up 2027* checklist. All of that app's features remain: ideal/minimum versions, low-energy days, the widget, reminders, weekly review, backup. Existing data carries over automatically.

Stack: Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Jetpack Glance (widget) · Anthropic Java SDK (own-key mode) · Cloudflare Worker + Anthropic TypeScript SDK (server mode).

- **APK:** `dist/Ascend-2.0.0.apk` (release build, R8-shrunk, about 7 MB, signed with the same key as 1.0, so it installs over it)
- **Min Android:** 8.0 (API 26) · **Target:** Android 15 (API 35)

---

## 1. Install or update

1. Copy `dist/Ascend-2.0.0.apk` to the phone and open it with Files or Downloads.
2. Allow **Install unknown apps** for that app if asked, then tap **Install**. If Play Protect warns you, choose **More details → Install anyway**.
3. **Already using 1.0?** Install over it. The database migrates in place:
   - tasks, completions, goals, progress and reviews are kept
   - the old categories map onto the new ones
   - you skip onboarding and land on Today

Back up the signing key (`keystore/levelup2027-release.jks` + `keystore.properties`). Future updates must be signed with it, or they can't install over the app without uninstalling it, which erases your data.

## 2. Turn on AI planning

AI is optional. Without it you can set up manually or load the example plan, and everything else works offline. There are two ways to connect:

| Mode | How it works | Who pays | Set up |
|---|---|---|---|
| **Ascend AI server** (default) | The phone talks to a small Cloudflare Worker that holds the API key, picks the model and enforces per-device quotas | Whoever runs the server | Deploy `worker/` (see `worker/README.md`), then set the address |
| **Own API key** | The phone calls the Claude API directly with the official Anthropic Java SDK | The key's owner, billed by Anthropic | Settings → AI → *My own Anthropic API key* → paste an `sk-ant-…` key |

**This APK has no server address built in.** Until you deploy the Worker, use own-key mode or enter a server address in Settings → AI.

Before the first AI request, the app shows what will be shared and asks you to agree once.

### Configuration and environment variables

| Where | Name | Purpose |
|---|---|---|
| `local.properties` (app build) | `ascendAiServerUrl` | Server address built into the APK (optional; can be set per phone instead) |
| `local.properties` | `sdk.dir` | Android SDK path (already set) |
| `keystore.properties` | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` | Release signing (already set, never committed) |
| Worker secret | `ANTHROPIC_API_KEY` | The server's Claude API key |
| Worker secret | `ADMIN_TOKEN` | Protects `/v1/admin/usage` |
| `worker/wrangler.toml` | KV namespace `USAGE` id | Device tokens and usage counters |
| `worker/wrangler.toml` `[vars]` | `DAILY_REQUEST_LIMIT`, `MONTHLY_REQUEST_LIMIT`, `MONTHLY_OUTPUT_TOKEN_LIMIT`, `GLOBAL_DAILY_REQUEST_LIMIT`, `REGISTRATIONS_PER_IP_PER_DAY`, `ENFORCE_PROMPT_HASHES` | Quotas and the prompt allowlist |

No API key is stored in the app's source or the APK.

## 3. The new-user journey

1. **Open the app → "What do you want to work toward?"** Type or dictate (🎤 uses the phone's speech recognizer). One goal or several, in your own words. For example: *"I want to get fit, change careers, learn Spanish, and start a small business. I work full-time and have about an hour free on weekdays."*
2. **Up to three quick questions.** AI (Claude Haiku 5.5) extracts what it can and asks only what would change the plan. Every question can be skipped.
3. **Draft plan.** Claude Sonnet 5.5 drafts it and the app validates it. The preview shows:
   - your interpreted goals, with measurable outcomes, baselines, targets and deadlines (assumed ones are labelled)
   - the weekly routine
   - the first 7 days, with ideal/minimum versions and minutes
   - time per day against your available time
   - assumptions, warnings (unrealistic goals, conflicts, capacity) and anything the app corrected
4. **Accept Plan / Edit Plan / Regenerate.**
   - *Edit* changes or removes individual goals and tasks, or the daily time budget, with no AI call.
   - *Regenerate* takes optional feedback ("lighter weekdays").
   - Nothing is added to your schedule until you press **Accept**.
5. **Today** shows your plan. Add the widget (step 5). Complete tasks as ideal or minimum.
6. **When life changes**, use **Adjust my plan** (step 4).

If a request fails (no network, quota, a bad response), you see why. What you typed is kept, because drafts are saved on the phone, and **Try again** picks up where you left off. Closing the screen keeps an unfinished draft; **Start over** discards it.

"Set up without AI instead" offers an empty plan or the example *Level Up 2027* plan.

## 4. Daily use

- **Today** answers three questions at the top:
  - **What matters most today?** Highest priority, nearest deadline, and what other tasks depend on.
  - **What should I do next?** The best open task for the current time of day that isn't waiting on another task.
  - **What's the minimum if I can't do the full task?**

  The rest of the screen:
  - **Daily time budget** (Settings): tasks that don't fit go under **If you have time**. They aren't hidden or deleted, and the screen says which goals are being favoured and whether minimum versions would fit.
  - Each card: **Ideal**, **Minimum**, **Skip** (optional reason), and **↻ Move** to another day.
  - **Low-energy day?** switches everything to minimum versions for today only.
- **Missed tasks:** nothing rolls forward automatically. A *tasks from earlier days* card suggests, for each missed one-off task, either moving it to a day with room (at most one moved task per day, never over budget) or letting it go. It explains why. History is always kept.
- **Adjust my plan** (Today and Goals). Examples: "I have only 20 minutes tonight", "I missed the last three days", "Move my workout to Saturday", "I have exams next week", "This goal is no longer important".
  - The AI proposes specific changes: move one occurrence, do the minimum version, skip today's, edit, add or pause a task, change a goal's status, deadline or priority, or change your time budget.
  - Changes to goals, deadlines or a habit's days are marked **Needs your OK**.
  - Untick anything you don't want, then **Apply selected**.
  - Optional: Settings → AI → *Apply small changes without asking*, for one-day changes only.
- **Plan next week:** near the end of each week, if you have AI-planned goals, a card offers to plan next week's dated tasks from your milestones and how this week went. One AI request, reviewed before saving. Habits continue without it.
- **Goals:**
  - goal status (active / paused / done / dropped)
  - progress bars for numeric metrics, and **Record progress**
  - milestones, and this week's outcomes
  - deadlines (an assumed deadline says so)
  - **Add goals with AI**
- **Tasks:** every task, with search and filters (category, goal, slot, date, state) and the weekly view.
- **Review:** weekly stats per goal and category, frequently skipped tasks, patterns, and the four reflection questions.
- **Settings:**
  - AI mode, key or server address, usage this month, consent
  - daily time budget, reminders, categories (rename, merge, turn off, new), widget, theme
  - backup and restore, recent plan changes, **Delete all my data**

### Rules the app keeps
- Ideal and minimum both count as done, and which one you did is recorded. Skipping is never penalised.
- Recurring tasks never pile up. Missed one-off tasks get a suggestion, not an automatic rollover.
- History is never deleted by the AI:
  - moved occurrences leave a *Moved* record
  - paused tasks are disabled, not deleted
  - completed records are never edited
- Each AI change is logged under Settings → *Recent plan changes*.

## 5. Home-screen widget

Long-press the home screen → **Widgets → Ascend → "Ascend: today"**. Configure the time slot, ideal or minimum text, whether to show completed tasks, and which categories to show (none selected means all). Resize it for a small, medium or large layout.

- **The circle** completes the task in place, then shows it as done. It uses the version the widget shows: minimum on low-energy days and for tasks planned as minimum.
- **The title** opens the task in the app.

The widget refreshes after every completion, plan change or accepted AI adjustment, and just after midnight. It never calls the AI.

## 6. Privacy and data

- Your plan, history and settings are stored **only on the phone**. Backups are a JSON file you save yourself.
- **Sent to the AI provider (Anthropic), only when you use an AI feature:**
  - what you type, and your answers
  - goal titles, deadlines and priorities
  - task titles, times and durations for the next 7 days, plus your habits
  - counts of done, minimum and skipped tasks for the last 7 days
- **Never sent:** notes, progress entries, reviews, backups, your full history.
- The AI server keeps only device tokens and request/token counters, not content.
- **Own-key mode:** the key is encrypted with an Android Keystore key, kept in a private file excluded from cloud backup and device transfer, and isn't included in JSON backups.
- **Settings → Delete all my data** erases everything on the phone, removes this device's record from the AI server, and can also remove the API key.

## 7. Cost controls

- The AI is used only where it adds value:
  - onboarding (2 requests: intake + plan; Regenerate adds one)
  - **Adjust my plan** (one request per message)
  - **Plan next week** (one request)
- Completing tasks, progress, filtering, the daily plan, missed-task suggestions, reminders and the widget all run locally.
- Structured outputs (JSON schema) mean a response is either valid or rejected, with no retry loops. The app never retries automatically.
- Each request carries a compact snapshot of the next week and a summary of the last one, never the whole history.
- Plans are revised by updating rows with stable ids, not by regenerating everything. Accepting the same plan twice adds nothing twice.
- Cheaper model where it suffices: Haiku 5.5 for intake, Sonnet 5.5 for planning and adjustments. Static system prompts use prompt caching.
- **Own-key mode:** a local monthly request cap (default 60) and a usage line in Settings with a cost estimate at list prices.
- **Server mode:** per-device daily and monthly limits, a monthly output-token limit, a server-wide daily cap, an admin usage endpoint, and request logs without content (`worker/README.md`).
- Users with their own key are told that Anthropic bills them.

## 8. How the AI integration is built

```
app/src/main/java/app/levelup2027/ai/
  AiClient.kt        AiOperation table (model, effort, max tokens), AiClient interface,
                     AnthropicDirectClient (official Java SDK), ProxyAiClient (Ascend server), error types
  AiService.kt       consent / configuration / quota checks, usage logging, parsing; encrypted SecretStore
  Prompts.kt         static system prompts (cacheable)
  AiModels.kt        typed plan / intake / week / adjust models and their JSON Schemas
  ContextBuilder.kt  compact, purpose-specific request payloads
  PlanValidator.kt   deterministic checks and repairs: dates, duplicates, dependencies (cycles, order),
                     minimum ≤ ideal, capacity rebalancing and warnings, category resolution, limits
  PlanMaterializer.kt  validated draft → Room rows with stable ids; duplicate-safe; nothing deleted
  ChangeValidator.kt typed, validated plan changes; marks major changes; never touches history
app/src/main/java/app/levelup2027/domain/DailyPlanner.kt   "what matters today", capacity fitting, missed-task policy
worker/              Cloudflare Worker (Anthropic TypeScript SDK): key custody, model choice, quotas, prompt allowlist
```

The language model is used only to understand language and propose plans or changes. Everything else is deterministic code: storage, dates, recurrence, capacity arithmetic, de-duplication, validation, history and quotas. The provider sits behind `AiClient`, so it can be swapped.

## 9. Test and build status

Built on this machine (OpenJDK 17, Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, SDK 35, Node 20):

| Check | Result |
|---|---|
| `./gradlew :app:assembleRelease` | ✅ signed APK (same key as 1.0), R8 shrinking |
| `./gradlew :app:testDebugUnitTest` | ✅ **101 tests, 0 failures** |
| `./gradlew :app:lintDebug` | ✅ 0 errors (14 warnings, same kinds as 1.0: newer library versions available, API-31-only widget attributes, one large vector preview, the v26 icon folder) |
| `cd worker && npm test` | ✅ **8 tests, 0 failures** |
| Worker bundle (esbuild with `nodejs_compat` externals, as wrangler does) | ✅ bundles, prompt allowlist embedded |
| APK inspection | ✅ signature verifies; permissions: internet (AI only), notifications, boot-completed, wake-lock |

App test suites (`app/src/test`):
- **MigrationTest (3):**
  - builds a real SQLite database from the exported v1 schema with v1 data
  - runs the migration and checks every table, column type, NOT NULL, default, primary key and index against the exported v2 schema
  - checks the data survived and was upgraded
  - v1 backup files restore with categories mapped
- **PlanValidatorTest (10):**
  - out-of-week, past or invalid dates
  - duplicates, including a dated copy of a habit
  - minimum ≤ ideal; missing minimums; rest tasks
  - dependency cycles and ordering
  - capacity rebalancing and over-capacity warnings
  - past deadlines, milestone clamping, goal limits
  - category resolution
  - next-week de-duplication
- **PlanMaterializerTest (3):** stable unique ids, recurrence rules, active windows, milestone and dependency links, only stated baselines become progress, accepting twice adds nothing, existing goals are reused.
- **ChangeValidatorTest (7):**
  - simple vs major changes
  - history can't be changed; unknown ids are rejected
  - horizon and past dates
  - task edits are clamped; no-op edits are rejected
  - new tasks are validated and de-duplicated
  - one change per occurrence
- **DailyPlannerTest (7):**
  - priority and time-of-day choice
  - budget fitting and trade-off text
  - done work counts; low energy uses minimum minutes
  - blocked dependencies; paused goals
  - missed-task suggestions (one per day, budget-aware, minimum-only fallback, let go)
- **RepositoryPlanTest (10):**
  - fresh-install defaults
  - the v1 → v2 settings upgrade (onboarding skipped, disabled categories kept)
  - rescheduling one-time and recurring tasks with history kept; can't move completed work
  - minimum overrides on the widget
  - applying AI changes without deleting anything
  - accepting a plan
  - merging categories
  - erasing all data
- **AiClientTest (7):**
  - the **real Anthropic Java SDK** against a local mock HTTP server: exact request (model, `output_config` schema and effort, adaptive thinking, cached system prompt, `fallbacks` and beta header), usage parsing, mapping of 401, refusal, max_tokens and 400
  - the server client: payload, token, re-registering on 401, error mapping
  - every JSON schema follows the structured-output rules
- **AiServiceTest (6):** availability states, no request without consent, usage logging, own-key monthly cap, at most 3 questions, unreadable output, network failure, adjust context limited to one week and excluding notes.
- **PromptHashesTest (1):** the server's prompt allowlist matches the app's prompts.
- **Earlier suites, updated:** ScheduleEngineTest (14), ProgressAndWidgetTest (10), SeedTest (12), RepositoryTest (11).

### Tested vs. not yet tested

| Feature | Status |
|---|---|
| Plan validation, de-duplication, capacity, dependencies, change validation, daily planner, missed-task policy, rescheduling, completion history, migration, backup compatibility, widget state | ✅ unit-tested |
| Request format and error handling of the SDK client and the server client | ✅ tested against local mock servers |
| Worker routing, validation, quotas, prompt allowlist, error mapping | ✅ `npm test` |
| **Real calls to the Claude API** (plan quality, latency, actual cost) | ❌ **not tested**: no API key was available on this machine |
| **Worker deployed on Cloudflare** | ❌ not deployed |
| **The app running on a phone or emulator** (all screens, widget, voice input, own-key requests inside the R8-minified release build) | ❌ **not tested**: no device connected, and too little disk for an emulator |

### Testing AI with a real key

1. Install the APK.
2. Settings → AI → *My own Anthropic API key* → paste a key.
3. Goals → *Add goals with AI*, or start fresh with Settings → *Delete all my data* to go through onboarding.

Usage appears under Settings → AI.

If own-key requests fail only in the release APK with a class or reflection error, the cause is the R8 keep rules for the SDK (`app/proguard-rules.pro`). Install a debug build (`./gradlew :app:assembleDebug`) to confirm.

## 10. Known limitations

- **Not yet run on a real device**, and **AI not yet tried with a real key** (see above).
- **Release APK size grew from 2.5 to 7 MB**, mostly the Anthropic SDK and Jackson, which R8 must keep for reflection.
- **AI quality depends on the model.** The app validates structure, dates, capacity and duplicates, but it can't judge whether a suggested exercise or study method suits you. Plans are drafts for you to review.
- **Server quotas are approximate** under concurrent use (Workers KV), and device registration is anonymous. Both are fine for a small group, but should be hardened before a public launch (`worker/README.md`).
- **Weeks after the first are planned on request** (**Plan next week**), not automatically. Habits continue on their own regardless.
- **Exact clock times** are only shown on cards. Reminders are still per time slot, not per task.
- **Widget settings are shared** by all Ascend widgets. **One resizable widget**, not three separate entries.
- **Progress values are recorded for today's date**, and **restore replaces** rather than merges.
- **The example plan** (*Level Up 2027*) is still offered under *Set up without AI* and Settings, and contains its original personal targets.

## 11. Back up, restore, edit

- **Backup:** Settings → Your data → **Export backup (JSON)** saves tasks, completions, goals, milestones, progress, reviews, categories, weekly outcomes, overrides, the change log and settings. **Restore** replaces everything on the phone. Version 1 backups are accepted.
- **Edit the plan by hand:** Tasks → tap a task → edit days, slot, ideal/minimum, minutes, priority, goal and category → **Save**. Turn off **Enabled** to pause a task. Use **+** to add one. Categories are managed in Settings.

## 12. Build from source

```bash
cd levelup-2027
./gradlew :app:testDebugUnitTest      # app tests
./gradlew :app:assembleRelease        # → app/build/outputs/apk/release/app-release.apk
cd worker && npm install && npm test  # server tests
```

Disk note: the Android SDK (`~/Android/Sdk`, about 0.6 GB), the Gradle cache (`~/.gradle`) and the npm/npx caches (`~/.npm`) were used for these builds. All are safe to delete and will re-download.

```
app/src/main/java/app/levelup2027/
  ai/        AI layer (see section 8)
  data/      Entities (tasks, goals, categories, outcomes, overrides, drafts, AI usage, change log),
             Database (Room v2 + Migration1to2), SettingsStore, Repository, Seed (example plan), Backup
  domain/    Schedule (recurrence, occurrences), Progress, WidgetState, DailyPlanner (focus + missed-task policy)
  widget/    Glance widget + configuration
  notify/    reminders
  ui/        AppRoot, AppViewModel, Catalog, plan/ (Build my plan, Adjust, Next week, AI settings),
             today/, tasks/, goals/, review/, settings/, components/, theme/
app/schemas/ Room schema history (v1, v2)
worker/      Ascend AI server (Cloudflare Worker)
```
