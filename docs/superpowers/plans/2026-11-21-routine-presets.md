# Routine Presets & Block Check-ins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three timeline presets (Daily Routine / Extreme Productive / Lazy Day) made of time blocks, switchable any time, with a full-screen check-in at each block end where the user ticks off today's tasks.

**Architecture:** Presets live in `AppSettings` (DataStore JSON — already in backups, so no Room migration). Pure logic in `domain/Routine.kt` (validation, active preset, current/next block, task ordering). One exact alarm is armed for the next block end in today's active preset; it posts a high-priority full-screen-intent notification that opens a dedicated `CheckInActivity`. Onboarding is gated by a `RoutineSetup` state so every existing onboarding exit path flows into the "Your day" step without touching each path.

**Tech Stack:** Kotlin, Jetpack Compose M3, DataStore + kotlinx.serialization, AlarmManager, NotificationCompat, JUnit4 + kotlinx-coroutines-test (existing `TestEnv` fakes).

**Spec:** `docs/superpowers/specs/2026-11-21-routine-presets-design.md`

## Global Constraints

- Preset names, fixed: "Daily Routine", "Extreme Productive", "Lazy Day". No add/delete/rename.
- Every day starts on Daily Routine; a switch lasts for today only.
- Lazy Day ⇒ low-energy on for today; leaving Lazy Day ⇒ low-energy off. The Today low-energy toggle stays independent.
- Blocks: start < end, no overlap, within one calendar day (end may be exactly midnight), label non-blank, max 40 chars.
- Check-in timing: ≤ ~2 min late acceptable, > ~10 min not.
- Ticking in the check-in = ticking on Today (same `Completion` rows, same ideal/minimum rule).
- Only one check-in notification at a time (newer replaces older); never re-shown on app open.
- No "draw over other apps". Banner while phone in use is accepted.
- Backups from 2.3 must restore and must not lock the user into the setup screen.
- Min SDK 26, target 35. Release version 2.4.0 (versionCode 6).
- Test command: `./gradlew :app:testDebugUnitTest --tests '<class>'`; full suite `./gradlew :app:testDebugUnitTest`.

## Review Focus

1. **Restoring a 2.3 backup** → user lands on Today with the one-time routine prompt, not a forced setup screen. (Task 1 test `restored 2_3 settings get the prompt, not the forced setup`.)
2. **Switching preset mid-block** → the new preset's block containing *now* still checks in at its end; blocks that ended earlier don't. (Task 3 test `switching mid-block arms the current block's end`.)
3. **A block ending at midnight / a late alarm after midnight** → the check-in shows the tasks of the block's own date, not the new day's. (Task 3 test `midnight end carries the block's date`; Task 4 reads date from the intent.)
4. **Editing a preset or switching while an alarm is armed** → the alarm is re-armed (settings change triggers `scheduleAll`). (Task 2 test `saving a preset or switching reports a settings change`.)
5. **Turning the Today low-energy toggle off while on Lazy Day** → preset stays Lazy Day, minimums off; switching back to Daily doesn't re-enable anything. (Task 2 test `low-energy toggle is independent of the preset`.)

---

### Task 1: Routine model, pure logic, settings migration

**Files:**
- Create: `app/src/main/java/app/levelup2027/domain/Routine.kt`
- Modify: `app/src/main/java/app/levelup2027/data/SettingsStore.kt` (add `routine` field to `AppSettings`, `CURRENT_VERSION = 5`, v4→5 step in `migrated()`)
- Test: `app/src/test/java/app/levelup2027/RoutineTest.kt`, `app/src/test/java/app/levelup2027/SettingsMigrationTest.kt`

