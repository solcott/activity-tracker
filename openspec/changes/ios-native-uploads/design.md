# Design

## Context

What exists, from the code:

- **The contract** (`feature/uploads/api`). `UploadsState` holds `content: ContentState<UploadQueueView?>`, `importsPaused`, `serverPausedUntil: Instant?`, `timeLeft: Duration?`, `isOpenOnly` (desktop and web only), `expandedImports` and `busy` (`ImmutableList<String>`: rkeys being cancelled or sent again, and import ids whose rest is being cancelled), `changingPause`, `clearing`, `confirmingCancel` (an rkey) and `confirmingCancelRest` (an import id), `error: UiError?`. Events: `Back`, `Retry`, `Cancel`/`ConfirmCancel(rkey)`, `CancelRest`/`ConfirmCancelRest(batchId)`, `Dismiss`, `SendAgain(rkey)`, `PauseImports`, `ResumeImports`, `ClearFinished`, `ShowAll(batchId)`, `OpenActivity(uri)`.
- **The queue model is `:core-data`'s** (`UploadQueue.kt`): `UploadQueueView` (`current`, `own`, `imports`, `finished`, `refusedOther`, with `isEmpty` and `hasFinished`), `UploadImport` (`source`, `savedAt`, `saved`, `skipped`, `uploaded`, `refused`, `live`, `finished`, `save: ImportSaveStatus?`), `UploadItem` (`uri: AtUri?`, `isEdit`, `state: UploadState`, `title`, `sport`, `startedAt`, `sent`, `total`, `cancellable`), `RejectedChange` (`rkey`, `kind`, `title`, `sport`, `startedAt`), the sealed `UploadSource` (`Export(kinds)`, `Files(first, count)`) and `ImportSaveStatus` (`Saving`, `Stopped`). Its lists are plain Kotlin `List`s, not immutable ones.
- **The shared UI builds text and decisions in composables.** `UploadsText.kt` picks the item state's words, the item and change names (title; or sport and the ISO day in `LocalTimeZone`; or "An activity"; "Updating …" for an edit), the server pause through `untilText` (`:app-common-ui`: "until" or "until tomorrow at" by comparing days with `LocalClock`), the time left as one of three plurals rounded to the largest unit, and the source's name. `UploadsUi` decides in place when Clear can be used, when Pause imports shows, which of an import's live items show before Show all (`IMPORT_PREVIEW`, 20), when Cancel rest shows, the saved line through `startTime` (`:app-common-ui`: "Today", "Yesterday" or "October 26, 2026", then " at " a time), the saving and stopped lines, the counts joined with " · ", and the cancel-rest message for an import still saving. `UploadsRows` decides when an item opens, offers Send again or Cancel, and is busy.
- **The pieces this reuses**: `ContentStateView`/`RefreshStatusView`, `ErrorMessage(layout: .row)`, `.listStatusRow()`, `pageTitle(_:actions:)`, `AppSecondaryButton(small:)`, `AppTextButton`, `.alert(_:isPresented:)` bound to a state's question (`GearCollectionView`), `AppText.Time` for a time of day, `Labels.sport`, `formatWhole`.
- **Samples**: `UploadsSamples.all`, the content-loading kinds over everything at once, and empty, own only, an import part way with a time left, expanded, both pauses, refused, finished, saving, stopped, the three confirmations, a failed cancel and a send in flight. `Fixed.NOW` is the samples' clock; the Compose previews and tests read it as `LocalClock` in UTC.

## Goals / Non-Goals

**Goals:**

- The queue, native, saying and offering exactly what the shared page does, in the same order.
- Every rule it follows written once in Kotlin, including a rough length of time, which the feed and the activity stats will reuse.

**Non-Goals:**

- An `AppText` for a date in general. The queue needs "Today", "Yesterday", a written date and "tomorrow at", and the day rule moves to `:app-common-api` for it (decision 4); the feed's start times, with an activity's own UTC offset, stay the shared UI's until the feed goes native.
- New design-system pieces: built from what exists.
- `isOpenOnly`: false on iOS (uploads run in the background there), so the native page draws its note only because the samples set it, the same words.
- Pull to refresh: the shared page has none.

## Decisions

### 1. Probe first

