# Proposal

## Why

Segment browse and name search show one row for every `feed.segment`, and the segment importer publishes one segment per sport group and per direction. A two-way multi-use trail therefore fills the list with up to six rows that read almost the same: "Ann's (eastbound)" for Ride, Run and Hike, then "Ann's (westbound)" three times, and every "A → B" stretch again with its reverse. The trail an athlete is looking for gets lost among copies of itself.

## What Changes

- **Routes.** Live segments of one kind whose lines follow the same path form a route, whatever their sport group and whichever way they run. The test is the existing duplicate test, applied forwards or reversed. The AppView derives each segment's route when it indexes the segment, keeps it current as segments are deleted, archived or re-lined, and serves it in every segment view.
  - Each segment stays its own record, with its own efforts, leaderboards, Mayors, names and status.
  - Nothing about duplicates changes: a run segment over a ride line is still not a duplicate.
- **Name search lists routes.** `feed.searchSegmentNames` ranks and pages routes, with a route ranked by its best-matching member. Each page returns every listed member of the routes on it.
  - Older clients still get plain segment views, now with a route's members next to each other.
  - **BREAKING** (paging): a page's `limit` and cursor count routes, not segments, so a page can hold more segment views than its limit.
- **Browse shows one row and one line per route.** This applies to both the area list and name results, on Android, desktop, web and iOS.
  - A row drops the direction from importer names: "Ann's (eastbound)" reads "Ann's", and "Lupine Hiking → Potato Hill" reads "Lupine Hiking ↔ Potato Hill" when both directions are listed.
  - The row's details list the route's sport groups and say "Both ways" when both directions are listed. A name that doesn't fit the importer's pattern shows unchanged.
  - The sport filter keeps only that group's members, so a route can still list both directions.
  - Selection, hover and the map's highlight work on routes.
- **The segment page switches within its route.** `feed.getSegment` lists the other members of the segment's route that the viewer may see. The segment page, shared and native on iOS, shows a sport and direction switcher when there is more than one member, and switching replaces the page with that member's.
  - Opening a route from browse opens the member of the chosen sport group (the first when All is chosen), in the direction listed first.

## Capabilities

### New Capabilities

- `segments/segment-routes`: what a route is, how the AppView derives and keeps it, how it's served (`segmentView.route`, `getSegment`'s route members), how a route's row is named, and the segment page's sport and direction switcher.

### Modified Capabilities

- `segments/segment-records`: name results are ranked and paged by route, and following the cursors returns every matching route exactly once.
- `segments/segment-browse`:
  - The area list, name results and the map show one row and one line per route.
  - Selection and hover work on routes.
  - The sport filter narrows a route's members.
  - The native iOS list shows route rows.

## Impact

- **Lexicons:** `feed.defs#segmentView` gains an optional `route`. `feed.getSegment`'s output gains `routeMembers`. `feed.searchSegmentNames` documents paging by route. The `core/lexicon` mirror changes to match.
- **AppView:**
  - A migration adding `segments.route_uri` and backfilling it for existing segments.
  - Route derivation and recheck in `index/SegmentRecordIndex.kt`.
  - `SegmentViews.searchNames` grouped by route, and `getSegment` route members.
  - A reversed-line match in `SegmentGeometry` (`core/location`).
  - Tests.
- **Client:**
  - `feature/segment/api`: a route row model, the route name rule and texts, and the segment page's switcher state.
  - `feature/segment/presenter`: grouping, selection by route, and switching members.
  - `feature/segment/ui`: rows and the switcher.
  - `app/common/api`: `SegmentsMap` draws one line per route.
  - `:ui-resources` strings and generated iOS resources.
  - `:app-fixtures` samples, plus screenshots.
- **iOS:** `SegmentBrowseView` rows and selection, the native segment page's switcher, and snapshots.
- **Shared:** compass heading moves from the segment importer into `core/location` so the client can label directions.
- **Unchanged:** the segment importer's output, efforts, leaderboards, statuses and the duplicate rule.