**Interfaces — Produces:**
```kotlin
// domain/Routine.kt
@Serializable enum class PresetKind(val label: String) { DAILY("Daily Routine"), PRODUCTIVE("Extreme Productive"), LAZY("Lazy Day") }
@Serializable enum class RoutineSetup { REQUIRED, PROMPT, DONE }
/** Minutes since local midnight; endMinute may be 1440 (ends at midnight). */
@Serializable data class Block(val startMinute: Int, val endMinute: Int, val label: String)
@Serializable data class RoutineSettings(
    val presets: Map<PresetKind, List<Block>> = emptyMap(),
    val activeKind: PresetKind = PresetKind.DAILY,
    @Serializable(with = LocalDateSerializer::class) val activeDate: LocalDate? = null,
    val checkInsEnabled: Boolean = true,
    val setup: RoutineSetup = RoutineSetup.REQUIRED,
    val promptDismissed: Boolean = false,
)
object Routine {
    const val MAX_LABEL = 40
    fun validate(blocks: List<Block>): List<String>          // empty = valid; messages are user-facing
    fun normalized(blocks: List<Block>): List<Block>          // trimmed labels, sorted by start
    fun activeKind(r: RoutineSettings, date: LocalDate): PresetKind   // activeKind if activeDate == date else DAILY
    fun blocksFor(r: RoutineSettings, date: LocalDate): List<Block>
    fun currentBlock(blocks: List<Block>, minute: Int): Block?        // start <= minute < end
    fun nextEnding(blocks: List<Block>, minute: Int): Block?          // first block with end > minute
    fun orderForBlock(block: Block, occ: List<Occurrence>, categoryNames: Map<String, String>): List<Occurrence>
    fun format(minute: Int): String                                    // "5:00", "19:30", "0:00" for 1440
}
// AppSettings
val routine: RoutineSettings = RoutineSettings()
```

- [ ] **Step 1: Write failing tests in `RoutineTest`**

```kotlin
private fun b(s: String, e: String, l: String) = Block(min(s), min(e), l) // helper "05:00" -> 300, "24:00" -> 1440

@Test fun `valid preset passes`() = assertEquals(emptyList<String>(), Routine.validate(listOf(b("05:00","07:00","Gym"), b("07:00","08:00","Breakfast"))))
@Test fun `overlap is rejected`() = assertTrue(Routine.validate(listOf(b("05:00","07:00","Gym"), b("06:30","08:00","Run"))).isNotEmpty())
@Test fun `end before or equal to start is rejected`() { assertTrue(Routine.validate(listOf(b("07:00","07:00","X"))).isNotEmpty()); assertTrue(Routine.validate(listOf(b("23:00","00:00","Late"))).isNotEmpty()) }
@Test fun `block ending at midnight is allowed`() = assertEquals(emptyList<String>(), Routine.validate(listOf(b("22:00","24:00","Wind down"))))
@Test fun `blank or over-long label is rejected`() { assertTrue(Routine.validate(listOf(b("05:00","06:00","  "))).isNotEmpty()); assertTrue(Routine.validate(listOf(b("05:00","06:00","x".repeat(41)))).isNotEmpty()) }
@Test fun `gaps are allowed`() = assertEquals(emptyList<String>(), Routine.validate(listOf(b("05:00","06:00","A"), b("09:00","10:00","B"))))
@Test fun `switch applies only on its date`() {
    val r = RoutineSettings(activeKind = PresetKind.LAZY, activeDate = D)
    assertEquals(PresetKind.LAZY, Routine.activeKind(r, D)); assertEquals(PresetKind.DAILY, Routine.activeKind(r, D.plusDays(1)))
}
@Test fun `current and next block`() {
    val bl = listOf(b("05:00","07:00","Gym"), b("09:00","10:00","Read"))
    assertEquals("Gym", Routine.currentBlock(bl, min("06:59"))?.label); assertNull(Routine.currentBlock(bl, min("07:00")))
    assertEquals("Read", Routine.nextEnding(bl, min("07:00"))?.label); assertNull(Routine.nextEnding(bl, min("10:00")))
}
@Test fun `matching tasks come first, others keep order`() {
    // tasks: "Read 20 pages" (learning), "Gym session" (fitness), "Evening walk" (health, exactTime 05:30)
    // block 05:00–07:00 "Gym" → order: Gym session, Evening walk (exactTime inside), Read 20 pages
}
@Test fun `no match keeps original order`()
```

- [ ] **Step 2: Add migration tests to `SettingsMigrationTest`**

```kotlin
@Test fun `a fresh install must set up its routine`() = assertEquals(RoutineSetup.REQUIRED, AppSettings().migrated().routine.setup)
@Test fun `upgrading an onboarded 2_3 user gets the prompt`() = assertEquals(RoutineSetup.PROMPT, AppSettings(settingsVersion = 4, onboardingDone = true).migrated().routine.setup)
@Test fun `restored 2_3 settings get the prompt, not the forced setup`() {
    val json = SettingsJson.encodeToString(AppSettings.serializer(), AppSettings(settingsVersion = 4, onboardingDone = true))
        .let { Json.parseToJsonElement(it).jsonObject.minus("routine").let(::JsonObject).toString() } // a 2.3 file has no "routine"
    assertEquals(RoutineSetup.PROMPT, SettingsJson.decodeFromString(AppSettings.serializer(), json).migrated().routine.setup)
}
@Test fun `already migrated settings are untouched`() = assertEquals(RoutineSetup.DONE, AppSettings(settingsVersion = 5, routine = RoutineSettings(setup = RoutineSetup.DONE)).migrated().routine.setup)
```