Throwaway Swift tests on a cloned iPhone 17 simulator (iOS 26.0), and the generated Swift read, recorded in the roadmap under "Phase 4 probes (uploads)":

- **`kotlin.time.Duration` reaches Swift as a class** (`ExportedKotlinPackages.kotlin.time.Duration`, boxed from the value class), with `inWholeSeconds` and the other whole-unit getters, and `UploadsState.timeLeft` is a `Duration?`. So a function taking a `Duration` is callable from Swift. The native page still never handles one: it asks `UploadsTexts.timeLeft(state)`.
- **`UploadQueueView`, `UploadImport`, `UploadItem` and `RejectedChange` already have Swift types** (the samples name them), and their `List` fields are generated as typed arrays (`[UploadItem]`, `[UploadImport]`, `[RejectedChange]`, `[String]` for `Export.kinds`). The open question was the empty ones: an empty persistent list stops the app through a `List` getter (`ios-zones-contracts`), and the empty queue's lists are `emptyList()`. **Found:** they read. Kotlin's `emptyList()` (and `listOf()` from `take` and `drop`) reads through a `List` getter as an empty Swift array; only the immutable library's shared empty instance stops the app. So the view reads the model's fields directly, and `shownLive(group)` returns a plain `List`.
- **Found, each as expected:** `content.data` casts with `as? UploadQueueView` (a class, not a list), `UploadSource` and `ImportSaveStatus` cast to their cases, `UploadState` and `RejectedChange.Kind` read as Swift enums, and `AtUri.rkey` reads.
- **`kotlinx.datetime.TimeZone` and `kotlin.time.Instant` can be made in Swift**: `TimeZone.Companion.shared.of(zoneId:)` and `currentSystemDefault()`, `Instant.Companion.shared.fromEpochMilliseconds(epochMilliseconds:)`, already exported because other signatures name them. `fromEpochSeconds(epochSeconds:nanosecondAdjustment:)` is ambiguous from Swift (an `Int64` and an `Int32` overload), so Swift uses milliseconds.
- The sample's `timeLeft?.inWholeSeconds` read 93,600 (26 h); an `#expect` comparing it with the literal `26 * 3600` failed while printing 93600 on both sides, so tests compare with a typed `Int64`.

### 2. A length of time is a plural chosen in Kotlin, not a new `AppText` case

`AppText.Time` is a case because a time of day follows a device setting (the 12- or 24-hour clock), which each UI reads. A rough length of time has no such setting: which unit, how it rounds, and its words are all the shared rule. So it is `DurationText.approximate(duration): AppText` in `:app-common-api` (`app.text`), returning a plural with the rounded quantity as its argument: `duration_minutes` ("40 min", whole minutes, at least 1, under an hour), `duration_hours` ("3 h", rounded, under a day), `duration_days` ("1 day", "2 days", rounded). Both resolvers already read plurals, so nothing changes in `TextSource` or `AppText.string()`. The queue's time left is `uploads_time_left` ("About %1$s left") around it, and the three `uploads_time_left_*` plurals go; the words are the same. A new sealed case was rejected: its rounding would have to be written in each resolver, or turned into these plurals inside `resolve` and again inside the Compose resolver. The clock form `StatText.duration` ("1:23:45") stays where it is; when the feed or the stats need another form of a length of time, it joins `DurationText`.

### 3. The queue's text in `UploadsTexts`, its conditions on `UploadsState`

- `UploadsTexts` (`:feature-uploads-api`, `app.uploads`): `state(item)`, `itemName(item, zone)`, `changeName(change, zone)`, `source(source)`, `saved(group, now, zone)`, `saveStatus(group)` (saving or stopped, or null), `progress(group)`, `counts(group)` (refused and skipped, joined by a new `uploads_import_counts` "%1$s · %2$s", or null), `showAll(group)`, `cancel(name)`, `sendAgain(name)`, `cancelRest(group)` (the spoken labels), `serverPausedUntil(until, now, zone)`, `timeLeft(state)`, `cancelRestMessage(state)`. Numbers go through `formatWhole`; a sport through `LabelText.sport`.
- `UploadsState` getters and functions: `showsClear`, `canClear`, `showsPauseImports`, `shownLive(group)` (a copy: the first 20 until shown whole), `showsShowAll(group)`, `offersCancelRest(group)`, `isBusy(key)`, `opens(item)`, `offersSendAgain(item)`, `offersCancel(item)`.
- `UploadsUi` and `UploadsRows` ask them; `UploadsText.kt` keeps only thin composables resolving them with `LocalClock` and `LocalTimeZone`. Tests in `:feature-uploads-api` `commonTest` over the samples.

