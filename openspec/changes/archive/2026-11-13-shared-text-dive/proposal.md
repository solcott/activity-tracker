# Proposal

## Why

Round 3 of moving rule-chosen text into shared code (the roadmap's "Dive tables and dead dive code" item). `:app-common-ui` still holds
a second, Compose-only way of writing a dive's figures, its computer's settings, a depth chart's readout and a table row: `DiveStats.kt`,
`DiveSettingsStats.kt`, `DepthProfile.kt` and `LapTableUi.kt`'s `spokenRow` and `cell`. Nothing calls any of it. The activity page in
both UIs reads a dive through `FigureTexts`, its charts through `CardLines` and its laps through `LapTableTexts`, and the import
screen's preview is the activity page, so the dive preview's depth chart that `DepthProfile` drew is gone too. Dead text helpers are a
second rule that can drift from the shared one, and they hold strings nothing else uses.

## What Changes

- **Delete the dead Compose dive code.** `DiveStats.kt` (`diveCardStats`, `diveOverviewStats`, `conditions`, `gas`, `airUsed`,
  `consumption` and the depth and time helpers), `DiveSettingsStats.kt` (`diveSettingsStats`, `decoModel`, `bar`, `decoModelName`),
  `DepthProfile.kt` (`DepthProfile`, `rememberDepthLabel`) and `DiveFormat.kt`, whose only callers were those files.
- **Delete `spokenRow` and `cell`** from `LapTableUi.kt`; `LapTable` and `TableCells` stay.
- **Delete the strings only that code used:** `stat_air_temp`, `stat_desaturation`, `stat_dive_number`, `stat_end_n2`,
  `stat_highest_ppo2`, `stat_max_ascent_rate`, `stat_n2_value`, `stat_no_fly` and `stat_water_type` (the page names those figures with
  its own `page_*` strings), and regenerate the iOS String Catalog. Every other string the deleted code used stays: shared code names it.

Visible text changes: none, on any platform. Nothing deleted was on a screen.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: a dive's figures, settings and lap rows have one text path, the shared one.

## Impact

- `:app-common-ui` (four files deleted, one trimmed) and `:ui-resources` (`strings.xml`); `iosApp/iosApp/Localizable.xcstrings`
  regenerated. No test, sample, screenshot or snapshot used the deleted code.
- `DivePreview.profile` (`:feature-record-api`) and the presenter that fills it are left: nothing draws it now either, but it is
  contract and presenter, not text, and outside this round.
- Roadmap `openspec/explorations/ios-native-ui.md`: the item ticked and a findings section.
