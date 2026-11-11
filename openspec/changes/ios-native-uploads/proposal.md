# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time, cheapest infrastructure first. The upload queue (`UploadsScreen`, data/upload-queue) is a self-contained list: no map, no activity card, no chart. What it needs beyond the design system that exists is text the Compose UI builds in composables (`UploadsText.kt`), among it the app's first length of time ("About 3 h left"), which the feed and the activity stats will want too. So it is the next cheapest page, and it gives the length of time its shared home.

## What Changes

- **The upload queue is native on iOS.** An inset grouped list showing what the shared page shows, in the same words: Clear in the bar (unavailable with nothing finished or while clearing); the failure of the last action; imports paused by the athlete with Resume, or Pause imports while an import uploads; the server's pause until a time of day ("until tomorrow at" past midnight); the time left; the item uploading now; the athlete's own items; each import under its source, when it was saved, its saving or stopped line, its progress and its refused and skipped counts, with Cancel rest, its first 20 waiting activities and Show all; the finished items; other refused changes; and the empty queue's message. Each item has its name and state, Send again where refused and Cancel where waiting or refused (unavailable while busy, read to VoiceOver with the item's name), and an uploaded one opens its activity. The content-loading states of ui/content-loading.
- **Cancelling asks first in the system's alert**, bound to the state's question as the gear collection's delete is: "Cancel this upload?" with Cancel upload and Keep, and "Cancel the rest of this import?" with the words for an import still saving. Closing the alert any other way dismisses it.
- **Kotlin chooses the text and conditions, both UIs ask.** `UploadsText.kt`'s choices move to `UploadsTexts` in `:feature-uploads-api` (each item's name and state, a refused change's name, an import's source, saved, saving, progress and counts lines, Show all, the spoken Cancel and Send again, the pauses, the time left, the cancel-rest message), and when each control shows or can be used goes to getters on `UploadsState`. Day words that depend on today ("Today", "tomorrow at") are decided there from the clock reading and time zone each UI passes, and the day rule that `startTime` and `untilText` use moves to `:app-common-api` (`DayText`). No visible change on Android, desktop or web.
- **A length of time has shared text.** `DurationText.approximate` in `:app-common-api` reads a length of time rounded to its largest unit ("40 min", "3 h", "2 days"); the time left is "About %1$s left" around it. The three `uploads_time_left_*` plurals become `duration_minutes`, `duration_hours`, `duration_days` and `uploads_time_left`, with the same words.
- **Samples, previews and snapshots** of the native page over every `UploadsSamples` sample, a native clock reading and zone in the environment so previews and snapshots show the samples' fixed times, Swift Testing tests of the text and conditions, and the registry entry.

Out of scope: the feed's upload row (the feed's change), the import screen that opens the queue, pull to refresh (the shared page has none), any new design-system control, and an `AppText` for dates in general (the queue's day words are its own functions until the feed needs the full rule).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `data/upload-queue`: the queue is drawn natively on iOS, with cancelling confirmed in the system's alert.
- `dev/shared-display-text`: the queue's text and conditions, and a length of time, are chosen in shared code.
- `dev/ios-swift-interop`: a contract model from `:core-data` holding plain Kotlin lists, empty ones included, reaches Swift as typed arrays.

## Impact

- **`:app-common-api`:** `DurationText`, `DayText`; tests. **`:app-common-ui`:** `startTime`'s day and `untilText` ask `DayText`; `SharedTextNamesTest` covers `DurationText`.
- **`:feature-uploads-api`:** `UploadsTexts`; getters on `UploadsState`; tests. **`:feature-uploads-ui`:** `UploadsUi` and `UploadsRows` ask them; `UploadsText.kt` shrinks to their resolvers.
- **`:ui-resources`:** the duration plurals and `uploads_time_left`; the iOS catalog regenerated.
- **`iosApp`:** `UploadsView`, a clock environment for day words, type aliases, samples, previews, snapshot and text tests, a registry entry.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (uploads)" and findings section, its open items (lengths of time and dates); `.agents/rules/compose-screens.md`.
- **Risk carried:** the test account's queue is empty unless the walk saves something, and the walk must not cancel a real upload or pause imports without the user's go-ahead; snapshots compare only on iOS 26.0; iOS 18 can't be walked signed in.