### 4. Day words from the clock the UI passes

"Today", "Yesterday", "tomorrow at" and an item's day depend on the current date in a zone, which shared code can't read (a preview, a test and a snapshot each fix their own). So the functions that need one take `now: Instant` and `zone: TimeZone`, which Compose passes from `LocalClock` and `LocalTimeZone`, and Swift from a clock in its environment (decision 5). The day rule itself moves to `:app-common-api` as `DayText`: `day(date, today)` ("Today", "Yesterday", or "October 26, 2026" with `month_long_*`) and `until(until, now, zone, today, tomorrow)` (a named string with `AppText.Time`), and `startTime` and `untilText` in `:app-common-ui` resolve them, so the feed's words are unchanged and written once. A time of day inside them stays `AppText.Time`, which each UI formats in its own zone.

### 5. A clock in the Swift environment

`iosApp/iosApp/Text/ShownClock.swift`: `ShownClock` holds a Foundation `TimeZone` and a function giving the current `Date`; `\.clock` in the environment, the device's by default. It gives Kotlin its `Instant` and `TimeZone` (by identifier), and a text source whose `time(epochSeconds:)` formats in the same zone, so the day and the time of day are read on one clock. Previews and snapshots set `Samples.clock`, `ShownClock.fixed(atEpochSeconds:zone:)` at `Fixed.NOW` in UTC,, so they read as the Compose previews do on any machine and day. `CatalogText` gains an initialiser taking the zone; `CatalogText.shared` stays the device's.

### 6. The view

`UploadsView`: `ContentStateView(state.content, as: UploadQueueView.self, errorKind: .LoadUploads)`, then an `.insetGrouped` `List` in the shared page's order: `RefreshStatusView`; the action error (`ErrorMessage(layout: .row)`); a banners section (the athlete's pause with `AppSecondaryButton(small:)` Resume, or a Pause imports row button; the server's pause, the time left and the open-only note as secondary text); then a section per group with the shared page's headers (Uploading now, Your activities, each import with its lines, Finished, Other changes refused), the empty message as a secondary status row. A row: name and the state line (red when refused) leading, Send again and Cancel as small text buttons trailing, outside the row's button, with their spoken labels; an uploaded row is a button sending `OpenActivity`. Show all is a row button. `pageTitle(Text(.uploadsTitle)) { Clear }`, Clear an `AppTextButton` with `uploads_clear_description` as its accessibility label. Two alerts bound to `confirmingCancel` and `confirmingCancelRest` (setting false sends `Dismiss`), destructive Cancel upload / Cancel rest and a cancel-role Keep. Registered in `ScreenRegistry.app` in one commented block; `KotlinTypes.swift` aliases the contract and the queue model in one block, with the module imports.

### 7. Samples, previews, snapshots

`Samples.uploads` over `UploadsSamples.all`; `#Preview`s with `SamplePages` inside a `NavigationStack` and the fixed clock; `UploadsSnapshotTests` over every sample, light and dark, at the iPhone size, with `Snapshots.barButtons` (Clear is in the bar); `UploadsTextTests` (Swift Testing) for the texts and conditions read through Swift over the samples. No new Kotlin samples are needed: `all` already has every state the page draws.

## Risks / Trade-offs

- [Two clocks in previews] Swift's previews need the fixed clock set; a view that forgets it reads the real day. → The preview and the snapshot suite both set `Samples.clock`, and `UploadsTextTests` asserts the fixed words.
- [The empty queue on the test account] The walk sees the empty page and, at most, an item it saves itself. → Record every state it couldn't see under "Not verified"; never cancel or pause without the user's go-ahead.
- [Moving `startTime`'s day rule] touches the feed's start times. → The Compose resolvers keep their signatures; the Android screenshot suite and the existing UI tests hold the words.
