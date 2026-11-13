# Proposal

## Why

Round 4 of moving rule-chosen text into shared code (the roadmap's "Stats and units" item). The activity page's zones, its avg/max table
and its figures still choose text in a UI: the Compose zone bars write a zone's percent ("<1%", not through a string), its label and its
spoken sentence themselves, the avg/max table decides its Min column and dash itself, a figure's number and unit runs are split twice (and
only Compose keeps a unit from wrapping inside itself), and two comment plurals are built in the composable, one with an ungrouped count.
Two Compose helpers nothing calls are left over.

## What Changes

- **A zone's percent, title and sentence**: `ZoneRow` gains `title` ("Z2 Endurance"), `percent` (its share rounded, "<1%" for a sliver,
  through `unit_percent`) and `spoken` (label, name, range, time and percent as one sentence), chosen in `ZoneRows.of`. The Compose bars
  and the native zone rows draw them; the native rows now also show the percent and read the shared sentence.
- **The avg/max table**: the Compose table asks `FigureTexts.showsMin` and falls back to `StatText.missing`, as the native table does.
- **A figure's runs**: one shared split, `FigureParts.of(value)`, gives a value's number and unit runs over text a narrow column may break
  only between number and unit. Compose's `withSmallUnits` and the native figure draw from it, so iOS no longer wraps inside a unit
  ("/100 yd"). Its local `unbreakableUnit` and Swift's `FigureParts.split` are removed.
- **Comment plurals**: Compose's "View n more replies" asks `CommentsTexts.moreReplies` (its count now grouped, "1,234") and the comments
  header asks `CommentsTexts.count`, as iOS does.
- **Removed, unused**: `elevationStat` (and the `showsLength` wrapper only it and a test used) and `GearTimeChart`.

Visible text changes:
- iOS: an activity's heart rate and power zone rows show each zone's percent, and VoiceOver reads a row as the Compose sentence. A figure's
  unit no longer wraps inside itself in a narrow column.
- Android, desktop and web: "View n more replies" groups a count over 999. Nothing else changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: a zone's percent and sentence, the avg/max table's Min column and dash, a figure's number and unit runs and
  the comment counts are chosen once.

## Impact

- `:app-common-api` (`ZoneRows`/`ZoneRow`, new `FigureParts`), with tests; strings in `:ui-resources` and the String Catalog.
- `:ui-designsystem` (`ZoneBar`, `ZoneBars`); `:app-common-ui` (`ActivityChartsUi`, `PageCardsUi`, `PageFigures`, `PageUi`,
  `CommentThreadUi`, `CommentsUi`, `ActivityStats`, `GearTimeChart` removed), with tests; the Android chart screenshot's sample.
- Swift: `ActivityPageCards.swift`, `KotlinTypes.swift`, the figure tests.
- iOS 26.0 activity page snapshots with zones; no Android reference is expected to change.
- Roadmap `openspec/explorations/ios-native-ui.md`: the item ticked and a findings section.
