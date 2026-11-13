# Design

## Context

`LOCAL_START` (`ImportActivityPresenter.kt`) formats a `LocalDateTime` as `yyyy-MM-dd HH:mm`. Four callers use it for text a reader
sees (`AddingRecording.preview`, `batchRows`, `duplicateInfo`, `ImportRow.compared`) and one for the import clock (`importClock`). The
texts wrap the string in `AppText.written`, so neither UI can format it. Both UIs already format `AppText.Time` on the device's clock, in
a given UTC offset (`feed/activity-start-time`).

## Goals / Non-Goals

**Goals:**
- An import's start reads like every other start: a written-out date, and a time of day on the device's 12- or 24-hour clock.
- The state carries what the text needs (the moment and its offset), not a format.

**Non-Goals:**
- The import clock (`ImportClock.localStart`). It is a zoneless wall clock that stays the same while the athlete moves the zone, so it is
  not a moment; the task keeps it as text.
- Day words ("Today at …"). `DayText` needs the current moment, which none of these texts take, and an import's rows are grouped under
  month headings, where a written-out date reads better. `StartText` also writes the month long ("October 26, 2026"); a batch row is
  dense, so it uses `DateText`'s short month.

## Decisions

- **`ImportStart(epochSeconds: Long, utcOffsetMinutes: Int)`** in `:feature-record-api`, one field in place of the string, so
  `CompareValues` and the samples stay simple. The offset is the zone's offset at that moment (`TimeZone.offsetAt`), so a row placed in
  the picked zone reads its local wall time. Two `Long`/`Int` fields cross Swift export with no bridge. Reusing
  `FigureValue.StartTime` was rejected: it is a figure's value, with a `dateOnly` an import never has.
- **`DateText.at(epochSeconds, utcOffsetMinutes)`**: `page_start_value` ("%1$s at %2$s") over `DateText.of` of the day in that offset
  and `AppText.Time(epochSeconds, utcOffsetMinutes)`. It lives in `DateText` with the other written-out dates, not in the record
  texts, since nothing about it is the import's.
- **The batch test that moves a file's zone** compares the rows' local wall times (each start read in its own offset), as it compared
  the text before.
- **The kept-shared dialog**: remove the Compose branch and `ArchiveHandOffResult.Shared` (no `actual` produces it). Keep the sample
  (`exportSharedAsk`), which iOS's `AccountSnapshotTests` draws, and leave it out of the Android screenshots with a note, deleting its
  reference; the Compose page would draw it as `exportReady`.

## Risks / Trade-offs

- [Compose pads a 24-hour hour ("07:02") and Swift follows the locale] → The existing `AppText.Time` contract; the roadmap already
  lists it.
- [Text tests in both languages asserted ISO starts] → Updated, with 12- and 24-hour cases and a start on another offset.
