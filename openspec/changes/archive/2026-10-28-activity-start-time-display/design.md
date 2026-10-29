# Design

## Context

The activity page already formats its Start time figure in the UI: the presenter builds `FigureValue.StartTime(epochSeconds, utcOffsetMinutes, dateOnly)` (`ActivityPage.kt`), and `startTime()` in `composeApp/.../detail/StartTimeText.kt` renders it with `page_start_date` ("%1$s %2$d, %3$d") and `page_start_value` ("%1$s, %2$s"), the time from `timeFormat()` (`uses24HourClock()`). A missing offset is read as UTC (`?: 0`). `dateOnly` is detected as `startedAt == DetailCategory.localMidnight(startedAt, utcOffset)`, which also reads a missing offset as UTC, matching how the record was truncated. `ActivityCard` (`ui/ActivityCard.kt`) renders an `ActivityView` directly from `effectiveRecord()`; it has no presenter-built figures. The current time comes from `LocalClock` and the zone from `LocalTimeZone` (`ui/Format.kt`), both settable by `AppPreview`.

## Goals / Non-Goals

**Goals:**
- One formatter for both surfaces, so the card and page can't drift.
- Deterministic in tests: the clock and zone come only from composition locals.

**Non-Goals:**
- Locale-aware date patterns (month/day order, "at" placement) beyond the string resources. The app is English-only today; the wording lives in `strings.xml` so a translation can reorder it.
- Changing what the presenter or AppView serves, or any sort.

## Decisions

**Keep formatting in the UI, in `startTime()`.** "Today" depends on the current time, which only the UI reads (`LocalClock`); a presenter-built string would go stale past midnight and couldn't use resources. The card calls the same `startTime()` with a `FigureValue.StartTime` built from its record. `startTime()` is `internal` in `composeApp`, so `ui/ActivityCard` can import it from `app.detail` as is; move the file to `app/ui/` only if detekt or the package layout objects. Alternative: a new card-only formatter — rejected, two wordings would drift.

**Share the `dateOnly` check.** The page and card both need it. Put a small helper next to the formatter (e.g. a `FigureValue.StartTime` factory from an `ActivityRecord`, in `composeApp`) and use it on the card; the presenter keeps its own (it can't depend on `composeApp`). Both call `DetailCategory.localMidnight`.

**Relative day by the activity's offset.** `today = LocalClock.current.now()` read at the same zone as the start; "Today" when the dates match, "Yesterday" when the start's date is one day earlier. Any other date, including a future one (clock skew), uses the written date.

**Missing offset → viewer's zone (`LocalTimeZone`), except date-only.** This matches the export spec's fallback for local times and is closer to the truth than UTC for manual and GPX entries, the usual offset-less ones. A date-only start with no offset was truncated at UTC midnight, so it is read in UTC to land on the right day. This changes the page's current UTC reading of offset-less activities; the spec states the new behaviour.

**Wording in resources.** `page_start_value` becomes "%1$s at %2$s"; new `start_today` ("Today") and `start_yesterday` ("Yesterday"). `zones_history_today` isn't reused: different screen, may translate differently.

**The page's start is its own figure.** `ActivityPageContent.start` carries the Start time figure, mark included, and the stats list's Timing group no longer does. The header renders it with `StartLine` (the shared `startTime()` and a `VisibilityBadge` for the mark), the way `mapMark` sits by the map. Built by the presenter rather than from `activity.record` in the header, because the owner's page merges the Start time details record into the record it builds from, while `activity.record` holds only the local midnight when the start is narrower. The import preview renders the same `start` above the hero, so it keeps showing the start it would publish.

**Card placement.** A `bodySmall`, `onSurfaceVariant` line under the author's name in the author row's column, like Strava. It stays inside the clickable author area only if that reads naturally; otherwise directly below the row. Choose by screenshot.

## Risks / Trade-offs

- [A real start at exactly local midnight looks date-only] → Already true on the page today; the time is lost only for a start at 00:00:00 exactly. Accepted.
- [Screenshot references change for feed, profile and page] → Regenerate and review them in the same change.
- [Retained screens past midnight keep "Today"] → `LocalClock` is read at composition; a screen open across midnight updates on its next recomposition. Accepted, as with other relative times.
