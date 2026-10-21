# Tasks

## 1. Lexicons and shared geometry

- [x] 1.1 Add optional `status` (knownValues proposed, active, duplicate, archived) and `duplicateOf` (at-uri) to `segmentView`, and `status` to `segmentViewBasic`, in `lexicons/app/activitytracker/feed/defs.json` and `Views.kt`; verify `SegmentRecordsTest` round-trips views with and without them
- [x] 1.2 Add the `feed.checkSegment` procedure lexicon (input `sport`, `line`; output `duplicates`, `remaining`) and its `XrpcProcedure` in `Queries.kt`; verify a lexicon round-trip test in `core/lexicon`
- [x] 1.3 Add each sport group's minimum length (500 m ride, 200 m others) beside `SegmentSportGroup` in `core/lexicon`; verify `SegmentSportTest` pins every group
- [x] 1.4 Add `SegmentGeometry.duplicates(a, b)` (length ratio ≤ 1.2, endpoints within 30 m, banded discrete Fréchet ≤ 30 m on 10 m resamples) in `core/location`; verify `SegmentGeometryTest` covers jittered copy, reversed line, longer superset, a detour loop, and a 50 km pair within a time budget
- [x] 1.5 Run the `lexicon-auditor` agent on `feed.defs` and `feed.checkSegment` and fix what it reports

## 2. AppView: statuses and duplicates on indexing

- [x] 2.1 Add `V30__segment_status.sql` (`status`, `duplicate_of`, `first_indexed_at` backfilled from `indexed_at`, indexes on `(status, sport)` and `duplicate_of`) and the columns in `db/Tables.kt`; verify `:server-appview:test` migrates on H2
- [x] 2.2 In `upsertSegment`, set `first_indexed_at` only on insert and apply the per-group minimum length; verify `SegmentsTest` drops a 350 m ride but indexes a 350 m run, and an edit keeps `first_indexed_at`
- [x] 2.3 Refuse a new URI when its author has 10 segments first indexed in the last 24 h; verify `SegmentsTest` drops the eleventh and still indexes a rename of an existing one
- [x] 2.4 Decide status on insert or line/sport change: candidates by start and end cells, same sport, `proposed`/`active`, earlier `first_indexed_at`; mark `duplicate` with `duplicate_of` and delete its efforts, else `proposed` (an active segment with a changed line goes back to `proposed`); verify `SegmentsTest` covers duplicate, reversed, other sport and superset cases
- [x] 2.5 Re-check a segment's duplicates when it's deleted or its line or sport changes, earliest first, enqueueing a `segment` job for each that becomes `proposed`; verify `SegmentsTest` promotes a duplicate to `proposed` and matches past tracks after the original is deleted

## 3. AppView: matching and status pass

- [x] 3.1 Restrict `replaceEfforts` candidates to `proposed`/`active` and make `matchSegment` skip other statuses; verify `SegmentMatcherTest` gives no efforts on a duplicate or archived segment and drops an activity's archived efforts on recompute
- [x] 3.2 Promote `proposed` segments at the end of `replaceEfforts` when distinct athletes reach `SEGMENT_ACTIVE_ATHLETES` (new `AppViewConfig` field, default 3); verify `SegmentMatcherTest` promotes on the third athlete, counts an Only me effort, and keeps `active` after efforts are removed
- [x] 3.3 Add `updateStatuses(now)` to `SegmentMatcher.start` (on start and hourly): promote proposed segments at the threshold, archive proposed ones first indexed over 180 days ago, re-check their duplicates; verify `SegmentMatcherTest` with an injected clock
- [x] 3.4 Document `SEGMENT_ACTIVE_ATHLETES` wherever `AppViewConfig`'s other env vars are documented, and verify the AppView starts with and without it

## 4. AppView: serving

- [x] 4.1 Serve `status` and `duplicateOf` in `segmentView`, and `status` in `segmentViewBasic`, from `SegmentViews`; verify `SegmentsTest` and `SegmentEffortsTest` read them from `getSegment` and `getActivitySegments`
- [x] 4.2 Filter `searchSegments` to active, plus proposed for their creator or athletes with an effort on them, never duplicate or archived; verify `SegmentsTest` with the creator, a rider, a stranger and an anonymous viewer
- [x] 4.3 Serve `feed.checkSegment` in `api/Routes.kt`: authenticated, the same validation and duplicate test, views earliest first, and `remaining`; verify `SegmentsTest` for a duplicate, no duplicate with an allowance of 8, a malformed line, an unknown sport and an unauthenticated call

## 5. Client data

- [x] 5.1 Add `SegmentRepository.check(sport, line)` and `create(name, sport, line, derivedFrom)` (in `SegmentPublisher`, so the read-only repository keeps no outbox), queueing a repo `feed.segment` (`source` athlete, `license` CC0-1.0) through the outbox; verify `SegmentRepositoryTest` with `MockEngine` and the queued outbox row
- [x] 5.2 Add a helper that lays an activity's privacy-cut pieces end to end by distance and cuts the selected range into a line, refusing a range across a gap; verify `core-data` `jvmTest` with a route through a zone and the hide-ends distance

## 6. Client UI

- [x] 6.1 Add `AppRangeSlider` to `ui/adaptive` and list it in `.agents/rules/adaptive-ui.md`; verify it compiles on every target (`./gradlew check`)
- [x] 6.2 Add an optional `highlight` polyline to `ActivityMap` (all actuals) and to the web `RoutePreview`; verify on the desktop app with Compose Hot Reload, screenshotting a highlighted stretch
- [x] 6.3 Scaffold `CreateSegmentScreen` with the `new-screen` skill: presenter state (selection, validity, lines, name, public warning, `derivedFrom` rule, duplicates, allowance) and `Publish` through `rememberSingleFlight`; verify `CreateSegmentPresenterTest` covers a too-short ride, a gap, duplicates found, the limit reached, a failed check, `derivedFrom` for public versus Followers activities, and a double tap queueing once
- [x] 6.4 Build `CreateSegmentUi` (map with highlight, range slider, distance and validity text, name field, warning, duplicates list opening `SegmentScreen`, Publish), with strings in `strings.xml`; verify on the desktop app with Hot Reload
- [x] 6.5 Add the `CreateSegment` event and menu item to the owner's activity detail, shown only with a track, a sport group and readable zones, and show the confirmation on return; verify `ActivityDetailPresenterTest` for own, others', indoor and unreadable-zones cases
- [x] 6.6 Show a non-active status beside efforts in `ActivitySegmentsUi`, and the status plus a link to the original in `SegmentUi` (`SegmentPresenter` opens `duplicateOf`); verify `ActivityDetailSegmentsTest` and `SegmentPresenterTest`

## 7. Docs

- [x] 7.1 Update AGENTS.md's segments wording and `.agents/rules/appview.md` (statuses are derived by the AppView, which never writes segments; promotion counts private efforts but serves only the status); verify `scripts/check-agent-sync.sh` passes
- [x] 7.2 Make `docker/scripts/seed-segment.sh` refuse a ride under 500 m, and update the local-stack skill to note statuses and `SEGMENT_ACTIVE_ATHLETES`; verify by seeding a 300 m ride (refused) and a 300 m run (indexed, `proposed`)

## 8. Integration

- [x] 8.1 Run `./gradlew check` and `./gradlew :server-appview:test` and verify both pass
- [x] 8.2 On the local stack (`scripts/dev.sh --seed alice`, plus `bob` and `carol`), create a segment from alice's activity in the app, see a duplicate refused, ride it with bob and carol (seeded activities), and verify it becomes `active` and appears in bob's search only after promotion
