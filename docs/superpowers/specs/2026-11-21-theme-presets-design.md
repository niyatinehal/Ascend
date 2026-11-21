# Theme presets — design (short)

Date: 2026-11-21 · App: Ascend (Android) · Builds on `feat/routine-presets`

## Intent
Let people make Ascend look the way they like by choosing from preset colour themes. The user asked to focus on preset themes and to leave fonts out.

## Approaches
- **A. Colour presets, each with a dark and a light version** — the existing Dark / Light / Follow system switch still chooses the version. Keeps night use and system dark mode working; each preset keeps a distinct ideal/minimum pair; the widget can follow. Cost: ~12 colour schemes to define and check. **Chosen by the user.**
- B. Complete fixed looks that replace the Dark/Light switch — simpler, but a light look can't follow system dark mode.
- C. A plus a wallpaper-colours (Material You) preset — personal, but Android picks the colours, so ideal/minimum can lose contrast; Android 12+ only. Not now.

## What the user gets
- **Settings → Appearance** shows a row of preset swatches above the existing Dark / Light / Follow system chips. Each swatch shows the preset's two colours (ideal and minimum) and its name; the selected one has a check. Tapping one applies it immediately everywhere.
- **Presets** *(my call on names and number)*: **Gold** (today's look, the default), **Ocean**, **Forest**, **Rose**, **Lavender**, **Ember**. Each has a dark and a light version.
- **Everything follows the preset**: the main app, the block check-in screen, the assistant window, and the home-screen widget.

## Decisions (mine)
- Each preset defines: primary (ideal), secondary (minimum), their containers / on-colours, and a lightly tinted background and surfaces. Error colour and category colours stay as they are.
- The widget keeps using the **dark** version of the chosen preset, as it does today (it's always dark now).
- Stored as `themePreset` in settings (default Gold), so it's in backups; older backups restore as Gold.
- Fonts are out of scope.

## Constraints
- For every preset and both versions: primary and secondary text/icons must reach at least 4.5:1 contrast against the background (WCAG AA), checked by a unit test.
- Ideal and minimum must stay clearly different: at least 60° apart in hue, checked by the same test.
- Gold must look exactly like today (no visual change for people who don't pick anything).

## Non-goals
Fonts, custom colour pickers, wallpaper colours, per-category colour changes.

## Left to the builder
Exact colour values (within the constraints), swatch layout details in the calm 2.3/2.4 style, how the widget gets the preset.
