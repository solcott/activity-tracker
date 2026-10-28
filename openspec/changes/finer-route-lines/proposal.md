# Proposal

## Why

Routes and segments on the map cut across switchbacks and turn rounded hairpins into sharp V's, while the basemap's trail beside them curves. Every line is Ramer–Douglas–Peucker-simplified at 5 m, which at trail zoom is 15–30 px: the OSM way for Rush Trail – Tom Sawyer Section has 229 nodes 2.3 m apart, and the segment published from it keeps 24 of them, exactly what RDP at 5 m leaves. The record limits have plenty of room for more detail: activity polylines allow 50,000 characters and average about 1,500 today, and segment lines allow 100,000.

## What Changes

- An activity's polyline follows its track within 2 m. It gets coarser only when it wouldn't otherwise fit `feed.activity#polyline`'s 50,000 characters: the tolerance doubles until it fits, as the importer already does for segment lines.
- The owner's detail map, which is meant to draw their full track, draws it within 1 m instead of the 5 m polyline. It has no record limit.
- Segment candidates follow their trail within 1 m instead of 5 m, doubling only to fit a `feed.segment` record.
- Existing activity polylines are rebuilt once from their tracks, at every place, by the route sweep that re-trims them when zones change. `actor.routeTrim` gains an optional `lineVersion` that marks which polyline detail the sweep last wrote, so the rebuild runs once per athlete, survives interruption and finishes on any device.
- A new importer command, `refine`, rewrites the line of each segment the importer already published when a fresh extract gives the same candidate a finer line along the same path. It leaves any segment alone whose trail actually moved. **BREAKING** for the importer's rule that it never edits its segments: this is the one edit allowed, and the AppView recomputes efforts on the finer line as it does for any line change.
- Maps and route previews draw a smooth curve through the line's points instead of straight segments between them, so bends read as bends at trail zoom. The curve passes through every stored point and never strays more than 2 m from the straight line between two of them: as far as the simplification let the track stray. Records are unchanged; this is drawing only. Smoothing alone couldn't have fixed the 5 m lines (it can't bring back switchbacks the simplification dropped), and corner-cutting such as Chaikin's would pull hairpins inward, so it comes after the finer lines and goes through the points.

## Capabilities

### New Capabilities
- `feed/route-line-detail`: how closely activity polylines and the owner's map follow the track, the size budget, the one-time rebuild of existing polylines, and how maps draw a line between its points.

### Modified Capabilities
- `profile/privacy-zones`: once a re-trim is done, later sessions still rebuild polylines when the polyline detail changes, not only when the zones do.
- `segments/trail-candidates`: a candidate's line stays within 1 m of its trail (was 5 m), coarser only to fit a record.
- `segments/segment-import`: the importer may rewrite a published segment's line with a finer one along the same path (`refine`), and never otherwise.

## Impact

- `core/location`: `TrackMath.simplify` callers, and a shared simplify-to-fit-a-budget helper (moved from `CandidateCutter.simplified`).
- `core/data`: `SegmentedPolyline.of`, `ActivityDraft.polylineOf`, `RouteTrimmer` (sweep trigger and `actor.routeTrim` write), `PrivacyZones`/`RouteTrim`.
- `app/presenters`: `OwnRoute` (the owner's map line).
- `composeApp`: `ActivityMap` (MapLibre route, highlight and segment lines) and `RoutePreview` (feed cards, previews, web) draw the smoothed curve; markers stay on the stored points.
- Lexicon: `app.activitytracker.actor.routeTrim` gets an optional `lineVersion` integer (JSON and Kotlin mirror only; the record lives in the `self` space, and the AppView ignores it).
- `server/segment-importer`: tolerance, the extract format version (candidate lines change), the new `refine` command and `scripts/segment-import.sh`.
- Record sizes: activity polylines grow, typically several times over, up to the existing 50,000-character limit. Feed cards and maps draw more vertices.
- Clients without this change keep writing 5 m polylines and drop `lineVersion` when they write `actor.routeTrim`. The next updated client then sweeps again, which only rewrites polylines that differ.
