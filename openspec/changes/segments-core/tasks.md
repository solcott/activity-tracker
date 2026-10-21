# Tasks

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/feed/segment.json`:
  - `name`, `sport` (open enum `ride`/`run`/`hike`/`ski`/`paddle`), `line` (precision-6 encoded polyline, max 100 kB), `source` (open enum `athlete`/`osm`/`agency`), `license`, an optional `derivedFrom` strong ref, `createdAt`; key `tid`;
  - a description saying it's public, in the repo only, and that the AppView computes its length.

  Verify `./gradlew :core-lexicon:jvmTest` still passes.
- [x] 1.2 Add `segmentView`, `segmentViewBasic` and `effortView` to `feed/defs.json`, and the queries `feed/getSegment.json`, `feed/searchSegments.json` (bbox params as E7 integers, `sport`, `limit`, `cursor`), `feed/getActivitySegments.json` and `feed/getSegmentEfforts.json` (`segment`, `actor`, `limit`, `cursor`). Verify the JSON parses with the other lexicons.
- [x] 1.3 Mirror them in `core/lexicon`:
  - `Nsid.FEED_SEGMENT` (added to `indexedCollections`) and the four query NSIDs;
  - `SegmentRecord`, `SegmentView`, `SegmentViewBasic`, `EffortView` in `Records.kt`, and the query objects in `Queries.kt`.

  Verify round-trip tests in a new `SegmentRecordsTest` case: `$type` is written, unknown keys are ignored, optional fields are dropped.
- [x] 1.4 Add `SegmentSport.of(sport, subSport)` in `core/lexicon` per design decision 2. Verify a unit test that pins every known `Sport` and the listed sub-sports to their group, null or e-bike flag.
- [x] 1.5 Run the `lexicon-auditor` agent on `feed.segment` and the four queries, and fix every mismatch it reports.

## 2. Segment geometry

- [x] 2.1 Add `SegmentGeometry` to `core/location`:
  - polyline precision-6 encode/decode;
  - `lengthMeters`, and `resample(line, every)`;
  - `passes(piece, ref, startRadius, endRadius, corridor)` with interpolated start and end times, per design decision 3.

  Verify with `SegmentGeometryTest` (`./gradlew :core-location:jvmTest`):
  - one full pass, two laps, a reversed pass (none), a 40 m shortcut (none);
  - jitter of ±10 m (a match) and ±35 m mid-segment (none);
  - a track starting mid-segment (none);
  - interpolated times, and that an E7 round trip leaves results unchanged.
- [x] 2.2 Add `SegmentCells` (cell of a point, and the cells of a line with a 30 m neighbour margin) in `core/location`. Verify unit tests: a point near a cell edge includes the neighbour, and a line crossing a cell boundary covers both cells.

## 3. AppView: segments

- [x] 3.1 Add `implementation(projects.coreLocation)` to `server/appview/build.gradle.kts`, and migration `V29__segments.sql`:
  - `segments`: `uri` PK, `cid`, `author_did`, `name`, `sport`, `line`, `distance_m`, `min_lat_e7`, `max_lat_e7`, `min_lng_e7`, `max_lng_e7`, `source`, `license`, `indexed_at`;
  - `segment_cells` and `segment_track_cells`, each with a cell index;
  - `segment_efforts` per design decision 6, with indexes on `(segment_uri, author_did, elapsed_s)` and on `activity_uri`;
  - `segment_match_jobs` with primary key `(kind, subject_uri)`.

  Use H2-portable SQL, plus matching Exposed tables. Verify `MigrationVersionsTest` and `./gradlew :server-appview:test`.
- [x] 3.2 Index `feed.segment` from the repo only:
  - validate per `segments/segment-records` (length 200 m–50 km computed, name, known sport group, at least 2 points);
  - store its cells;
  - on a changed line or sport, delete the old efforts and enqueue a `segment` job;
  - on delete, remove the segment, its cells and its efforts;
  - add a `deleteActor` line.

  Verify `SegmentsTest` (AppView, H2): indexed from the repo; ignored from spaces; too short, too long and unknown sport dropped; replay is idempotent; delete removes efforts.
- [x] 3.3 Add `getSegment` and `searchSegments` routes and views:
  - the viewer's best effort, through `visibleTo`;
  - search by cells and bbox, with a 1° cap (`InvalidRequest`) and a cursor;
  - moderation via `served`.

  Verify `AppViewTest`-style cases for both, including `NotFound` and the box cap.

## 4. AppView: matching and efforts

- [x] 4.1 Add the job enqueueing to the indexer per design decision 5:
  - segment-track upsert → `track`;
  - segment upsert → `segment`;
  - activity, details or streams change for an activity with efforts or a usable track → `tiers`, or `track` on a sport, sub-sport or member change;
  - deletes, setting off, a dropped `self` space and account deletion → efforts deleted directly.

  Verify indexer tests asserting the queued jobs and deleted efforts for each trigger.
- [x] 4.2 Add `SegmentMatcher` (`appview/segments/`). It runs the `track`, `segment` and `tiers` jobs:
  - blob reads through `SpaceBlobs` for usable tracks only;
  - members of a combined activity skipped (`validClaim`);
  - efforts replaced per activity in one transaction;
  - tiers from `DetailCategory.currentTiers`;
  - failed fetches backed off;
  - the one-off backfill of usable tracks that have no cells.

  Start it from `Application.kt` when a signing key is set. Verify `SegmentMatcherTest` on H2 with a fake `SpaceBlobs`:
  - a pass creates an effort with its interpolated offset and elapsed time;
  - matching off creates nothing and drops existing efforts;
  - a member is skipped;
  - a new segment backfills past tracks;
  - a narrowed Map category re-tiers efforts;
  - a re-trimmed track removes the effort;
  - jobs are idempotent.
- [x] 4.3 Add `getActivitySegments` and `getSegmentEfforts` routes and views:
  - `visibleTo` on the effort's tier and on the activity row;
  - PR among the efforts the viewer may see;
  - a keyset cursor on `(elapsed_s, id)`;
  - `NotFound` for a hidden activity.

  Verify tests:
  - follower vs stranger vs athlete for Public/Followers/Only me activities and for a Map category narrower than the activity;
  - a blocked viewer sees nothing;
  - fastest-first order and paging.

## 5. Client

- [x] 5.1 Add `SegmentRepository` (`core/data`, SessionScope) wrapping the four queries. Verify jvmTests with a `MockEngine` AppView.
- [x] 5.2 Add the segments section on the activity detail screen:
  - `ActivityDetailState.segments`, and a `SegmentOpened` event navigating to `SegmentScreen`;
  - a separate `LaunchedEffect` load that's hidden on error or when empty;
  - the UI list with name, time and "PR", built from `ui/adaptive` components, with strings in `strings.xml`.

  Verify `ActivityDetailPresenterTest` cases (listed, empty hides, error hides, tap navigates) and `composeStabilityCheck`.
- [x] 5.3 Add `SegmentScreen` with the `new-screen` skill: the presenter loads `getSegment` and the viewer's `getSegmentEfforts`; the UI shows the map/preview of the line, name, distance in the athlete's units, sport and efforts (fastest first, with dates). Verify `SegmentPresenterTest` (loaded, error, efforts order), check it on desktop via Compose Hot Reload, and run `composeStabilityCheck`.

## 6. Dev tooling and docs

- [x] 6.1 Add `docker/scripts/seed-segment.sh` per design decision 9, and document it in the local-stack skill. Verify on the local stack that seeding a segment along a sample GPX makes `getSegment` return it.
- [x] 6.2 Update `.agents/rules/appview.md` (segments, efforts as derived private data served by tier, the matcher loop and its job table) and `AGENTS.md`'s data-flow paragraph. Verify `scripts/check-agent-sync.sh` passes.

## 7. Integration

- [x] 7.1 Run `./gradlew check` and verify it passes on all targets, with lint, ktfmt, detekt and the stability check.
- [x] 7.2 On the local stack, with an account that has matching on and a GPS activity:
  - seed a segment along that activity's route;
  - verify the activity page lists an effort with a sensible time, and the segment screen shows it;
  - verify a stranger doesn't see it when the activity is Followers;
  - turn matching off and verify the effort disappears.

## Workflow follow-up

- Archive with `/opsx:archive` once implemented and reviewed, which creates `segments/segment-records` and `segments/segment-efforts` and updates `segments/segment-track`.
- Next on the roadmap: `segment-creation`.
