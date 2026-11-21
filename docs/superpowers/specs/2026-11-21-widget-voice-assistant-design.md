# Widget voice assistant — design

Date: 2026-11-21 · App: Ascend (Android) · Builds on: routine presets (2.4, `feat/routine-presets`)

## Why

The user wants to change their day without opening the app: tap a mic on the home-screen widget and say what they want — "switch to Lazy Day", "move my gym block to 6 to 8", "push my Spanish goal back a month", "I finished the gym", "what's left today?". It should feel like an assistant that can do anything in Ascend, not a fixed list of commands.

## What exists to build on

- **Adjust my plan** (`ui/plan/AdjustSheet.kt`, `AiService.adjust`, `ai/ChangeValidator.kt`, `Repository.applyChanges`): the person describes a change in words; the AI returns a `reply`, an optional clarifying `question`, and a list of `ChangeOp`s (reschedule, use minimum, skip, update/add/disable task, update goal, set capacity, set low energy); the app validates them and shows them for approval before applying. Conversation turns are supported.
- **Voice input**: `rememberVoiceInput` / `MicButton` (`ui/plan/AiSetup.kt`) using the system speech recognizer.
- **Widget**: Jetpack Glance (`widget/LevelUpWidget.kt`), with a header row and tap actions.
- **Routine presets** (2.4): `Repository.setPreset`, `Repository.savePreset`, `Routine.validate`.

## What the user gets

1. **A mic button on the widget header.** One tap opens a compact assistant window over the home screen (not the full app) and starts listening immediately.
2. **They speak a request.** The words appear as text so they can see what was heard; they can re-record or type instead.
3. **The assistant answers** with a short reply, and, when the request changes something, a list of the exact proposed changes in plain language ("Switch today to Lazy Day", "Daily Routine: Gym 5:00–7:00 → 6:00–8:00", "Spanish goal deadline 1 Mar → 1 Apr", "Mark 'Gym session' done").
4. **Nothing changes until they tap Apply.** Cancel discards. (User decided: always confirm.)
5. If the request is unclear, the assistant asks one follow-up question and the user answers by voice again (same window, conversation continues).
6. Questions with no changes ("what's left today?", "what's my next block?") just get a reply and a Close button.
7. After Apply, a one-line confirmation ("Done. Today is now Lazy Day.") and the window closes; widget refreshes.

### What it can do (scope)

Everything Adjust already does, plus three new change types:

| New change | Meaning | Applied via |
|---|---|---|
| `SET_PRESET` | Today's preset → Daily / Extreme Productive / Lazy Day (Lazy turns on minimums, as in 2.4) | `Repository.setPreset` |
| `SET_TODAY_BLOCKS` | **Default for any block change.** Today's blocks only: the AI sends the full new list for today's active preset, so "move gym to 6–8", "add reading 9–10 pm", "remove commute" are all one op. Tomorrow is back to the saved preset. | New `Repository.setTodayBlocks` (validated with `Routine.validate`) |
| `SET_PRESET_BLOCKS` | **Only when the user says so** ("permanently", "for good", "every day", "from now on"): replace a preset's saved block list. | `Repository.savePreset` (validated; invalid → rejected with the reason shown) |
| `COMPLETE_TASK` | Mark today's occurrence of a task done (ideal or minimum per the usual rules) | `Repository.toggle`-style completion, never undoing an existing completion |

Questions/answers need no new change type; they use `reply`.

**Today-only vs permanent (user's decision):** a block change applies to today only unless the user explicitly says it's permanent. The proposed change says which ("Today only: Gym 5:00–7:00 → 6:00–8:00" vs "Daily Routine, from now on: …").

**How today-only blocks are stored (my call):** `RoutineSettings` gains one optional `todayOverride = (date, preset, blocks)`. `Routine.blocksFor(r, date)` returns the override's blocks when `date` and the active preset match it, otherwise the preset's saved blocks. Because Today's "Now:" line, check-in scheduling and the check-in screen all go through `blocksFor`, they follow automatically; it lives in settings, so it's in backups. Only one day is kept: a new today-only change replaces the old one, and an override for a past date is ignored. Switching preset shows that preset's saved blocks; switching back to the overridden preset shows the override again. Editing a preset permanently in Settings doesn't touch today's override.

*(My call)* Requests outside Ascend ("set an alarm", "text mum") get a polite reply that it can only help with the plan, routine, goals and tasks.

## Decisions (mine unless marked)

- **One AI path, not two.** The widget assistant uses the existing Adjust operation, extended with the routine context (presets + blocks + today's active preset + current block) and the three new change types. The in-app Adjust sheet gains the same abilities for free. *(My call — avoids two prompts drifting apart.)*
- **Confirmation always** (user's decision), including for ticking a task off. In the in-app Adjust sheet, the existing "auto-apply simple changes" setting stays, but the new change types are always marked as needing approval, so they never apply without a tap anywhere. *(My call)*
- **Choosing changes**: the window lists proposed changes with a checkbox each (all ticked), like the Adjust sheet; Apply applies the ticked ones. If two block changes for the same preset/day clash, the later one wins. *(My call)*
- **"I finished X"**: completes as minimum if the user says so ("only did the minimum"); otherwise the usual app rule (low-energy day, per-day prefer-minimum, or default mode set to Minimum → minimum; else ideal). A task skipped earlier today can be turned into a completion; an existing completion is never undone. *(My call)*
- **Assistant window**: a separate, translucent, dialog-styled activity launched from the widget (`excludeFromRecents`), so it opens over the home screen without the app's navigation. It does not show over the lock screen. *(My call)*
- **Listening** uses the system speech recognizer (as the app already does). If none is available, the window opens with the text field focused instead. *(My call)*
- **No AI configured / no consent / offline / quota**: the window says why in one line, with "Open AI settings" (or the existing consent dialog for first use). Nothing else works without AI. *(My call)*
- **Context sent to the AI** follows the existing Adjust rules (a week of tasks, no notes or diary text) plus preset names, block times/labels and the active preset. No new personal data categories.
- The widget gets the mic in its header on every widget size (next to refresh where there is one). *(My call)*

## Constraints

- Prompt change ⇒ `worker/src/prompt-hashes.json` regenerated (`PromptHashesTest`) and the AI server redeployed before release, same as 2.4.
- All new change types go through `ChangeValidator` with clear rejection messages (unknown task id, invalid blocks, unknown preset).
- Applying is one `Repository.applyChanges` call so the change log records it like any Adjust.
- Must work on min SDK 26; widget built with the existing Glance setup.

## Goals

- Say it, see it, confirm it — from the home screen, in a few seconds.
- Covers routine, presets, goals and tasks with one mic.

## Non-goals

- A wake word, always-listening, or spoken (text-to-speech) replies.
- Acting on anything outside Ascend.
- Changing settings like reminders, theme or AI keys by voice.
- Lock-screen use.

## Anti-goals

- Anything changing without the user tapping Apply.
- A separate assistant brain that disagrees with Adjust.
- Silent failures: if the AI can't do it or the change is invalid, the user is told why.
- Sending more personal data than Adjust already does.

## Left to the builder

Exact window layout (follow the calm 2.3/2.4 style and the existing Adjust sheet), how proposed changes are worded (reuse `PlanChange.describe` style), the prompt wording for the new ops, schema details, and tests in line with `ChangeValidatorTest`, `AiServiceTest`, `PromptHashesTest`.