- [ ] **Step 3: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests 'app.levelup2027.RoutineTest' --tests 'app.levelup2027.SettingsMigrationTest'` → compilation errors (types missing).

- [ ] **Step 4: Implement `Routine.kt` and the settings changes.** Migration step: `if (settingsVersion < 5) routine = routine.copy(setup = if (onboardingDone) PROMPT else REQUIRED)` applied after the existing steps (so v1 users, whose `onboardingDone` is set by the earlier step, also get PROMPT). Matching in `orderForBlock`: a task matches if `exactTime` (HH:mm) is within `[start, end)`, or a lowercase word (≥ 3 letters, not in a small stop-list: "the","and","for","with","time","day") of the block label equals a word in the task title or its category name. Stable partition: matches first, others after, each keeping input order.

- [ ] **Step 5: Run, expect PASS** (same command).

- [ ] **Step 6: Commit** — `git commit -m "feat(routine): preset model, validation and settings migration"`

---

### Task 2: Repository operations

**Files:**
- Modify: `app/src/main/java/app/levelup2027/data/Repository.kt`
- Modify: `app/src/main/java/app/levelup2027/widget/LevelUpWidget.kt` (call site of renamed toggle)
- Test: `app/src/test/java/app/levelup2027/RepositoryRoutineTest.kt`

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
```kotlin
/** Validates; on success stores normalized blocks and returns emptyList(), else returns errors and stores nothing. */
suspend fun savePreset(kind: PresetKind, blocks: List<Block>): List<String>
/** Sets today's preset; LAZY turns low-energy on for today, leaving LAZY (from an active LAZY today) turns it off. */
suspend fun setPreset(today: LocalDate, kind: PresetKind)
suspend fun setRoutineSetup(state: RoutineSetup)
suspend fun dismissRoutinePrompt()
/** Replaces toggleFromWidget. Undo if done; else complete as MINIMUM when low-energy, minimumByDefault, or prefer-minimum override, otherwise IDEAL. */
suspend fun toggle(taskId: String, date: LocalDate, minimumByDefault: Boolean): CompletionType?
```
All settings writes go through `updateSettings(ChangeKind.SETTINGS)` so alarms re-arm.

- [ ] **Step 1: Write failing tests** (use `TestEnv`):
  - `saving an invalid preset stores nothing and returns errors`
  - `saving a preset stores it sorted and trimmed`
  - `choosing Lazy Day turns on low energy for today`
  - `leaving Lazy Day turns low energy off`
  - `switching between Daily and Productive leaves low energy alone` (low-energy set manually stays on)
  - `low-energy toggle is independent of the preset` — setPreset(LAZY); setLowEnergy(false) → activeKind LAZY, isLowEnergy false; setPreset(DAILY) → isLowEnergy still false
  - `saving a preset or switching reports a settings change` — `env.changes` contains `ChangeKind.SETTINGS`
  - `toggle completes minimum on low-energy day and undoes on second tap`

- [ ] **Step 2: Run, expect FAIL** — `--tests 'app.levelup2027.RepositoryRoutineTest'`
- [ ] **Step 3: Implement.** Widget passes `minimumByDefault = s.widget.mode == WidgetMode.MINIMUM`.
- [ ] **Step 4: Run `RepositoryRoutineTest` and `RepositoryTest`, expect PASS.**
- [ ] **Step 5: Commit** — `feat(routine): repository preset switching and shared toggle`

---

### Task 3: Check-in scheduling and notification

**Files:**
- Create: `app/src/main/java/app/levelup2027/notify/CheckIns.kt`
- Modify: `app/src/main/java/app/levelup2027/notify/Reminders.kt` (`scheduleAll` also calls `CheckInScheduler.schedule`; `Notifications.createChannel` also creates the check-in channel)
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` (channel name/desc)
- Test: `app/src/test/java/app/levelup2027/CheckInSchedulerTest.kt`

