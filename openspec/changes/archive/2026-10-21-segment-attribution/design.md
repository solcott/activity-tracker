# Design

## Context

`SegmentPresenter` loads a `SegmentView` whose `record` holds `source` (open enum `athlete`/`osm`/`agency`, `SegmentSource`) and `license` (an SPDX id). `SegmentUi` shows length, sport group, "By @handle" and the status, under an `ActivityMap` of the line. The segment screen is the only place the app draws a segment's line: the activity page and the starred list show segment names only, and `CreateSegmentUi` draws the athlete's own route. Map tiles already carry the tile provider's OSM attribution through `RouteMapOverlay`; that credits the basemap, not the segment's data.

## Goals / Non-Goals

**Goals:**
- Meet the ODbL's attribution requirement for any segment whose line comes from OSM, wherever its line is drawn.
- Keep the credit logic testable in the presenter.

**Non-Goals:**
- Naming the specific agency: the record has no field for it. `segment-agency-import` decides whether to add one; until then the agency credit is generic.
- Checking that a record's `source` and `license` agree, or that the creator is the importer. A false `osm` claim only adds a credit.
- Credits on screens that show a segment's name without its line.

## Decisions

**The presenter derives a `SegmentCredit`, the UI renders it.** `SegmentState` gains `credit: SegmentCredit?`, a sealed type in `:app-screens`: `OpenStreetMap(license)`, `Agency(license)`, `License(id)`, or null for `athlete`. `SegmentPresenter` maps the loaded record with a small pure function, so `SegmentPresenterTest` covers each source without UI. *Alternative:* map in `SegmentUi` straight from `segment.record.source`. Rejected: the mapping is logic (unknown sources, blank licences), which this repo keeps in presenters.

**Unknown sources show only the licence.** The source is an open string written by any client, so showing it verbatim would let a record put arbitrary text under the creator. The licence is bounded (≤ 64 chars) and meant to be an SPDX id; a blank one shows nothing.

**The OSM credit links to `https://www.openstreetmap.org/copyright`.** That is what OSM's attribution guideline asks for. The text is a clickable `AppTextButton`-styled line opened through Compose's `LocalUriHandler`, which works on every target, so no new `expect`/`actual`. The credit is plain text under the map, not inside the map overlay: the overlay's attribution collapses after five seconds, and the segment credit should stay.

**The seed script takes a source.** `seed-segment.sh` gains `--source osm|agency` (default `athlete`), setting `ODbL-1.0` or `CC0-1.0`. That is enough to see each credit on the desktop app before the importer exists.

## Risks / Trade-offs

- [The generic agency credit is vague] → Acceptable while no agency segments exist; `segment-agency-import` revisits it.
- [A future screen draws a segment line and forgets the credit] → The requirement says "wherever the app draws a segment's line"; the KDoc on `SegmentCredit` says so too, so a reviewer of a new map screen sees it.
