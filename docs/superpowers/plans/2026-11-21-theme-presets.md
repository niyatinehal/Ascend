# Theme Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Six preset colour themes (each with dark and light versions) chosen in Settings → Appearance and applied to the app, check-in screen, assistant window and widget.

**Architecture:** A `ThemePreset` enum in settings; `ThemePresets.scheme(preset, dark)` builds Material 3 colour schemes from a small per-preset palette; `LevelUpTheme` takes the preset; the widget gets the preset through `WidgetState` and draws with the preset's dark colours.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Glance, DataStore + kotlinx.serialization, JUnit4, Robolectric/Roborazzi screenshot walkthrough.

**Spec:** `docs/superpowers/specs/2026-11-21-theme-presets-design.md`

## Global Constraints

- Presets, in this order: Gold (default), Ocean, Forest, Rose, Lavender, Ember. Each has a dark and a light version; the existing Dark / Light / Follow system choice picks the version.
- Every preset × version: primary and secondary reach ≥ 4.5:1 contrast against background (WCAG AA).
- Ideal (primary) and minimum (secondary) ≥ 60° apart in hue, every preset × version.
- Gold must look exactly like today: dark primary `0xFFE8B44C`, secondary `0xFF5DBBA6`, background `0xFF0F1115`; light primary `0xFF9A6B00`, secondary `0xFF2F7F6F`, background `0xFFF6F5F2` (and the rest of today's `DarkColors`/`LightColors` unchanged).
- Error colour and category colours unchanged. No fonts, no custom pickers, no wallpaper colours.
- Widget uses the **dark** version of the chosen preset.
- Stored as `themePreset` in `AppSettings` (default Gold) → in backups; settings/backups without it restore as Gold.
- Build with `JAVA_HOME=~/.jdks/jdk-21.0.13+9`; tests `./gradlew :app:testDebugUnitTest --offline --tests '<class>'` (no `-q`; it hides failures).

## Review Focus

1. **Settings or a backup written before this change** → loads as Gold, no crash. (Task 1 test `settings without a preset load as Gold`.)
2. **A preset in light mode with a pale accent** → still readable. (Task 1 test `every preset is readable in both versions`.)
3. **Changing the preset while the widget is on the home screen** → widget redraws in the new colours. (Task 3 test `widget state carries the chosen preset`; redraw comes from the existing settings-change → widget refresh.)
4. **Check-in or assistant window opened after choosing Ocean** → uses Ocean. (Task 2: every `LevelUpTheme(` call passes the preset — checked by `grep`.)
5. **Gold users after the update** → nothing changes. (Task 1 test `gold matches today's colours`.)

---

### Task 1: Preset model and colour schemes

**Files:**
- Create: `app/src/main/java/app/levelup2027/ui/theme/ThemePresets.kt`
- Modify: `app/src/main/java/app/levelup2027/ui/theme/Theme.kt` (`LevelUpTheme` gains `preset`; today's `DarkColors`/`LightColors` become Gold's schemes)
- Modify: `app/src/main/java/app/levelup2027/data/Model.kt` (enum next to `ThemeChoice`), `data/SettingsStore.kt` (field)
- Test: create `app/src/test/java/app/levelup2027/ThemePresetsTest.kt`

**Interfaces — Produces:**
```kotlin
// data/Model.kt
@Serializable enum class ThemePreset(val label: String) { GOLD("Gold"), OCEAN("Ocean"), FOREST("Forest"), ROSE("Rose"), LAVENDER("Lavender"), EMBER("Ember") }
// AppSettings
val themePreset: ThemePreset = ThemePreset.GOLD
// ui/theme/ThemePresets.kt
data class WidgetColors(val bg: Color, val surfaceHigh: Color, val text: Color, val muted: Color, val ideal: Color, val minimum: Color)
object ThemePresets {
    fun scheme(preset: ThemePreset, dark: Boolean): ColorScheme
    fun widget(preset: ThemePreset): WidgetColors        // from the dark scheme
    fun contrast(a: Color, b: Color): Double             // WCAG ratio via Color.luminance()
    fun hueDistance(a: Color, b: Color): Float            // degrees, 0..180
}
@Composable fun LevelUpTheme(choice: ThemeChoice, preset: ThemePreset = ThemePreset.GOLD, content: @Composable () -> Unit)
```
Approach: each non-Gold preset defines, per version, primary/onPrimary/primaryContainer/onPrimaryContainer, secondary/(same four), and a lightly tinted background + surface ladder; everything else copies Gold's scheme. Hue from HSV (`android.graphics.Color.colorToHSV` is unavailable on the JVM — compute from RGB).

- [ ] **Step 1: Failing tests** in ThemePresetsTest:
  - `gold matches today's colours` — the exact values in Global Constraints for both versions.
  - `every preset is readable in both versions` — for each preset × dark/light: `contrast(primary, background) >= 4.5` and `contrast(secondary, background) >= 4.5`; failure message names the preset and version.
  - `ideal and minimum stay distinct` — `hueDistance(primary, secondary) >= 60f` for every preset × version.
  - `presets look different from each other` — no two presets share a dark primary.
  - `settings without a preset load as Gold` — decode `AppSettings` JSON with `"themePreset"` removed → `GOLD`; and default `AppSettings().themePreset == GOLD`.
  - `widget colours come from the dark version` — `widget(OCEAN).ideal == scheme(OCEAN, true).primary`, `.bg == scheme(OCEAN, true).background`.
- [ ] **Step 2: Run, expect FAIL** (unresolved `ThemePreset`).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run ThemePresetsTest + full suite → PASS.**
- [ ] **Step 5: Commit** `feat(theme): six colour presets`

---

### Task 2: Choose a preset in Settings and apply it everywhere

**Files:**
- Create: `app/src/main/java/app/levelup2027/ui/settings/ThemeSwatches.kt`
- Modify: `ui/settings/SettingsScreen.kt` (Appearance card: swatches above the Dark/Light chips)
- Modify: `MainActivity.kt`, `widget/WidgetConfigActivity.kt`, `ui/assistant/AssistantActivity.kt`, `ui/checkin/CheckInActivity.kt` (pass `settings.themePreset`)
- Modify: `app/src/test/java/app/levelup2027/screenshots/RoutineScreenshots.kt` (two shots)

**Interfaces:** Consumes Task 1. Produces `@Composable fun ThemeSwatches(selected: ThemePreset, dark: Boolean, onSelect: (ThemePreset) -> Unit)`.

- [ ] **Step 1: Implement `ThemeSwatches`** — a horizontally scrolling row; each swatch is a ~44dp circle split into the preset's primary (left) and secondary (right) for the version currently shown, name under it, a check over the selected one; whole swatch ≥ 48dp tap target with `contentDescription` "<name> theme" and selected state. Selecting calls `update(Repository.ChangeKind.OTHER) { it.copy(themePreset = p) }`.
- [ ] **Step 2: Pass the preset** at all four `LevelUpTheme(` call sites. Verify: `grep -rn "LevelUpTheme(" app/src/main | grep -v "fun LevelUpTheme" | grep -v themePreset` prints nothing.
- [ ] **Step 3: Screenshots** — in the walkthrough, after the existing Settings shots: scroll to "Appearance" and shot `17-settings-appearance`; tap "Ocean" and shot `18-settings-ocean` (the screen itself recolours). Run with `-PscreenshotDir=…`, look at both images.
- [ ] **Step 4: Verify** — `./gradlew :app:compileDebugKotlin --offline`, full suite PASS.
- [ ] **Step 5: Commit** `feat(theme): pick a theme in Settings`

---

### Task 3: Widget follows the preset

**Files:**
- Modify: `app/src/main/java/app/levelup2027/domain/WidgetState.kt` (`WidgetState.preset`, `WidgetStateBuilder.build(..., preset: ThemePreset = ThemePreset.GOLD)`)
- Modify: `app/src/main/java/app/levelup2027/data/Repository.kt` (`buildWidget` passes `s.themePreset`)
- Modify: `app/src/main/java/app/levelup2027/widget/LevelUpWidget.kt` (replace the top-level `text`/`muted`/`gold`/`teal` providers and `Palette.Bg`/`Palette.SurfaceHigh` with `ThemePresets.widget(state.preset)`)
- Test: `app/src/test/java/app/levelup2027/ProgressAndWidgetTest.kt`

**Interfaces:** Consumes `ThemePresets.widget`, `AppSettings.themePreset`.

- [ ] **Step 1: Failing test** `widget state carries the chosen preset` — `TestEnv(AppSettings(themePreset = ThemePreset.FOREST)).repo.widgetStateNow(mon).preset == FOREST`; default build → `GOLD`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** (colours passed down from `WidgetBody(state)` to `Header`/`TaskRow`/etc.; no other widget behaviour changes).
- [ ] **Step 4: Run ProgressAndWidgetTest + full suite → PASS; `./gradlew :app:assembleRelease --offline` → BUILD SUCCESSFUL.**
- [ ] **Step 5: Commit** `feat(theme): widget uses the chosen theme`
