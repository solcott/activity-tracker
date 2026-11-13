# Design

## Context

See proposal.md - Why. `ZoneRows.of` (`CardLines.kt`) already gives each zone's label, name, range, time and fraction as `AppText`s, and
the native rows draw them; the Compose `ZoneBars` (`:ui-designsystem`, which can't see `AppText`) takes strings in a `ZoneBar` and words
the rest itself. `FigureTexts.showsMin`, `StatText.missing`, `CommentsTexts.moreReplies` and `count` already exist and iOS asks them.

## Goals / Non-Goals

**Goals:** each listed rule has one home in `:app-common-api`; both UIs ask it; the dead helpers go.

**Non-Goals:** restyling the native zone rows into Compose's bars (colours, widths), or changing a figure row's value (neither UI splits
it into runs).

## Decisions

- **Zones**: `ZoneRow` gains `title`, `percent` and `spoken`, computed in `ZoneRows.of`. The percent rounds `fraction * 100`, and a
  non-zero share that rounds to 0 reads `chart_zone_percent_under` ("<1%"); others `unit_percent` over the whole number. New strings:
  `chart_zone_title` ("%1$s %2$s"), `chart_zone_percent_under`, `chart_zone_spoken` ("%1$s, %2$s: %3$s, %4$s"). `ZoneBar` keeps strings
  and takes `title`, `percent` and `spoken` in place of `label` and `name`, so the design system stays free of `AppText`. The native row
  keeps its label and name in two weights (the title is Compose's single line), adds the percent and labels the row with `spoken`.
- **Figure runs**: `FigureParts.of(value): List<FigurePart>` (`text`, `isNumber`) in `app.detail`, over `FigureParts.unbreakable(value)`
  (every space but the first after the number non-breaking, a word joiner after each slash) and the number pattern both copies used.
  `withSmallUnits` builds its `AnnotatedString` from the parts; Swift's `FigureRuns.text` (renamed from its `FigureParts`, whose name the
  Kotlin object now takes) concatenates `Text` runs from them. A value with no number reads in the number's style throughout.
- **Plurals and table**: direct swaps to the existing shared functions.
- **Removals**: checked by name across Kotlin and Swift; `showsLength`'s test moves to `:app-common-api` against
  `CardStatsText.showsLength`.

## Risks / Trade-offs

- [The native zone rows grow a column] → the percent sits after the time in the same row, secondary, monospaced; snapshots show it.
- [Swift export of a `List<FigurePart>`] → reads as `[FigurePart]`, as `List<ZoneRow>` already does; `FigurePart` gets an alias.
