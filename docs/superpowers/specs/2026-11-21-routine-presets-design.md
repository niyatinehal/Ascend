# Routine presets and block check-ins — design

Date: 2026-11-21 · App: Ascend (Android, Kotlin/Compose) · Target release: 2.4

## Why

Ascend knows *what* the user wants to do each day (tasks under long-term goals) but not *when*. Tasks only sit in rough slots (Morning / Work / Evening / Flexible). The user wants their day laid out as a timeline ("5–7 AM gym, 7–8 AM breakfast"), different timelines for different kinds of days, and a moment at the end of each block where the app asks "that block's over — what did you get done?" and lets them tick it off right there.

Goals and tasks stay exactly as they are. Presets are a new, separate layer of time on top of them.

## What the user gets

### Three presets

| Preset | Notes |
|---|---|
| **Daily Routine** | The default. Required during setup. ("Normal" and "Daily Routine" are the same thing.) |
| **Extreme Productive** | Optional; for heavy days. |
| **Lazy Day** | Optional; for low days. Choosing it also turns on the existing low-energy mode for today (tasks show and complete as their minimum versions). |

The three names are fixed; users don't add or delete presets in this version.

### Blocks

A preset is an ordered list of **blocks**. A block is: start time, end time, label (e.g. `05:00–07:00 Gym`).

- Blocks don't link to tasks or goals. A block is just a labelled stretch of time.
- Blocks within a preset can't overlap. Gaps are allowed (unplanned time).
- *(My call)* Blocks stay within one calendar day (no block crossing midnight) in v1. Sleep isn't modelled as a block.
- A preset may be empty (e.g. Lazy Day not set up yet). An empty preset produces no check-ins.

### Choosing today's preset

- Every day starts on **Daily Routine**.
- The user can switch preset at any time from the top of the Today screen. The switch applies to the rest of today only and resets to Daily Routine tomorrow.
- After a switch, only blocks that **end after now** in the new preset produce check-ins. Check-ins that already happened today stay as they were.
- Switching *to* Lazy Day turns on low-energy mode for today; switching *away from* Lazy Day turns it off. (The existing "Low on energy today?" toggle keeps working independently.)
- Today shows the current block under the switcher, e.g. "Now: Gym · until 7:00", or "Free time" in a gap.

### The block check-in (full-screen)

When a block in today's active preset ends, the app shows a full-screen check-in:

- Heading: the block that just finished — "5:00–7:00 · Gym".
- Below it: **today's tasks** (not long-term goals).
  - Tasks that seem to belong to this block come first (see matching below), then the rest of today's tasks.
  - Tasks already done today show as ticked. Open tasks can be ticked here. Ticking here is exactly the same as ticking on Today: same completion record, same ideal/minimum rules (low-energy / default mode / per-day "prefer minimum"), widget refreshes.
  - *(My call)* Ticking can be undone on this screen, like on Today.
- A "Done" / close button dismisses it. Anything unticked just stays open on Today.
- *(My call)* The check-in still appears when every task is already done — it then reads as a confirmation ("All of today's tasks are done"). The user asked for one at every block end.
- *(My call)* A check-in for an older block that is still unseen when a newer one fires is replaced by the newer one (see Notifications).

**Matching tasks to a block** (to order the list only — nothing is stored): a task counts as belonging to a block if its `exactTime` falls inside the block, or its title or category name shares a meaningful word with the block label (case-insensitive, ignoring short/common words). Precise rules are left to the builder; when nothing matches, the list is just today's tasks in normal order.

### Setting up presets

**New users (registration):** after the existing goals/plan step, add a "Your day" step. *(My call)* It runs on every path that currently finishes onboarding (accepting a plan, example plan, start empty, jumping to AI settings — see `AppRoot.kt:144`, `PlanBuilderScreen.kt:179`, `Repository.kt:69`/`300`): onboarding is only marked done after Daily Routine has at least one block.

1. Daily Routine first (required). Two ways, user's choice:
   - **By hand:** add blocks one by one (start, end, label), edit, delete.
   - **Describe it:** type or dictate their day in plain words ("gym 5 to 7, office 9 to 6, …"); AI drafts blocks; user edits them with the same by-hand editor before saving.
