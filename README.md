# Ascend (2027 campaign)

A personal, offline Android checklist for the 26 Oct 2026 – 31 Dec 2027 campaign. It covers five goals: career, fitness, technical confidence, parents' phones, and business. It includes a home-screen widget that can complete tasks without opening the app.

Stack: Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Jetpack Glance (widget) · AlarmManager (inexact reminders). No backend, account, network permission, or paid service.

- **APK:** `dist/Ascend-1.0.0.apk` (release build, R8-shrunk, about 2.5 MB, signed with the local key in `keystore/`)
- **Min Android:** 8.0 (API 26) · **Target:** Android 15 (API 35)

---

## 1. Install on your phone

1. Copy `dist/Ascend-1.0.0.apk` to the phone. You can use a USB cable, Google Drive, Telegram "Saved messages", or email it to yourself.
2. On the phone, open the file with Files or Downloads.
3. When Android asks, allow **Install unknown apps** for the app you opened it from. On most phones this is Settings → Apps → Special app access → Install unknown apps. Tap **Install**.
4. Play Protect may warn that it doesn't recognise the app (it isn't from the Play Store). Choose **More details → Install anyway**.

Or install over USB: enable Developer options → USB debugging, then run:

```bash
~/Android/Sdk/platform-tools/adb install -r dist/Ascend-1.0.0.apk
```

**Updating later:** install the new APK over the old one. Your data stays, but only if the new APK is signed with the **same key**, which is `keystore/levelup2027-release.jks` plus `keystore.properties`. **Back up both files.** If you lose them, you'll have to uninstall to update, and uninstalling erases your data unless you export a backup first.

## 2. Add the home-screen widget

1. Long-press an empty spot on the home screen → **Widgets**.
2. Find **Ascend → "Ascend: today"** and drag it onto the home screen.
3. A settings screen opens (on Android 12+ it may be skipped; reconfigure any time by long-pressing the widget, or under Settings → Home-screen widget). Choose:
   - time slot: all of today, or Morning / During Work / Evening / Flexible
   - ideal or minimum task text
   - whether to show completed tasks
   - which categories to show
4. **Resize** the widget (long-press → drag the handles):
   - **small** (about 2×2): progress plus the next 2 open tasks
   - **medium** (about 3×3): progress plus up to 5 tasks, each with its one-line instruction
   - **large** (4 rows or more): the full scrollable list, grouped by time of day

On the widget:
- **Circle** → completes the task in place, using the version the widget shows. On a low-energy day that is always the minimum version. Tap a filled circle to undo.
- **Task title** → opens that task's detail sheet in the app.
- **Header** → opens the app. **↻** → refreshes.

The widget refreshes when you complete or undo a task (in the app or on the widget), when you edit the schedule or settings, when you change the widget's filter, and just after midnight. As a safety net, Android also refreshes it hourly.

## 3. Daily use

- **Today** (the default screen) shows the date, a greeting, your campaign day, today's progress (done / scheduled, ideal vs minimum), and tasks grouped by Morning → During Work → Evening → Flexible.
  - Each card already shows the first ideal and minimum step with time estimates, so you don't need to open it.
  - **Ideal** / **Minimum** buttons complete the task in one tap. **Skip** asks for an optional reason. Tap a card for full details, notes and resources.
  - Filter chips: Today, Morning, During Work, Evening, Flexible, Overdue, Completed. The count line follows the filter.
  - **Low-energy day?** switches every card to its minimum version for **today only**. Task definitions are never changed.
- **Tasks**: every task, with search plus filters for date, category, time slot, goal and state. **Weekly plan** shows Mon–Sun with estimated ideal and minimum minutes per day.
- **Goals**: five goal cards with target, current value, next milestone, a measurable bar where one exists, a timeline (tick milestones off), linked tasks, and **Record progress**.
- **Review**: weekly stats (done, %, ideal vs minimum, gym sessions, tech sessions, business actions, phone fund change, frequently skipped tasks, patterns), the four reflection questions, and saved past reviews.
- **Settings**: daily mode, schedule/task editing, reminders, categories, widget, theme, backup/restore and reset.

