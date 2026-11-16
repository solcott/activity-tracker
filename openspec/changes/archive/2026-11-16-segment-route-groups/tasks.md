# Tasks

## 1. Geometry (`core/location`, `server/segment-importer`)

- [x] 1.1 Move `heading`/`compass` from `CandidateCutter` into `SegmentGeometry` (or a sibling in `core/location`) and point the importer at it. Verify by passing `./gradlew :core-location:jvmTest` and `./gradlew :server-segment-importer:test` with no name changes in the importer's tests.
- [x] 1.2 Add `SegmentGeometry.sameRoute(a, b)`: `duplicates(a, b) || duplicates(a.reversed(), b)`. Add tests for a reversed line, a same-direction line, a longer stretch, and a line 50 m off. Verify by passing `:core-location:jvmTest`.

## 2. AppView: deriving routes (`server/appview`)

- [x] 2.1 Add the migration `V<next>__segment_routes.sql` (check `db/migration`, `db/vendor/postgresql` and `javaMigrations` for the next free version): nullable `segments.route_uri` and an index on it. Verify that `MigrationVersionsTest` passes.
- [x] 2.2 In `index/SegmentRecordIndex.kt`, add `routeOf(kind, line, before)`. It unions the end-cell lookups, keeps live segments of the kind (any sport), and returns the earliest root's route by `sameRoute`. Set `route_uri` inline when a new or re-lined segment is indexed, and for a duplicate that turns live in `recheckDuplicatesOf`.
- [x] 2.3 Recheck a route's members, earliest first, when its root is deleted, archived, re-lined, or marked duplicate. Do it in the same transaction as the change.
- [x] 2.4 Add the startup pass `assignMissingRoutes`, run independently of the signing key. It assigns routes in batches, earliest first indexed, to segments with a null `route_uri`, and can resume.
- [x] 2.5 Add `SegmentRoutesTest` (H2, the `SegmentTestBase` style) covering:
  - three sport groups in one route
  - reversed lines in one route
  - a longer stretch apart
  - a lift and a run apart
  - deleting the root keeps the rest together under the next earliest member
  - a re-lined member moving to another route
  - a run over a ride line still not being a duplicate
  - the backfill assigning routes to pre-existing rows

  Verify by passing `./gradlew :server-appview:test --tests "*.SegmentRoutesTest"`.

## 3. Lexicons and views

- [x] 3.1 Use the `lexicon-change` skill to make these optional changes, and mirror them in `core/lexicon`:
  - `feed.defs#segmentView` gains `route`.
  - `feed.getSegment` output gains `routeMembers` (a `#routeMember` with uri, sport, kind, name and line).
  - `feed.searchSegmentNames` documents that its limit and cursor count routes.
- [x] 3.2 Serve `route` from `SegmentViews.views` as `COALESCE(route_uri, uri)`. Serve `routeMembers` from `getSegment`: the members `segmentServed`, `unhidden` and `listedTo` allow, excluding itself, and none for a lift. Add tests to `SegmentsTest` (route on area search views, members listed, a proposed member hidden from a stranger). Verify by passing `:server-appview:test --tests "*.SegmentsTest"`.
- [x] 3.3 Run the `lexicon-auditor` agent on the changed NSIDs and fix every mismatch it reports.

## 4. AppView: name search by route

- [x] 4.1 Rewrite `SegmentViews.searchNames`:
  - A grouped paging query by `COALESCE(route_uri, uri)` with `MIN(rank)` and `MIN(distance)` (or the name with no centre), offset-paged by route.
  - A members query for the page's routes under the same filters except the name, keeping the `crosses` box check.
  - Members in route order, then by first indexed.
- [x] 4.2 Extend `SegmentNameSearchTest`. Verify by passing `:server-appview:test --tests "*.SegmentNameSearchTest"`. Cases:
  - one route of six returned together on one page, and not again
  - the limit counting routes
  - a renamed member ranking the whole route
  - the sport filter narrowing members
  - the box excluding a member whose line misses it
  - the existing ranking cases still passing

## 5. Browse model and texts (`feature/segment/api`, `:ui-resources`)

