# Design

## Context

- `ImportClock.localStart` is a `String` the record presenter writes with `LOCAL_START` (`yyyy-MM-dd HH:mm`); `ImportActivityTexts.zoneNotice`
  quotes it with `AppText.written`. Neither UI reads `localStart` itself: both draw `zoneNotice`.
- `DateText.at(epochSeconds, utcOffsetMinutes)` (round 2) writes a moment as `page_start_value` over `DateText.of` and an `AppText.Time`
  in that offset. `AppText.Time` is a moment plus an optional offset; each UI formats it on the device clock.
- `kotlinx.datetime.LocalDate` already appears in contract signatures (feed filter, gear, profile, record), so Swift export carries
  `kotlinx-datetime` types the contracts name; `LocalDateTime` comes through the same way.
- Checked by name across every Kotlin and Swift source set, `:app-fixtures` and the Android screenshot tests: `ImportPreview.dive`,
  `DivePreview`, `DiveGasPreview`, `DiveTankPreview`, `DiveSitePreview`, `DepthPreviewPoint`, `ShiftingPreview.gears` and
  `GearTimePreview` are read only by the record presenter that builds them and its tests. The samples pass `dive = null`. The batch row's
  `DiveRowExtras` (depth and dive number, which `ImportActivityTexts` reads) is a different type and stays.

## Goals / Non-Goals

**Goals:** the zone notice's start written by shared code; the dead dive and gear-time preview state gone; the wide iPad snapshot
stable alone.

**Non-Goals:** other `ImportPreview` fields no UI may read now (power, cadence, MTB dynamics, shift counts, elevation loss, device,
elapsed, `hasStreams`) are outside this change; they are recorded as a finding for a later cleanup. No change to how the zone is picked.

## Decisions

- **`DateText.of(dateTime: LocalDateTime)`**, next to `DateText.of(date)`: `page_start_value` over the date and
  `AppText.Time(dateTime.toInstant(UTC).epochSeconds, 0)`. A wall clock read as UTC at offset 0 formats back to the same hour and minute
  in both UIs, so no new `AppText` case or UI code is needed. Alternative: a new `AppText.LocalTime(hour, minute)` case, which both
  resolvers would have to learn; not needed.
- **`ImportClock.localStart: LocalDateTime`**, not a pre-built `AppText`: the state carries data and the text is chosen in
  `ImportActivityTexts`, as with `ImportStart`.
- **Delete whole types** (`DivePreview` and its parts, `GearTimePreview`, `DepthPreviewPoint`, and `ImportPreviewCharts.kt` once empty)
  with their mapping code in the presenter; drop `ShiftingPreview.gears` but keep the type for its shift counts (out of scope above).
  Helpers left without callers (`StreamAnalytics.everyNth` if so) go too.
- **iPad snapshot**: reproduce alone first, compare the failing draw with the reference, and fix the cause in the test harness or the view
  (for example, settle the window's size and safe area before the first draw). Tolerance and sample stay.

## Risks / Trade-offs

- A `LocalDateTime` in a contract signature could become a Swift export stub if `kotlinx-datetime` were hidden; the build phase's stub
  check would fail the iOS build, so it can't pass unnoticed.
- The iPad cause may lie in UIKit's attach timing; if it can only be settled in the harness, every iPad-sized snapshot gets that fix.