### Rules the app follows
- Ideal and minimum both count as "done". The app stores which one you did, and minimum still earns most of the XP.
- Skipping never creates a backlog. A **recurring** task simply returns on its next scheduled day.
- **One-time** tasks (curriculum, checkpoints, your own) that pass without being done or skipped appear under **Overdue**. You can do them late or skip them, and they stay in history.
- Each task counts once per day. Completion history is stored separately from task definitions, so editing or deleting a task never erases past records.
- Days are calculated from the phone's local calendar date. Time-zone, clock and date changes are handled.

## 4. Editing your schedule

- **Settings → Edit weekly schedule** (or Tasks → *Weekly plan*) → tap a task → change its days, time slot, ideal and minimum text, minutes, effort, linked goal, notes and resources → **Save**.
- To pause something, turn off **Enabled** instead of deleting it.
- **New task**: the + button on the Tasks tab. Choose *Repeats weekly* (pick days) or *One-time* (pick a date).
- Turn off whole categories in **Settings → Categories**. They are hidden from Today, counts and the widget, but their history is kept.
- **Reset plan to defaults** restores the original tasks and goals and keeps your history. **Erase everything** also deletes history.

## 5. Back up and restore

- **Settings → Your data → Export backup (JSON)** saves one file with all tasks, completions, goals, milestones, progress entries, reviews and settings. Save it to Google Drive or Downloads. Doing this weekly is a good habit, for example as part of the Sunday review.
- **Restore from backup** → pick the file → confirm. This **replaces** all current data on the phone. Reminders and the widget update automatically.
- Android's own Google backup also includes the app database if backup is on for your phone. The JSON export is the reliable route between phones.

## 6. What's seeded

- **Recurring week**, as in your plan:
  - gym Mon/Tue/Thu/Fri (4 sessions), recovery and business discovery on Wednesday
  - Saturday deep work (project, business, optional easy cardio)
  - a light Sunday (fitness check-in, technical review, finances and phone fund, weekly review)
  - five short *During Work* observation tasks on weekdays only, a daily morning routine, and a weekday end-of-day review
- **Curriculum (29 dated tasks), 26 Oct – 22 Nov 2026.** Each one replaces its recurring counterpart on that day, so the same activity is never shown twice. Example: on Mon 2 Nov, "Async JavaScript" replaces "JavaScript / TypeScript study".
- **After the curriculum:**
  - recurring tasks carry topic banks (JS/TS, backend, system design, DSA order)
  - **roadmap checkpoint** tasks are generated from your milestones, on the last Sunday of each milestone month
  - **job search & interview practice** starts on Sundays from March 2027
- **Goals and milestones** exactly as in your plan. Phone fund target: ₹35,000 (editable, with an editable target date).
- **Starting values only from your plan:**
  - salary ₹6 LPA, target ₹10 LPA
  - weight ~63 kg, *labelled as an estimate*
  - MRR ₹0 and 0 paying customers (no business yet)
  - push-ups, pull-ups and the phone fund balance start as **not recorded**: log your baselines in week 1
  - targets such as "₹2,000–5,000 MRR" are shown only as targets and never as revenue

## 7. Reminders

The defaults are morning 7:00, during work 12:30, evening 20:30, and Sunday review 18:00. All are editable and can be turned off in Settings.

- The app asks for notification permission **after your first completed task**, not on first launch. You can also allow it from Settings.
- A reminder is skipped when nothing is left in its time slot. The weekly one is skipped if you've already saved that week's review.
- Reminders use **inexact** alarms, so no exact-alarm permission is needed and they may arrive a few minutes late. On phones with aggressive battery management (Xiaomi, Oppo, Vivo, Realme, OnePlus, some Samsung), use **Settings → Battery optimisation settings** to exempt the app if reminders arrive very late or not at all.

## 8. Build status and tests

Built on this machine (OpenJDK 17, Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, SDK 35):

| Check | Result |
|---|---|
| `./gradlew :app:assembleRelease` | ✅ success, signed APK, R8 shrinking |
| `./gradlew :app:testDebugUnitTest` | ✅ 47 tests, 0 failures |
| `./gradlew :app:lintDebug` | ✅ 0 errors (remaining warnings: newer library versions available; API-31-only widget attributes ignored on older phones) |
| APK inspection | ✅ signature verifies; permissions are only notifications, boot-completed and wake-lock (no internet or network-state) |