2. Then Extreme Productive and Lazy Day, same two ways, each with **"Set up later"** and a one-tap **"Start from a copy of Daily Routine"**.

Without AI configured (or offline / quota hit), the "Describe it" option is hidden or falls back to by-hand with a short note; by-hand always works.

**Existing users** (onboarding already done): a one-time prompt on Today ("Set up your daily routine") opening the same flow. Dismissable.

**Editing later:** a **Routine** section in Settings with all three presets, the same editor, and the check-in on/off switch.

### Notifications and permissions

- Check-ins should fire close to the block end; *(my call)* up to ~2 minutes late is acceptable, more than ~10 is not.
- Full-screen display uses a full-screen-intent notification. On Android 14+ this needs the user to grant "full-screen notifications"; ask at the end of the "Your day" step (and from Settings → Routine) with a one-line explanation. If not granted, fall back to a high-priority heads-up notification ("Gym block done — tick off what you finished") that opens the check-in screen when tapped.
- *(My call, user deferred)* When the phone is unlocked and in use, Android shows a full-screen-intent notification as a heads-up banner rather than taking over the screen. That's accepted: banner while in use, full screen when locked / screen off. Tapping the banner opens the check-in screen. No "draw over other apps" permission.
- *(My call)* An unopened check-in is fire-and-forget: it stays in the notification shade until swiped away and never re-appears automatically when the app is opened. Only the latest block's check-in is kept in the shade (newer replaces older).
- Timely firing may need exact alarms (`SCHEDULE_EXACT_ALARM` on 12+, user-granted on 14+). If not available, fall back to the current inexact alarms and accept lateness. Builder picks the mechanism within the timing target above.
- Alarms must be re-armed on preset switch, preset edit, boot, app update, time/time-zone change, and at midnight (new day → Daily Routine) — the existing `SystemEventsReceiver` / midnight alarm already covers the system events.
- Main switch in Settings → Routine: "Block check-ins" on/off (default on once a preset has blocks). Existing morning/work/evening reminders are unchanged.

## Data

- Presets and their blocks must be stored durably and **included in backup export/restore** (backups from older versions restore with no presets).
- Today's chosen preset is stored with its date (like `lowEnergyDate`), so it naturally resets the next day.
- Storage choice (new Room table + migration 2→3, or `AppSettings` JSON, which is already in backups) is left to the builder.

## AI

- One new AI operation: turn a plain-language day description into blocks for one preset. Small/cheap model, same consent and provider routing as existing operations.
- Output must be validated (valid times, start < end, no overlaps, within a day, sensible label length) before it reaches the editor; invalid output → show what could be parsed plus an error, never save silently.
- Server (Claude worker) mode enforces a prompt-hash allowlist (`worker/src/prompt-hashes.json`, `PromptHashesTest`); the new prompt must be added there.

## Goals

- Users can define timelines for three kinds of day and switch between them freely.
- At each block end they get a full-screen moment to tick off today's tasks.
- Lazy Day makes the day lighter (minimum versions) with one choice.

## Non-goals (this version)

- Custom presets beyond the three, or renaming them.
- Linking blocks to specific tasks or goals; time-blocking tasks automatically.
- Weekday/weekend auto-selection or a weekly preset calendar.
- Showing blocks on the widget.
- Changing how goals, tasks, or the AI plan work.

## Anti-goals (would be a failure even if it "works")

- Nagging: the check-in must be one screen, dismissable in one tap, never repeating for the same block.
- A second, separate way of completing tasks that disagrees with Today (different records, different ideal/minimum behaviour).
- Losing existing data or breaking backups/restores from 2.3.
- Making setup feel heavy: by-hand Daily Routine should take about a minute; everything except Daily Routine is skippable.

## Left to the builder

Storage shape, alarm mechanism within the timing target, exact task-matching heuristic, screen layouts (follow the calm 2.3 style), editor interaction details (time pickers, reordering), and test coverage in line with the existing test suite (`ScheduleEngineTest`, `RepositoryTest`, `MigrationTest`, `PromptHashesTest`, etc.).
