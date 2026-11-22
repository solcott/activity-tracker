# Proposal

## Why

The Trail page shows where a trail goes and which segments lie on it, but not how it climbs. An athlete choosing a stretch can't tell where the climbing is, or which segment holds the steep part, without opening each segment page. The AppView already samples terrain tiles for each segment's profile (`segments/segment-elevation`), so it can do the same for a whole trail. This is the next piece of the Trail & Segment design, after rating colours and filters.

## What Changes

- **AppView:**
  - Samples each path's line pieces against the terrain tiles, as it does a segment's line: when the path is indexed or edited, and once for existing paths. It stores each piece's smoothed profile with its climb and descent.
  - `feed.getPath` returns the path's profile, if any:
    - each covered piece's evenly spaced heights, length and steepest grade
    - the whole path's climb and descent
    - for each returned segment that lies along a piece, where it starts and ends along that piece
- **Trail page (shared UI and native iOS):**
  - A grade-tinted elevation chart sits under the map, with the pieces in the path's order and a break between pieces.
  - The header's summary adds the trail's climb and descent.
  - A strip under the chart marks where each listed row's segment lies along the trail, in that row's rating colour. It follows the search and filters.
  - Selecting a row or its line shades that row's stretch on the chart. Scrubbing the chart marks the same spot on the map.
  - With no terrain profile, the page looks as it does today.
- **Shared chart (`LineChart`, both UIs):** gains marked stretches (spans), breaking its line between pieces as it already does at gaps, and reports the point being scrubbed to its page.

## Capabilities

### New Capabilities
- `segments/path-profile`: a trail or road's terrain profile: how the AppView derives and serves it with each segment's stretch along it, and how the Trail page draws it, marks segments on it, and ties it to the selection, filters and map.

### Modified Capabilities

None. The Trail page's existing requirements (`segments/path-page`, `segments/path-filters`) keep their behaviour. The profile only adds to them.

## Impact

- **Lexicons:** `feed.getPath` output gains an optional `profile` (`#pathProfile`, `#pieceProfile`, `#segmentSpan`). These are mirrored in `core/lexicon`. Older clients ignore it.
- **AppView:**
  - A Flyway migration adds a `path_terrain` table.
  - A `path-elevation` job joins the segment jobs queue, with a backfill at start-up like `backfillTerrain`.
  - Path indexing enqueues the job, and the getPath view serves the profile.
  - Tests run on H2.
- **Client:**
  - `feature/segment` api, presenter and ui: `PathProfile`, its chart and the stretches, plus the scrub point.
  - `app/common/api` `LineChart` gains spans; `app/common/ui` `LineChartUi`/`TimeSeriesChart` draw them and report scrubbing.
  - New samples in `:app-fixtures`, updated Android screenshot references and iOS snapshots.
- **iOS:** `PathPage.swift` draws the chart and the map marker. `LineChartView` draws spans and reports the scrubbed point.
- No new records, no private data, and nothing an athlete writes.
