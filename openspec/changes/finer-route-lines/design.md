# Design

## Context

See proposal.md for why. Every line a map draws today goes through `TrackMath.simplify` (Ramer–Douglas–Peucker, default 5 m):

- `SegmentedPolyline.of` → `ActivityDraft.polylineOf` / `RouteTrim.polylineOf` / `segmentedPolylineOf`. This builds the published polyline (record or details) for recorded, imported and combined activities, for `RouteTrimmer` re-trims, and for `ActivityPage` previews.
- `OwnRoute` builds the owner's "full track" map with the same `ActivityDraft.polylineOf`, so it's 5 m too.
- `CandidateCutter.simplified` in the importer starts at `TOLERANCE_M = 5.0` and doubles until the precision-6 encoding fits 100,000 characters.

Maps draw the decoded vertices with round caps and joins (`ActivityMap.nonWeb.kt`, `RoutePreview`). Nothing on the client caps a polyline's length, but the AppView drops one longer than 50,000 characters (`DetailsIndex`, `MAX_POLYLINE_LENGTH`), so a finer line needs a budget. Athlete-created segments are cut from the raw track pieces (`SegmentRoute`) and are never simplified, so they aren't part of this change.

`RouteTrimmer.sweep` already rebuilds every polyline from its `self` track, at every place, and writes `actor.routeTrim { revision }` only once every rewrite has been sent. The sweep returns early when the zones have never been saved (`revision == null`) or the marker matches.

## Goals / Non-Goals

**Goals:**
- One shared "simplify within a tolerance, coarsen only to fit a character budget" routine, used for activity polylines and segment candidates.
- Reuse the zone re-trim sweep for the one-time rebuild, so it finishes after interruption and works across devices without a new mechanism.

**Non-Goals:**
- Smoothing or filtering GPS noise before simplification. 2 m keeps most 1 Hz jitter out of published lines. The owner's 1 m map may show jitter where the athlete stood still, which is their real track.
- Zoom-dependent detail.
- Changing segment tracks, matching, duplicate tests or athlete-created segments.
- Refining agency segments any differently from OSM ones (they go through the same cutter).

## Decisions

**1. Tolerances: 2 m for published activity polylines, 1 m for the owner's map and segment candidates.**
Measured on Rush Trail – Tom Sawyer (229 OSM nodes): 5 m keeps 24 points, 2 m keeps 46, 1 m keeps 69 and 0.5 m keeps 101. At 2 m the switchbacks keep their turns. Recorded tracks carry 2–5 m of 1 Hz jitter, so 1 m would publish fuzz along every straight. Survey lines (OSM, agency) are clean, so 1 m is safe and gives the best shape on segment pages, which are zoomed in on one trail. The owner's map has no record behind it, so it uses the finest tolerance that still drops collinear points, rather than no simplification at all. Alternatives: 0.5 m everywhere (doubles the size for no visible gain at precision-5's ~1.1 m quantization); leaving the owner's map unsimplified (raw 1 Hz tracks of several hours are tens of thousands of points for no visible gain).

**2. A shared `simplifyToFit` in `core/location`, doubling the tolerance from its start until the encoding fits.**
Move the loop out of `CandidateCutter.simplified` into `TrackMath` as something like `simplifyToFit(points, startTolerance, maxChars, encode)`. Activity polylines call it with 2 m, 50,000 characters and precision 5. The importer calls it with 1 m, 100,000 characters and precision 6. For a polyline in several pieces (breaks, combined members), one tolerance applies to every piece and the budget to their concatenated encoding, which is how `SegmentedPolyline` stores them. Doubling at most a few times keeps the cost bounded: each try is O(n log n) RDP over the full track. Alternative: binary-searching the tolerance gives a tighter fit but more passes, and doubling already reaches "no coarser than needed" within a factor of 2, which the spec allows.

**3. `actor.routeTrim` gains an optional `lineVersion` integer, and `revision` becomes optional.**
A client constant `LINE_VERSION = 1` names the current detail. An absent `lineVersion` reads as 0, the 5 m era. The sweep now runs when the zones revision differs from the marker's **or** the marker's `lineVersion` is below the constant, and it writes both when it finishes. Today an athlete who never saved zones has `revision == null` and never sweeps, yet their polylines still need rebuilding, so `revision` must be optional ("absent when the athlete has never saved zones"). That's safe for older clients: they read a marker without `revision` as a decode failure, so `marker()` gives null, and they only compare it when their own zones revision is non-null. Then they sweep and write a marker with a revision, which just makes the next updated client sweep once more. Alternatives: a separate `actor.routeDetail` record (a second marker to keep in step for the same sweep); a local-only flag (wouldn't carry across devices, and every device would rebuild).

