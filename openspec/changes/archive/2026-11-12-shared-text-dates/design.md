# Design

## Context

Most dates and times in both UIs already come from shared `AppText` functions (`StartText`, `DayText`, `DateText`, `DurationText`,
`AppText.Time`), since earlier native rounds moved each one when a native view first needed it. The inventory found the rest. A few
places in Compose still format a date or length of time themselves, though a shared function already exists for it and the native view
asks that function. One Swift view does the same. And shared `-api` code writes five kinds of dates as ISO text (`LocalDate.toString()`).
Edit profile keeps its birth date as `YYYY-MM-DD` text in `State`, and both UIs show that text as it is.

## Goals / Non-Goals

**Goals:**
- One rule for a calendar day written out, used by every date a UI shows.
- No date or length-of-time format chosen in a composable or a Swift view.

**Non-Goals:**
- The activity charts' time axes, pace values and readouts in Compose. They are chosen with the Vico chart's other labels
  (`ChartUnits`, `ActivityChartsUi`), and the native chart already reads them from `CardLines`. Moving them means moving the Compose
  chart onto `CardLines.chart`, as `SegmentProfileChart` did, which is the charts round in the inventory.
- How a time of day is formatted. Each UI formats `AppText.Time` on the device's clock by contract. Compose writes "07:05" on a 24-hour
  clock and "7:05 AM" otherwise. Swift uses the locale's short time.
- The dead dive code, which goes in the dive round.

## Decisions

- **`DateText.of(date: LocalDate)`** joins `date(year, month, day)` and `isoDate(text)`. It is a named function and not an overload of
  `date`, so Swift export gives it a clear name. `EditGearTexts.date`, `GearPageTexts.day`, `FeedFilterTexts.range` and `UploadsTexts`' activity name call it. Threshold history's
  `shortDate` ("Mar 2") moves into `DateText` as `monthDay`, so every date rule has one home. An
  alternative was to keep ISO in forms ("a form shows what it stores"). That was rejected because the same gear date read "Jul 1, 2026"
  on a board and "2026-07-01" on its own form, and both date pickers already present a calendar.
- **Edit profile's birth date: `EditProfileTexts.birthDate(state): AppText?`.** It reads `state.birthDay` (the parsed `LocalDate`) and is
  null when unset. Compose passes it to `PastDateField(shown =)`, and Swift reads it in place of `state.birthDate`. The stored text and
  the events stay as they are.
- **Compose asks the existing functions.** `CombinePickerTexts.detail`, `ActivityPageTexts.liftRides` and `ActivityPageTexts.liftTime`
  are already what iOS draws, so Compose's output for them is the same text.
- **Swift's picker bounds come from `ShownClock`.** `ShownClock` gains a `date` (the current `Date` on that clock), and the three
  pickers' `...Date()` become `...clock.date`. It is not text, but it was the one place a Swift date followed the machine rather than
  the clock a preview fixes.

## Risks / Trade-offs

- [The written-out date changes six visible strings on every platform] → The change and the roadmap list each one. Screenshot references
  and snapshots that show them are rerecorded and looked at.
- [`DateText.of` reads a `LocalDate` built in the viewer's zone (the gear page's mounts)] → Unchanged: the zone was already applied
  before `toString()`.
