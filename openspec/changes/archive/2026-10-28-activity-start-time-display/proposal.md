# Proposal

## Why

Feed cards don't say when an activity happened, so a feed ordered by upload date gives no sense of when each ride or run took place. The activity page shows a start time, but only as a full date ("May 31, 2026, 1:00 AM"), even for something done today. Athletes expect a relative "Today at 7:12 AM", in the time zone where the activity happened.

## What Changes

- Feed cards show the activity's start below the author, for example "Today at 7:12 AM", "Yesterday at 6:30 PM" or "October 26, 2026 at 2:00 PM".
- The activity page shows the start in its header, under the author and above the title, with the same wording, instead of as a row in the stats list's Timing group. "Today" and "Yesterday" replace the date when they apply, and " at " replaces the comma before the time. The import preview shows it above the hero.
- The day and time are on the clock of the place where the activity happened (its stored UTC offset). "Today" and "Yesterday" mean the day it is now at that place. With no stored offset (GPX, TCX, manual entries), the viewer's own time zone is used.
- A viewer who isn't shown the start time (Start time category narrower than the activity) sees only the day: "Today", "Yesterday" or the date.
- The format is fixed, with no setting. The time follows the device's 12- or 24-hour clock, as it already does.
- Feed order does not change: newest uploaded first.

## Capabilities

### New Capabilities
- `feed/activity-start-time`: how an activity's start date and time read wherever the activity is shown (feed card, activity page): the relative day, the date format, the activity's own clock and the date-only form.

### Modified Capabilities
- `feed/activity-page-layout`: the All stats list no longer holds the start, which moves to the page's header. `feed/detail-visibility`'s "Hidden start time keeps only the date" is unchanged; the new capability says how that date reads.

## Impact

- `:app-screens` `ActivityPageContent` gains `start`; `:app-presenters` `ActivityPage` fills it and drops it from the Timing group.
- `composeApp`: `detail/StartTimeText.kt` (`startTime()`, `StartLine`), `detail/ActivityDetailUi.kt` (header), `record/ImportPreviewUi.kt`, `ui/ActivityCard.kt`, `composeResources/values/strings.xml` (`page_start_value`, new Today/Yesterday strings).
- Other `startTime()` callers (combine picker, combine suggestion, saved suggestions) get the same wording.
- UI tests in `composeApp/src/jvmTest`, and screenshot references for the feed, profile and activity page in `:androidApp`.
- No lexicon, AppView or sort changes.