**4. A detail-only sweep doesn't touch segment tracks.**
`SegmentTrackSweeper` keys on `zonesRevision` and is unaffected. Segment tracks are already full-resolution E7 positions.

**5. `refine` reads the candidates file, lists the importer's own `feed.segment` records, and puts back only the line.**
It pages `com.atproto.repo.listRecords` for `feed.segment` in the service account's repo and joins records to the file's candidates by rkey (= candidate id). It rewrites with `putRecord` when the lines differ and their discrete Hausdorff distance, over both lines resampled every 2 m, is ≤ 6 m. Every other field is copied from the existing record. This needs no new AppView endpoint and works for every region in one pass. 6 m is the old 5 m tolerance plus the new 1 m: an unchanged trail can't differ by more, and a reroute almost always does. It reports refined, unchanged, moved and missing counts, like `publish`. `scripts/segment-import.sh` runs `refine` after `publish`. Alternatives: extending `publish` to rewrite (that would break its "never rewrites" contract for every run); an AppView query listing published candidates (new endpoint for an operator-only path).

**6. Bump `IMPORTER_VERSION` to 3.**
Candidate lines change, and staging is keyed by id with a revision per run, so an old-format file can't be staged with new lines by mistake. Staged candidates' lines get replaced on the next `stage` like any run's.

**7. Maps draw a capped centripetal Catmull-Rom curve through the stored points (`RouteCurve` in `core/location`).**
Each piece of a line is projected to local meters, and each stretch between two points that bends (its turn at either end over a few degrees) gets extra points about every 2 m, at most a fixed number per stretch, along a centripetal Catmull-Rom spline, the ends extended by reflection. Centripetal parameterization never forms cusps or loops on unevenly spaced points, which simplified lines always are. The spline can still bulge on a long stretch beside a hairpin, so each stretch's offsets from its chord are scaled down until none exceeds 2 m: the simplification kept the true track within its tolerance of that chord, so a curve further out would be drawn where nobody rode. `ActivityMap` (MapLibre) and `RoutePreview` (Canvas, the feed card and the web) draw the curve for the route and the highlighted stretch; breaks, markers and framing use the stored points, so nothing else moves. Alternatives: MapLibre has no curved-line layer; Chaikin corner-cutting doesn't pass through the points and cuts a hairpin a quarter of each neighbouring stretch inward; smoothing before simplifying would change records and hide nothing more.

## Risks / Trade-offs

- [Activity records grow several times over, and feed lists download more] → Bounded by the 50,000-character cap. Typical runs and rides land well under it. Feed cards draw the same polyline at thumbnail size, which costs more vertices but no extra requests.
- [The rebuild rewrites every activity once, through the outbox, on first launch] → It runs in the background with the existing sweep's concurrency (`CONCURRENT_TRACKS`), and only records whose polyline changes are put. Jetstream and the AppView re-index each once.
- [Old clients on another device keep writing 5 m polylines for new activities after the marker says `lineVersion = 1`] → Accepted: those activities stay coarse until the next zones change or detail bump. Old clients drop `lineVersion` when they finish a zone re-trim, which makes updated clients rebuild again (spec scenario "Older app writes meanwhile").
- [`refine` recomputes efforts on every refined segment at once] → The AppView already handles line changes this way (segment-records). Run it per region, the same scale as `publish`.
- [A real reroute under 6 m slips through `refine`] → Its line then follows the trail as mapped now, within the matcher's 30 m corridor. That's harmless, and arguably a fix.
- [Drawn lines have more vertices] → Only bending stretches are subdivided, and each at most a fixed number of times: a ride of 1,000 points draws a few thousand, which MapLibre and a Canvas path handle easily. Computed once per line (`remember`).
- [Capping the bulge leaves a slight change of direction at a stored point where it applies] → It only applies where the curve would leave the 2 m band, and is far smaller than today's corners.
- [Precision-5 encoding quantizes to ~1.1 m, near the owner-map tolerance] → Visually irrelevant at map zooms. The 1 m guarantee is measured before encoding, which is the same convention as today's 5 m.

## Migration Plan

1. Ship the lexicon change (`routeTrim.lineVersion`, optional `revision`) with the client. No AppView migration: it ignores `actor.routeTrim`.
2. Ship the client. Each athlete's first session sweeps once.
3. Importer: re-run `extract` (version 3), `stage` and `publish` per region, then `refine` with the same file.
4. Rollback: an older client simply stops rebuilding. Polylines already written stay finer and valid (same lexicon). Refined segments keep their finer lines.

## Open Questions

- Whether 2 m is still enough for long road rides that hit the budget. We can tune `LINE_VERSION`'s tolerance later without changing the approach, and bumping the version reruns the rebuild.
