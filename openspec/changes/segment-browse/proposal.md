# Proposal

## Why

Athletes can reach a segment today only from an activity's efforts, their starred list, or a link someone shares. There is no way to see what segments exist around them, or to find one by name, so the segment importer's trails and the community's segments stay hidden until someone happens to ride them. The AppView already serves segments by area (`feed.searchSegments`) and derives each segment's current name, so a browse and search screen needs one new query and a client screen.

## What Changes

- The Search tab gets an **Athletes / Segments** switch. Segments mode is a new screen that shows segments on a **map** and in a **list**, and searches them **by name**.
- **Map browse** (Android, iOS, desktop): the map shows the segments whose lines cross the visible area. It opens at the athlete's location on phones (asking for location permission), and otherwise at their latest activity's area. Zoomed out wider than 1 degree, it asks the athlete to zoom in. The list shows the same runs, nearest the map's centre first. Tapping a line or a row selects it, and the row opens the segment page.
- **Lifts** draw on the map in a muted style for orientation, and never appear in the list or in name results.
- **Name search**: typing a name lists matching segments, ranked by how well the name matches, then by distance from the map's centre. A scope switch chooses **This area** (only segments crossing the visible area) or **Everywhere**. On web, which has no interactive map yet, Segments mode is name search and a list only, always Everywhere.
- **Sport filter**: the athlete can limit the map, list and name results to one sport group.
- New AppView query **`feed.searchSegmentNames`** (`q`, optional area, optional centre, optional sport group, cursor). It serves the same segments `feed.searchSegments` would (active to everyone, proposed only to its creator and riders, never duplicate, archived, hidden or taken down), never lifts, matched on the current name.
- Additive lexicon change only; nothing existing changes shape.

Name scope, confirmed with the user: one query with an optional area and a This area / Everywhere switch, defaulting to This area where a map is shown.

## Capabilities

### New Capabilities
- `segments/segment-browse`: the segment browse and search screen. Its place in the Search tab, the map and list and how they stay in step, where it opens, the zoom limit, name search and its scope, the sport filter, how lifts show, and what web does without a map.

### Modified Capabilities
- `segments/segment-records`: segments can be found by name with `feed.searchSegmentNames`, with the same visibility rules as the area search, bounded work, and lifts left out.
- `ui/content-loading`: segment name results join the paged lists, and a new name query keeps the last results on screen until it answers, as athlete search does.

## Impact

- **Lexicons**: new `lexicons/app/activitytracker/feed/searchSegmentNames.json`; mirrored in `core/lexicon` (`Queries.kt`, `Nsid`).
- **AppView**: a name search in `api/SegmentViews.kt` modelled on `ActorSearch`, its route in `Routes.kt`, and tests in `SegmentsTest`. It needs no migration: it reads `segments.current_name`, the stored box and kind.
- **Clients**: `SegmentRepository` gains the name query; a new `SegmentBrowseScreen` in `feature/segment/{api,presenter,ui}`; a new multi-line map composable in `:app-common-ui` (MapLibre, phones and desktop only); the Search tab's switch (`feature/search` opens segments only through `:feature-segment-api`); a first location fix from `LocationTracker`. New strings go in `:ui-resources`, samples in `:app-fixtures`, screenshot references for the new screen.
- **Out of scope**: place or address search (a later change, with a geocoder), a real map on web, server-side distance ordering of area browse, and clustering lines at low zoom.