- [x] 5.1 Add `SegmentRoute(key, members, shown)` to `SegmentBrowseScreen.kt`. Switch `AreaRuns.runs`, the name `results`, `selected` and hover to route keys. `SegmentsMap.of` in `app/common/api` draws one line per route. Update `SegmentsMapTest`. Verify by passing `./gradlew :app-common-api:jvmTest`.
- [x] 5.2 Add `SegmentBrowseTexts.routeName(route)` (strip a direction suffix; make the single "→" a "↔" when both directions are listed) and the route details ("Ride, Run, Hike · Both ways"). Put the new strings in `:ui-resources`, run `./gradlew generateIosResources`, and add the names to the text-names test. Cover these in unit tests:
  - eastbound
  - clockwise
  - a stretch both ways
  - a one-way stretch
  - a renamed name

  Verify by passing `:feature-segment-api:jvmTest`.
- [x] 5.3 Add browse sample states with a three-sport, two-way route in `:app-fixtures`. Verify by passing `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"`.

## 6. Browse presenter and UI

- [x] 6.1 In `SegmentBrowsePresenter`:
  - Group area runs after the distance sort; a route takes its nearest member's distance.
  - Group name pages by `route`, treating a missing route as the segment's own URI.
  - Open a route's `shown` member.
  - Select by route.

  Add cases to `SegmentBrowsePresenterTest` (grouping, the sport filter, select and open by route, a missing `route`). Verify by passing `./gradlew :feature-segment-presenter:jvmTest`.
- [x] 6.2 Update `SegmentBrowseUi` rows to the route name and details, and route selection and hover. Extend `SegmentBrowseUiTest`, then run `SegmentBrowseThroughHostTest` and `SearchTabSegmentsTest`. Verify by passing `:feature-segment-ui:jvmTest` and `./gradlew :composeApp:jvmTest --tests "*SegmentBrowseThroughHostTest" --tests "*SearchTabSegmentsTest"`.
- [x] 6.3 Rerun the screenshot suite. Look at the changed browse references, then update them with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`.

## 7. Segment page switcher

- [x] 7.1 In the segment screen's `-api` state, add `RouteSwitcher` (sports, directions labelled from `heading`, the current choice) and a switch event, with direction labels as `AppText` strings in `:ui-resources` (rerun `generateIosResources`). Add a segment page sample with the switcher in `:app-fixtures`.
- [x] 7.2 In `SegmentPresenter`, build the switcher from `routeMembers` plus the segment itself. On a switch, keep the direction when it can, and show the chosen segment in place (no navigation; back leaves the page). Keep the direction choices in a fixed order. Add `SegmentPresenter` tests (no switcher when alone, a switched sport keeping its direction, falling back to the first direction, no navigation on a switch, directions keeping their order). Verify by passing `:feature-segment-presenter:jvmTest`.
- [x] 7.3 Draw the switcher on the shared segment page with `AppFilterChip` rows. Add a UI test that choosing Run sends the switch. Verify by passing `:feature-segment-ui:jvmTest`, then rerun and update the screenshots.

## 8. iOS

- [x] 8.1 In `SegmentBrowseView.swift`, use route rows (name, details) and route selection. In `SegmentPage.swift`, draw the switcher: a sport menu chip and a direction segmented picker from `iosApp/iosApp/Design`, in the shared page's words. Verify that the `xcodebuild … build` command from CLAUDE.md succeeds and that the stub check reports no new stubs.
- [x] 8.2 Run the Swift tests on an iOS 26.0 simulator and rerecord the browse and segment page snapshots after looking at them. In the simulator, check that searching "ann's" shows one row per trail and that switching sport and direction works.

## 9. Integration

- [x] 9.1 Run `./gradlew check` through the `gradle-runner` agent and fix what it reports.
- [x] 9.2 End to end:
  1. Run `scripts/dev.sh`.
  2. Import a small region with a two-way multi-use trail through `scripts/segment-import.sh`.
  3. In the desktop app, search the trail's name: one row reads "Ride, Run, Hike · Both ways", the area list shows one row per trail, and the switcher moves between sports and directions with back returning to browse.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed.