**Interfaces:**
- Consumes: `Routine.blocksFor`, `Routine.nextEnding`.
- Produces:
```kotlin
data class PendingCheckIn(val date: LocalDate, val block: Block, val at: ZonedDateTime)
object CheckInScheduler {
    const val REQUEST_CODE = 106
    const val EXTRA_DATE = "checkin_date"; const val EXTRA_START = "checkin_start"; const val EXTRA_END = "checkin_end"; const val EXTRA_LABEL = "checkin_label"
    fun next(r: RoutineSettings, now: ZonedDateTime): PendingCheckIn?   // pure: today's active preset only; null if disabled/empty/none left
    fun schedule(context: Context, s: AppSettings)                     // cancel + arm REQUEST_CODE for next(), or just cancel
}
object CheckInNotifications {
    const val CHANNEL = "checkins"; const val NOTIFICATION_ID = 2001
    fun canUseFullScreen(context: Context): Boolean                    // API 34+: NotificationManager.canUseFullScreenIntent(); else true
    fun post(context: Context, p: PendingCheckIn)
}
class CheckInReceiver : BroadcastReceiver()   // posts, then CheckInScheduler.schedule(...)
```

- [ ] **Step 1: Write failing tests for `CheckInScheduler.next`:**
  - `arms the next block end in today's preset` — Daily 05–07 Gym, now 06:00 → at 07:00 today, label Gym
  - `nothing when check-ins are off, preset empty, or all blocks ended`
  - `switching mid-block arms the current block's end` — Daily has 05–07; LAZY active today with 06–09 "Slow morning"; now 06:30 → 09:00 "Slow morning"
  - `yesterday's switch is ignored` — activeKind LAZY, activeDate yesterday → uses Daily
  - `midnight end carries the block's date` — 22:00–24:00 at 23:00 → `at` = next day 00:00, `date` = today
