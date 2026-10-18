# Tasks

## 1. Lexicons and the category table

- [x] 1.1 Add `lexicons/app/activitytracker/feed/activityDetails.json`: optional summary fields with the same names and units as `feed.activity`, plus `startedAt` and `polyline`, an `activity` at-uri, `createdAt`, rkey = the activity's, and a description of where it's stored. Verify with the lexicon-auditor agent.
- [x] 1.2 Extend `actor/activityDefaults.json` with an optional `details` object (one visibility per category token) and remove `chartsVisibility`. Add `details` (`{visibility, record}` list) to `feed/defs.json#activityView` and `parts` (`{visibility, channels}`) to `feed/getActivityStreams.json`. Update the descriptions on `activity`, `activityStreams` and `activityZones`. Verify with the lexicon-auditor agent.
- [x] 1.3 Mirror these in `core/lexicon` (`ActivityDetailsRecord`, the defaults, view and output models, `Nsid.FEED_ACTIVITY_DETAILS`, tier space collections and `indexedCollections`). Verify with a round trip in `RecordSerializationTest`.
- [x] 1.4 Add `DetailCategory` and its maps from category to record fields, stream channels and lap/leg fields, plus the explicit `CORE` list. Verify with a `core/lexicon` test that fails when any `activityStreams` channel `knownValues` entry, `ActivityRecord` stat field or lap/leg field is in neither a category nor `CORE`.
- [x] 1.5 Add `feed.activityDetails` to the `space.athlete` declared tier collections. Verify with `scripts/dev.sh` and the local PDS: a fresh sign-in's grant lists it.

## 2. AppView indexing and serving

- [x] 2.1 Write a Flyway migration (the next V number) that creates `activity_details` keyed `(uri, origin)` with an `activity_uri` index, and re-keys `activity_streams` to `(uri, origin)`. Verify with `./gradlew :server-appview:test` (H2 PostgreSQL mode).
- [x] 2.2 Index `feed.activityDetails` from tier spaces only (skip and log repo copies), validate the rkey and author as streams do, and add it to `TIER_COLLECTIONS`. Index streams per origin without `RecordShadows`. Verify with indexer tests: a repo details record is ignored, and two tiers' streams coexist and are deleted independently.
- [x] 2.3 Serve `activityView.details` in `Views`: one batched query per page, filtered by the tiers each viewer is admitted to and by activity visibility. Verify with view tests for owner, mutual, follower, stranger and signed-out viewers on a Public ride with Followers power and Only-me heart rate.
- [x] 2.4 Serve merged streams in `ActivityStreamsViews`: the union of admitted rows, narrower channels winning, `parts` listing each tier, and `visibility` as the widest admitted part. Merge lap and leg fields by index. Verify with tests for the "Viewers get the tiers they're admitted to" scenarios and the old single-record activity.
- [x] 2.5 Verify that `since`/`until` filters and `getAuthorActivitiesByStart` use only the record's `startedAt`, with a test of a hidden-start activity filtered by a range inside its day.

## 3. Client data layer

- [x] 3.1 Replace `VisibilityChoice.charts` with per-category visibilities, with `capped()` and the default rules from the spec (an unset category starts at the activity default, and `chartsVisibility` is ignored). Update `ActivityDefaults` read and save. Verify with `ActivityDefaultsTest` cases: never set, only the activity default set, an old `chartsVisibility` ignored, and category capped.
- [x] 3.2 Add `ActivityLayout.split`, which turns a draft and its categories into the activity record (with startedAt rounded to local midnight and polyline dropped when hidden), details records and streams data for each tier, with core channels, lap/leg structure, lengths and sets at the activity's tier, and lap/leg category fields split with start and elapsed kept. Verify with unit tests for each `detail-visibility` storage scenario.
- [x] 3.3 Add `ActivityView.effectiveRecord()` and streams merging for the client, and derive each category's current tier from `details` and `parts`. Verify with unit tests, including an old single-record activity.
- [x] 3.4 Rework `ActivityRepository.publish`/`publishAll`/`mergeInto` and `PdsActivityStreamsStore` to queue the split layout narrowest first, one blob per tier record. Refuse narrower categories up front when the grant lacks `feed.activityDetails`. Verify with `ActivityRepositoryTest` and `FakeAthletePds`, asserting the records in each place.
- [x] 3.5 Replace `setStreamsVisibility` with saving the edited categories: compute the diff, write narrow places first, then strip wide ones as direct calls, so a failure leaves data in at least one place and a retry finishes it. Verify with `ActivityVisibilityTest`, including the interrupted narrowing scenario.
- [x] 3.6 When the first edit of an activity from before categories would move core channels to a wider tier, warn before saving and save only on confirm. Verify with an EditActivity presenter test for an old Public run with Only-me streams.
- [x] 3.7 Place zones at the narrowest of the Heart rate tier, the Power tier and training in `ActivityZonesStore`, move them on HR/Power changes only, and update session-start repair (old activities use the streams tier). Verify with `ActivityZonesTest` covering the modified `activity-zones` scenarios.
- [x] 3.8 Have session-start repair also recompute the layout for the athlete's split activities whose stored tiers disagree with their categories, never widening. Verify with a repository test that leaves a stale wide copy and checks it is removed.
- [x] 3.9 Check that export reads the owner's merged data, with an `ActivityExporterTest` case: a run with Only-me heart rate exports heart rate samples.

## 4. Screens and UI

- [x] 4.1 Add category state and events to the `:app-screens` contracts for Settings, Record, ManualActivity, ImportActivity (with batch) and EditActivity, removing the charts visibility fields. Verify that `./gradlew composeStabilityCheck` passes.
- [x] 4.2 Update the presenters to prefill from the defaults, cap on activity changes, and in edit offer only categories with data. Verify with presenter tests in `SettingsPresenterTest`, `ManualActivityPresenterTest`, `ImportActivityPresenterTest` and the EditActivity tests.
- [x] 4.3 Build `DetailVisibilityPicker` from `ui/adaptive` wrappers and wire it into `SettingsUi`, `RecordUi`, `ImportActivityUi`, `DiveBatchForm`, manual entry and `EditActivityUi`. Remove the "Who can see these charts" picker from `ActivityDetailUi` and the charts default from `SettingsUi`, with their strings. Verify with `./gradlew check` (detekt `UseAdaptiveComponents`) and a desktop hot-reload screenshot of Settings and Edit.
- [x] 4.4 Make the feed card, `ActivityDetailUi`, `ActivityChartsUi` and the lap table read `effectiveRecord()` and merged streams, leaving out hidden stats with no gaps, and show the owner a lock and tier on narrower categories. Verify with a desktop hot-reload screenshot as owner and as a second local account.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and `./gradlew :server-appview:test`, and verify both pass.
- [x] 5.2 Run an end-to-end check on the local stack (`scripts/dev.sh --seed alice` plus a second account): publish a Public run with Heart rate Only me, Power Followers and Start time Only me. Verify with `com.atproto.repo.getRecord` that the repo record has no HR/power fields and a midnight `startedAt`, and that the repo streams blob has no `heartRate`/`power`. Verify that the follower account sees power but not HR and the owner sees everything.
- [x] 5.3 Update `.agents/rules/data-and-auth.md` and the `CLAUDE.md` architecture notes for per-tier details and streams, then run `scripts/check-agent-sync.sh` and verify it passes.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed. This syncs `feed/detail-visibility` into the main specs.
