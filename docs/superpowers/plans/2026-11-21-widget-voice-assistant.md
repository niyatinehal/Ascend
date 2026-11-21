# Widget Voice Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A mic on the home-screen widget opens a small assistant window that listens, proposes changes (presets, today's or saved blocks, completions, plus everything Adjust already does), and applies only what the user confirms.

**Architecture:** The assistant *is* "Adjust my plan" in a translucent activity: `AdjustSheet` + `AdjustViewModel` are reused with an assistant mode (auto-listen, always confirm). The Adjust AI operation gains routine context and four change types (`SET_PRESET`, `SET_TODAY_BLOCKS`, `SET_PRESET_BLOCKS`, `COMPLETE_TASK`), validated in `ChangeValidator` and applied in `Repository.applyChanges`. Today-only blocks are one `todayOverride` in `RoutineSettings`, read through `Routine.blocksFor`, so Today, check-ins and backups follow automatically.

**Tech Stack:** Kotlin, Jetpack Compose M3, Glance, DataStore + kotlinx.serialization, system speech recognizer, JUnit4 + coroutines-test (`TestEnv`), Robolectric/Roborazzi screenshot walkthrough.

**Spec:** `docs/superpowers/specs/2026-11-21-widget-voice-assistant-design.md`

## Global Constraints

- Nothing changes until the user taps Apply — in the assistant window always (ignores "auto-apply simple changes"); in the in-app Adjust sheet the four new change types are `major = true`.
- Block changes are today-only by default; saved preset changes only when the user says "permanently", "for good", "every day" or "from now on".
- One today-only override kept (date, preset, blocks); a past-date override is ignored.
- Blocks validated with `Routine.validate` (start < end, no overlap, within a day, label 1–40 chars).
- An existing completion is never undone; a skipped occurrence may become a completion.
- Context sent to the AI: existing Adjust context plus preset names, block times/labels, active preset, today's blocks. No notes.
- Prompt change ⇒ regenerate `worker/src/prompt-hashes.json` (`WRITE_PROMPT_HASHES=1`) and redeploy the worker before release.
- Ships in the same 2.4.0 release as routines (no version bump).
- Build with `JAVA_HOME=~/.jdks/jdk-21.0.13+9`; tests: `./gradlew :app:testDebugUnitTest --offline -q --tests '<class>'`.

## Review Focus

1. **"Switch to Lazy Day and move gym to 6–8" in one request** → the block change lands on Lazy Day's today blocks, not Daily's. (Task 2 test `today blocks follow a preset switch in the same request`.)
2. **A today-only change made yesterday** → ignored today; today's check-ins follow today's override. (Task 1 tests `yesterday's override is ignored`, `check-ins follow today's override`.)
3. **"I finished the gym" when it's already done** → rejected with a message; the completion is untouched. (Task 2 test `completing an already-done task is rejected`.)
4. **AI sends overlapping or unreadable blocks** → that change is rejected with the reason; other changes still offered. (Task 2 test `invalid blocks are rejected with the reason`.)
5. **Assistant used with auto-apply turned on** → still shows the proposal and waits. (Task 4 test `assistant never auto-applies`.)

---

### Task 1: Today-only blocks in the routine model

**Files:**
- Modify: `app/src/main/java/app/levelup2027/domain/Routine.kt`
- Modify: `app/src/main/java/app/levelup2027/data/Repository.kt`
- Test: `app/src/test/java/app/levelup2027/RoutineTest.kt`, `RepositoryRoutineTest.kt`, `CheckInSchedulerTest.kt`

**Interfaces — Produces:**
```kotlin
@Serializable data class DayOverride(@Serializable(with = LocalDateSerializer::class) val date: LocalDate, val kind: PresetKind, val blocks: List<Block>)
// RoutineSettings gains:  val todayOverride: DayOverride? = null
// Routine.blocksFor(r, date): override.blocks when override.date == date && override.kind == activeKind(r, date), else presets[activeKind]
suspend fun Repository.setTodayBlocks(today: LocalDate, kind: PresetKind, blocks: List<Block>): List<String>  // validate; store normalized; SETTINGS change
```