Test suites (`app/src/test`):
- **ScheduleEngineTest (14 tests):**
  - recurrence parsing and campaign bounds
  - curriculum tasks replacing recurring ones without duplicates
  - disabled tasks and categories
  - slot ordering (During Work never becomes Evening)
  - overdue only for one-time tasks
  - time-zone date boundaries, a 25-hour DST day, and Monday week starts
- **ProgressAndWidgetTest (10 tests):**
  - daily progress (ideal and minimum count as done, skipped doesn't, no double counting)
  - XP and weekly stats
  - widget item selection and filters, minimum text on low-energy days
  - reminder trigger times
- **SeedTest (12 tests):**
  - unique, stable ids
  - every task has ideal and minimum versions
  - exactly 4 gym days and none on Wednesday
  - during-work tasks ≤ 10 min, weekdays only
  - no more than one high-effort task per day
  - day workloads are reasonable
  - all 29 curriculum tasks fall on the correct weekday
  - initial values come only from the plan
- **RepositoryTest (11 tests, in-memory DAOs):**
  - seeding is idempotent and preserves your edits
  - completion types replace each other rather than duplicating, and undo works
  - skip reasons are stored
  - editing a task keeps its history
  - widget toggle (ideal / minimum mode / low-energy / undo) and that each toggle triggers a widget refresh
  - widget state after a widget completion
  - reset with and without history, and review upsert

## 9. Known limitations (please read)

- **Not yet run on a real device or emulator.** No phone was connected over USB, and the build machine's disk is nearly full (about 2.4 GB free), so there was no room for an emulator image. Everything above was verified by compiling, unit tests, lint and APK inspection. The widget's look and tap behaviour, notifications and the screens' layout still need a first check on your phone. If anything misbehaves, note the screen and what you tapped.
- **Widget settings are shared** by all Ascend widgets you place; they're not per widget.
- **One resizable widget**, not three separate entries in the picker. Small, medium and large are layouts chosen from the widget's size.
- **The widget circle completes the version the widget shows** (ideal by default, minimum on low-energy days or in minimum mode). To choose a different version, tap the title and use the app.
- **The midnight widget refresh uses an inexact alarm**, so on some phones it may lag by a few minutes. Opening the app always shows the correct day.
- **Progress values are recorded for today's date** (no backdating). Restore replaces data rather than merging.
- **"Afternoon" items** in your Saturday and Sunday plan go in the *Flexible / Anytime* slot, because the app has exactly the four requested slots. Their descriptions say "Saturday afternoon" and so on.
- **The gym tasks don't prescribe exercises, sets or loads.** They follow "the planned workout"; record sets and weights in the completion note.
- On Android 8–11 the widget's small category dots render square instead of round (a platform limitation).

## 10. Build from source

```bash
cd levelup-2027
./gradlew :app:testDebugUnitTest      # tests
./gradlew :app:assembleRelease        # → app/build/outputs/apk/release/app-release.apk
```

`local.properties` points at the SDK (`sdk.dir=/home/niyati/Android/Sdk`). Without `keystore.properties` the release APK is unsigned, so use `assembleDebug` for a quick installable build.

Disk note: the Android SDK (`~/Android/Sdk`, about 0.6 GB) and Gradle cache (`~/.gradle`, about 1.7 GB) were installed for this build. They're safe to delete if you need the space, and Gradle will re-download them for the next build.

### Project layout

```
app/src/main/java/app/levelup2027/
  data/      Model (enums, metrics), Entities, Database (Room DAOs), SettingsStore (DataStore),
             Repository (all writes), Seed (your plan), Backup (JSON export/restore)
  domain/    Schedule (recurrence, occurrences, overdue, dates), Progress (daily/weekly stats, XP),
             WidgetState (pure widget model)
  widget/    LevelUpWidget (Glance UI + actions), WidgetConfigActivity
  notify/    Reminders (inexact alarms, notifications, boot/time-change receiver)
  ui/        AppRoot (tabs), AppViewModel, components, today/, tasks/, goals/, review/, settings/, theme/
app/schemas/ Room schema history (add a Migration in AppDatabase.MIGRATIONS when bumping the version)
```