- [ ] **Step 2: Run, expect FAIL** — `--tests 'app.levelup2027.CheckInSchedulerTest'`
- [ ] **Step 3: Implement.**
  - Alarm: `setExactAndAllowWhileIdle(RTC_WAKEUP, …)` when `AlarmManager.canScheduleExactAlarms()` (API 31+) or API < 31; otherwise `setWindow(RTC_WAKEUP, at, 2 min, …)`. Manifest: `USE_EXACT_ALARM` (auto-granted, API 33+; app is sideloaded so Play policy doesn't apply) plus `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"`; `USE_FULL_SCREEN_INTENT`; register `CheckInReceiver` (not exported) and `CheckInActivity` (Task 4).
  - Channel `checkins`, `IMPORTANCE_HIGH`. Notification: title `"${block.label} block done"`, text `"Tick off what you finished."`, category `CATEGORY_REMINDER`, `setFullScreenIntent(pi, true)` only when `canUseFullScreen`, content intent = same activity PendingIntent (extras from `PendingCheckIn`), `setAutoCancel(true)`, fixed `NOTIFICATION_ID` so newer replaces older.
  - The midnight alarm and `SystemEventsReceiver` already call `scheduleAll`; no new triggers needed.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(routine): arm and post block check-ins`

---

### Task 4: Check-in screen

**Files:**
- Create: `app/src/main/java/app/levelup2027/ui/checkin/CheckInActivity.kt`
- Create: `app/src/main/java/app/levelup2027/ui/checkin/CheckInScreen.kt`

**Interfaces:**
- Consumes: `CheckInScheduler.EXTRA_*`, `Routine.orderForBlock`, `Routine.format`, `Repository.toggle`, `ScheduleEngine.occurrencesFor`, `Repository.hiddenCategories`.
- Produces: `CheckInActivity` (launched by Task 3's PendingIntent).

- [ ] **Step 1: Implement `CheckInActivity`** — `setShowWhenLocked(true)`, `setTurnScreenOn(true)` (API 27+; manifest attributes for 26), `excludeFromRecents`, `launchMode="singleTop"` (handle `onNewIntent` by replacing the block). Cancels `NOTIFICATION_ID` on open. Reads date + block from extras; missing/invalid extras → `finish()`.
- [ ] **Step 2: Implement `CheckInScreen(date, block, occurrences, preferMinimum, onToggle, onDone)`** — heading `"${format(start)}–${format(end)} · ${label}"`; list = `orderForBlock` of the date's occurrences (skipped/rescheduled excluded), ticked = `isDone`; tap toggles via `repository.toggle(id, date, minimumByDefault = settings.defaultMode == DefaultMode.MINIMUM)` (undo is the same tap); when all done, show "All of today's tasks are done" above the list; one "Done" button closes. Use the 2.3 theme (`ui/theme`) and existing components for task rows where they fit.
- [ ] **Step 3: Verify manually** — build `./gradlew :app:assembleDebug`, install, create a block ending in 2 minutes, lock the phone → full-screen check-in appears; unlocked in another app → banner, tap opens it; tick a task → it shows ticked on Today and on the widget.
- [ ] **Step 4: Commit** — `feat(routine): full-screen block check-in`

---

### Task 5: Preset editor and Settings → Routine

**Files:**
- Create: `app/src/main/java/app/levelup2027/ui/routine/PresetEditor.kt`
- Create: `app/src/main/java/app/levelup2027/ui/routine/RoutineSettingsSection.kt`
- Modify: `app/src/main/java/app/levelup2027/ui/settings/SettingsScreen.kt` (new `GroupLabel("Routine")` group above "Reminders")
- Modify: `app/src/main/java/app/levelup2027/ui/AppViewModel.kt` (expose `savePreset`, `setPreset`, `setRoutineSetup`, `dismissRoutinePrompt`)

**Interfaces:**
- Consumes: Task 2 repository functions.
- Produces:
```kotlin
@Composable fun PresetEditor(kind: PresetKind, initial: List<Block>, copyFrom: List<Block>?, onSave: (List<Block>) -> Unit, onDescribe: ((String) -> Unit)?, describing: Boolean, describeError: String?)
@Composable fun RoutineSettingsSection(vm: AppViewModel)
@Composable fun CheckInPermissionRow()   // shown when !CheckInNotifications.canUseFullScreen; opens Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT (API 34+)
```

- [ ] **Step 1: Implement `PresetEditor`** — list of blocks (start/end via the existing time-picker dialog pattern in `SettingsScreen.kt:451`, label field capped at 40), add/delete, "Start from a copy of Daily Routine" when `copyFrom` non-empty and list empty, inline errors from `vm.savePreset` result. `onDescribe == null` hides the "Describe it" option (wired in Task 7).
- [ ] **Step 2: Implement `RoutineSettingsSection`** — three rows (preset name + "n blocks" / "Not set up"), each opens the editor in a sheet; "Block check-ins" switch bound to `routine.checkInsEnabled`; `CheckInPermissionRow`.
- [ ] **Step 3: Verify manually** — edit each preset, save an overlapping block → error shown and nothing saved; valid save → alarm armed (check-in fires).
- [ ] **Step 4: Commit** — `feat(routine): preset editor in settings`

---

### Task 6: "Your day" onboarding step, Today switcher, existing-user prompt

**Files:**
- Create: `app/src/main/java/app/levelup2027/ui/routine/RoutineSetupScreen.kt`
- Create: `app/src/main/java/app/levelup2027/ui/routine/PresetSwitcher.kt`
- Modify: `app/src/main/java/app/levelup2027/ui/AppRoot.kt` (gate after the onboarding check)
- Modify: `app/src/main/java/app/levelup2027/ui/today/TodayScreen.kt` (switcher + current block at top; prompt card)

**Interfaces:**
- Consumes: `PresetEditor`, `CheckInPermissionRow`, Task 2 functions, `Routine.activeKind/blocksFor/currentBlock`.
- Produces:
```kotlin
@Composable fun RoutineSetupScreen(vm: AppViewModel, required: Boolean, onFinished: () -> Unit)
@Composable fun PresetSwitcher(active: PresetKind, current: Block?, onSelect: (PresetKind) -> Unit)
```

- [ ] **Step 1: Gate in `AppRoot`** — after the `onboarding || builderOpen` branch: `if (s.routine.setup == RoutineSetup.REQUIRED) { RoutineSetupScreen(vm, required = true) { vm.setRoutineSetup(DONE) }; return }`. Because every onboarding exit sets `onboardingDone`, this covers accept-plan, example plan, start empty and open-AI-settings with no change to those paths.
- [ ] **Step 2: Implement `RoutineSetupScreen`** — pages: Daily Routine (Next disabled until ≥ 1 block saved when `required`), Extreme Productive, Lazy Day (each with "Set up later"), then a final page with `CheckInPermissionRow` and "Finish". `required = false` (from the prompt) allows closing at any page.
- [ ] **Step 3: Today** — `PresetSwitcher` at the top: three-way segmented control, below it `"Now: Gym · until 7:00"` or `"Free time"` (re-evaluated each minute via the existing `LocalTime.now()` refresh pattern on Today). When `routine.setup == PROMPT && !promptDismissed`: card "Set up your daily routine" → `RoutineSetupScreen(required = false)` (finishing sets DONE), dismiss → `dismissRoutinePrompt()`.
- [ ] **Step 4: Verify manually** — fresh install: each of the four onboarding exits lands on "Your day" and can't skip Daily Routine; install over 2.3 / restore 2.3 backup: lands on Today with the prompt; switch to Lazy Day → task cards show minimums.
- [ ] **Step 5: Commit** — `feat(routine): your-day setup, today switcher and prompt`

---

### Task 7: AI "Describe your day"

**Files:**
- Modify: `app/src/main/java/app/levelup2027/ai/AiClient.kt` (`ROUTINE("routine", "claude-haiku-5-5", "low", 3_000, serverFallback = false)`)
- Modify: `app/src/main/java/app/levelup2027/ai/GeminiClient.kt:77` (ROUTINE uses `LIGHT_MODELS` like INTAKE)
- Modify: `app/src/main/java/app/levelup2027/ai/Prompts.kt` (`val ROUTINE = SHARED + …`), `ai/AiModels.kt` (`DraftBlock`, `RoutineDraft`, `Schemas.routine`), `ai/AiService.kt`
- Modify: `worker/src/operations.js` (`routine: { model: "claude-haiku-5-5", effort: "low", maxTokens: 3000, fallback: false }`), `worker/src/prompt-hashes.json` (regenerated)
- Modify: `app/src/test/java/app/levelup2027/PromptHashesTest.kt` (add ROUTINE to the map)
- Modify: `ui/routine/PresetEditor.kt` callers (pass `onDescribe`), `ui/plan/PlanViewModels.kt` or a new `ui/routine/RoutineViewModel.kt` for the request state
- Test: `app/src/test/java/app/levelup2027/AiServiceTest.kt`

**Interfaces:**
- Produces:
```kotlin
@Serializable data class DraftBlock(val start: String, val end: String, val label: String)   // "HH:mm"
@Serializable data class RoutineDraft(val blocks: List<DraftBlock> = emptyList())
data class RoutineResult(val blocks: List<Block>, val problems: List<String>)
suspend fun AiService.draftRoutine(kind: PresetKind, text: String): RoutineResult
```
`draftRoutine` parses times (unparseable → dropped with a problem line), runs `Routine.validate` on what parsed, drops blocks that overlap an earlier one (problem line each). It never saves; the editor shows `blocks` + `problems` for the user to fix and save.

- [ ] **Step 1: Write failing tests in `AiServiceTest`** (use the `Scripted` client):
  - `routine draft is parsed into blocks` — `{"blocks":[{"start":"05:00","end":"07:00","label":"Gym"}]}` → one Block(300, 420, "Gym"), no problems; request op == ROUTINE
  - `bad times and overlaps are reported, not kept`
  - `no request is made without consent` for draftRoutine
- [ ] **Step 2: Run, expect FAIL** — `--tests 'app.levelup2027.AiServiceTest'`
- [ ] **Step 3: Implement.** Prompt: turn the person's description of one kind of day into time blocks; 24h "HH:mm"; no overlaps; within one day; short labels (≤ 40 chars); don't invent blocks they didn't mention; include the preset name in the user message via a small `ContextBuilder.routine(kind, text)`.
- [ ] **Step 4: Regenerate hashes** — `WRITE_PROMPT_HASHES=1 ./gradlew :app:testDebugUnitTest --tests 'app.levelup2027.PromptHashesTest'`, then run it again without the variable → PASS. Run `cd worker && npm test` → PASS.
- [ ] **Step 5: Wire "Describe it"** in `PresetEditor` (text field + mic, same as `PromptAreas.kt`), hidden when `vm.ai.availability()` is `OFF`/`NOT_CONFIGURED`; `NEEDS_CONSENT` goes through the existing consent screen; errors show inline and leave by-hand editing available.
- [ ] **Step 6: Commit** — `feat(routine): describe your day with AI` (note in the commit body that the worker must be redeployed before release).

---

### Task 8: Release prep

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 6`, `versionName = "2.4.0"`)
- Modify: `README.md` (short "Daily routines and check-ins" section in the new-user journey; mention the full-screen notification permission and redeploying the worker)

- [ ] **Step 1:** Make the edits.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest` → all PASS; `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `chore: Ascend 2.4.0`