- [ ] **Step 1: Failing tests**
  - RoutineTest: `today's override replaces the preset's blocks`; `yesterday's override is ignored`; `override only shows for its preset` (active LAZY today, override for DAILY → Lazy's saved blocks; switch back to DAILY → override).
  - RepositoryRoutineTest: `today-only blocks are validated and stored for that preset` (invalid → errors, nothing stored); `saving a preset keeps today's override`; `a new today-only change replaces the old one`.
  - CheckInSchedulerTest: `check-ins follow today's override` (Daily 05–07 Gym saved, override 06–08 Gym → armed 08:00).
- [ ] **Step 2: Run, expect FAIL** (unresolved `DayOverride` / `setTodayBlocks`).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run RoutineTest, RepositoryRoutineTest, CheckInSchedulerTest, SettingsMigrationTest → PASS.**
- [ ] **Step 5: Commit** `feat(assistant): today-only routine blocks`

---

### Task 2: New change types — validate and apply

**Files:**
- Modify: `app/src/main/java/app/levelup2027/ai/AiModels.kt` (`ChangeTypes`, `ChangeOp`)
- Modify: `app/src/main/java/app/levelup2027/ai/ChangeValidator.kt` (`PlanChange`, `PlanState`, `validate`)
- Modify: `app/src/main/java/app/levelup2027/data/Repository.kt` (`applyChanges`)
- Test: `app/src/test/java/app/levelup2027/ChangeValidatorTest.kt`, create `app/src/test/java/app/levelup2027/AssistantChangesTest.kt`

**Interfaces:**
- Consumes: Task 1 `DayOverride`, `setTodayBlocks` semantics; `RoutineResult.from(RoutineDraft)` (2.4) for parsing blocks; `Routine.activeKind`.
- Produces:
```kotlin
// ChangeTypes: SET_PRESET, SET_TODAY_BLOCKS, SET_PRESET_BLOCKS, COMPLETE_TASK (added to ALL)
// ChangeOp gains: val preset: String = "", val blocks: List<DraftBlock> = emptyList(), val completion: String = ""
// PlanState gains: val routine: RoutineSettings = RoutineSettings()   (default keeps existing callers compiling)
sealed PlanChange additions (all major = true):
  SetPreset(val kind: PresetKind, reason)                         summary "Switch today to ${kind.label}"
  SetTodayBlocks(val kind: PresetKind, val blocks: List<Block>, reason)   summary "Today only (${kind.label}): " + blocks joined "5:00–7:00 Gym"
  SetPresetBlocks(val kind: PresetKind, val blocks: List<Block>, reason)  summary "${kind.label}, from now on: " + same
  CompleteTask(val task: Task, val date: LocalDate, val type: CompletionType?, reason)  summary "Mark \"${task.title}\" done" (+ " (minimum)" when MINIMUM)
```
Validation rules:
- `preset` matches `PresetKind` name or label, case-insensitive ("lazy", "Lazy Day", "productive" → PRODUCTIVE). Unknown → reject.
- SET_PRESET: reject when it's already today's preset.
- SET_TODAY_BLOCKS: kind = op.preset if given, else the kind of a SET_PRESET in the same batch, else today's active kind. Blocks parsed with `RoutineResult.from`; any problem → reject with the first problem. Empty list allowed (clears today).
- SET_PRESET_BLOCKS: preset required; same parsing.
- COMPLETE_TASK: task known, `ScheduleEngine.isScheduledOn(task, today)`; existing done completion → reject "already done"; skipped is fine. completion "MINIMUM"/"IDEAL" → that type, else null.
- Clashes: for SET_TODAY_BLOCKS / SET_PRESET_BLOCKS keep the **last** per kind; one COMPLETE_TASK per task.
Apply (in the order given):
- SetPreset → same rules as `Repository.setPreset` (share a private helper).
- SetTodayBlocks → `todayOverride = DayOverride(today, kind, normalized)`.
- SetPresetBlocks → `presets + (kind to normalized)`.
- CompleteTask → `complete(task, date, type ?: usual rule)`; usual rule = low-energy day, `defaultMode == MINIMUM`, or per-day prefer-minimum → MINIMUM, else IDEAL.

- [ ] **Step 1: Failing validator tests** (state with routine: Daily 05–07 Gym, 09–10 Read; Lazy empty; active Daily):
  - `preset names are understood` ("lazy", "Lazy Day", "PRODUCTIVE")
  - `switching to the current preset is rejected`
  - `today blocks default to today's preset`
  - `today blocks follow a preset switch in the same request`
  - `invalid blocks are rejected with the reason` (overlap; "9am") — rejected list mentions it, other valid op in the batch still accepted
  - `permanent blocks need a preset`
  - `the later block change for a preset wins`
  - `completing an already-done task is rejected`
  - `a skipped task can be completed`
  - `completing a task not scheduled today is rejected`
  - `new change types always need approval` (all `major`, `allSimple` false)
- [ ] **Step 2: Failing apply tests in `AssistantChangesTest`** (`TestEnv`):
  - `switching to Lazy through the assistant turns on minimums`
  - `today-only blocks don't change the saved preset`
  - `permanent blocks change the saved preset`
  - `completing uses minimum on a low-energy day and replaces a skip`
  - `completing as minimum when asked`
  - `applied changes are logged and re-arm alarms` (change log entry; `ChangeKind.SETTINGS` in `env.changes`)
- [ ] **Step 3: Run, expect FAIL.**
- [ ] **Step 4: Implement.**
- [ ] **Step 5: Run ChangeValidatorTest, AssistantChangesTest, RepositoryRoutineTest → PASS.**
- [ ] **Step 6: Commit** `feat(assistant): preset, block and completion changes`

---

### Task 3: Teach the Adjust AI about routines

**Files:**
- Modify: `app/src/main/java/app/levelup2027/ai/ContextBuilder.kt` (`AdjustInput`, `adjust(...)`)
- Modify: `app/src/main/java/app/levelup2027/ai/AiModels.kt` (`Schemas.adjust`)
- Modify: `app/src/main/java/app/levelup2027/ai/Prompts.kt` (`ADJUST`)
- Modify: `worker/src/prompt-hashes.json` (regenerated)
- Test: `AiServiceTest.kt`, `PromptHashesTest.kt`

**Interfaces — Produces:**
```kotlin
@Serializable data class BlockSnap(val start: String, val end: String, val label: String)   // "HH:mm", end may be "24:00"
@Serializable data class RoutineSnap(val activePreset: String, val now: String, val todayBlocks: List<BlockSnap>, val todayIsOverride: Boolean, val presets: Map<String, List<BlockSnap>>)
// AdjustInput gains: val routine: RoutineSnap? = null
fun ContextBuilder.adjust(..., conversation: List<ChatTurn>, routine: RoutineSettings = RoutineSettings()): String   // new last param, default
```
Schema: change items gain `preset` (string), `blocks` (array of {start,end,label}), `completion` (string, enum "", "IDEAL", "MINIMUM").
Prompt additions (to the change-type list and rules):
- SET_PRESET: preset = DAILY | PRODUCTIVE | LAZY ("laid back", "easy day" → LAZY; "push hard" → PRODUCTIVE).
- SET_TODAY_BLOCKS: the complete new block list for today (start, end "HH:mm", label); default for any block change. preset only when changing a preset other than today's.
- SET_PRESET_BLOCKS: the complete new saved list for preset; only when the person says permanently / for good / every day / from now on.
- COMPLETE_TASK: task_id of today's occurrence; completion MINIMUM only if they said they did the minimum.
- Questions about the day ("what's left", "what's next") → answer in reply, no changes.
- Requests outside the plan, routine, goals and tasks → say politely that you can only help with those; no changes.

- [ ] **Step 1: Failing tests** in AiServiceTest:
  - `adjust context includes the routine without notes` — JSON has `"activePreset":"LAZY"`, today's override blocks with `"todayIsOverride":true`, and no task notes.
  - `adjust understands routine changes` — Scripted reply with SET_TODAY_BLOCKS + blocks + COMPLETE_TASK completion "MINIMUM" parses into ChangeOp fields.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Regenerate hashes** — `WRITE_PROMPT_HASHES=1 ./gradlew :app:testDebugUnitTest --offline -q --tests 'app.levelup2027.PromptHashesTest' --rerun`, rerun without the variable → PASS; `cd worker && npm test` → 8/8.
- [ ] **Step 5: Run AiServiceTest, PromptHashesTest → PASS.**
- [ ] **Step 6: Commit** `feat(assistant): adjust AI knows presets and blocks` (body: redeploy the worker).

---

### Task 4: Assistant mode in Adjust

**Files:**
- Modify: `app/src/main/java/app/levelup2027/ui/plan/PlanViewModels.kt` (`AdjustViewModel`)
- Modify: `app/src/main/java/app/levelup2027/ui/plan/AdjustSheet.kt` (`AdjustSheet`)
- Test: create `app/src/test/java/app/levelup2027/AdjustDecisionTest.kt`

**Interfaces:**
- Consumes: Task 3 `ContextBuilder.adjust(..., routine)`; Task 2 `PlanState(routine = …)`.
- Produces:
```kotlin
class AdjustViewModel(private val c: AppContainer, private val alwaysConfirm: Boolean = false)
/** Pure: auto-apply only when the user allowed it, it's not the assistant, nothing needs clarifying, and every change is simple. */
fun shouldAutoApply(autoApplySimple: Boolean, alwaysConfirm: Boolean, validated: ValidatedChanges, needsClarification: Boolean): Boolean
@Composable fun AdjustSheet(vm: AppViewModel, adjust: AdjustViewModel, onDismiss: () -> Unit, onOpenAiSettings: () -> Unit,
    title: String = "Adjust my plan", intro: String = <existing text>, listenOnOpen: Boolean = false, onApplied: () -> Unit = {})
```
`send()` passes `repo.settings.current().routine` to both `ContextBuilder.adjust` and `PlanState`. With `listenOnOpen`, the sheet starts voice input once on first composition; a voice result is sent immediately (`adjust.send(text)`). If no recognizer exists (`rememberVoiceInput` returns null), the text field gets focus. `onApplied` fires after a successful apply.

- [ ] **Step 1: Failing tests** in AdjustDecisionTest: `assistant never auto-applies`; `simple changes auto-apply when allowed`; `major changes never auto-apply`; `clarifying questions never auto-apply`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** (`shouldAutoApply` top-level in PlanViewModels.kt; `send` uses it).
- [ ] **Step 4: Run AdjustDecisionTest + full suite → PASS.**
- [ ] **Step 5: Commit** `feat(assistant): assistant mode for adjust`

---

### Task 5: Widget mic and assistant window

**Files:**
- Create: `app/src/main/java/app/levelup2027/ui/assistant/AssistantActivity.kt`
- Create: `app/src/main/res/drawable/ic_mic.xml`
- Modify: `app/src/main/java/app/levelup2027/widget/LevelUpWidget.kt` (`Header`)
- Modify: `app/src/main/java/app/levelup2027/MainActivity.kt` (`handleIntent`: `"settings"` → `Tab.SETTINGS`)
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml` (`Theme.LevelUp.Assistant`: translucent, transparent background, no title, `windowIsFloating=false`)
- Modify: `app/src/test/java/app/levelup2027/screenshots/RoutineScreenshots.kt` (one more shot)

**Interfaces:** Consumes Task 4 `AdjustViewModel(c, alwaysConfirm = true)` and `AdjustSheet(..., title = "Ascend assistant", intro = "Say what you'd like to change. Nothing happens until you tap Apply.", listenOnOpen = true, onApplied = …)`.

- [ ] **Step 1: Implement `AssistantActivity`** — `excludeFromRecents`, own `taskAffinity`, `launchMode="singleTask"`, not shown on lock screen. Hosts `AppViewModel` + `AdjustViewModel(alwaysConfirm = true)` and the sheet in `LevelUpTheme`. `onDismiss` → `finish()`; `onApplied` → `WidgetUpdater.refresh`, finish after 1.5 s so "Done: …" is readable; `onOpenAiSettings` → `MainActivity` with `EXTRA_OPEN_TAB = "settings"`, then finish.
- [ ] **Step 2: Widget** — mic `Image(ic_mic, contentDescription = "Ask Ascend")` in `Header` on every size (before refresh when present), `clickable(actionStartActivity<AssistantActivity>())`.
- [ ] **Step 3: Screenshot** — add `16-assistant-open`: launch `AssistantActivity` (Robolectric has no speech recognizer, so the text field shows). A proposal can't be rendered without a live AI call, so only the open window is captured. Run with `-PscreenshotDir=…` and inspect.
- [ ] **Step 4: Verify** — `./gradlew :app:compileDebugKotlin` and full suite PASS; screenshot 16 shows title, intro, text field.
- [ ] **Step 5: Commit** `feat(assistant): widget mic opens the assistant`

---

### Task 6: Docs and release check

**Files:** `README.md` (Daily use → "Voice assistant on the widget" section: what it does, today-only vs "permanently", always confirms, needs AI, redeploy worker).

- [ ] **Step 1:** Write the section.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --offline -q` PASS; `./gradlew :app:assembleRelease --offline -q` PASS.
- [ ] **Step 3: Commit** `docs: voice assistant in README`
