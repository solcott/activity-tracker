# Design

## Context

Segments already run end to end: consent, segment tracks, `SegmentMatcher`, statuses, candidates staged from OSM and agency data, and publishing by the importer account. See proposal.md for why. The pieces this change bends:

- **Sport groups.** These are constants in `SegmentSportGroup` (`core/lexicon/.../app/Records.kt`), which holds `all`, `minMeters` and `maxSpeedMmPerS`. Activities map to a group through `SegmentSport.of` (`SegmentSport.kt`). Today resort `ski` and `snowboard` map to nothing.
- **Matching tolerances.** These are global in `SegmentGeometry` (`core/location`): a 25 m start/end radius and a 30 m corridor. `passes()` already takes them as defaulted parameters, but the matcher always uses the defaults.
- **Candidate selection.** The matcher picks segments by cell, `Segments.sport == group` and live status (`SegmentMatcher.replaceEfforts`). Candidate riders use the same test (`replaceCandidateRiders`).
- **Ranking.** Leaderboards and Mayors share `boardOf` and `rankable()` in `api/SegmentViews.kt`. `notRankedReasons` and `activitySegments` attach a rank and a reason only on `active` segments.
- **Naming.** Eligibility is one function, `mayPropose()` (`segments/SegmentNames.kt`), used by both the indexer and `getSegment.canProposeName`. The naming pass lives in `SegmentNaming`.
- **Importer.** OSM rules are a `TrailRules` table (`TrailTags.kt`), and `directions()` returns the allowed directions along node order. The importer never reads elevation. The candidates file carries an `IMPORTER_VERSION` that `stage` checks.
- **Database.** The latest migration is `V48__activity_invites.sql`.

## Goals / Non-Goals

**Goals:**
- Reuse the segment pipeline for both downhill runs and lifts, without a parallel matcher, record type or table set.
- Keep a lift's ride data exactly as private as an effort.
- Keep every existing segment, candidate id and board unchanged.

**Non-Goals:**
- Using elevation to decide direction, or to detect lift rides outside a mapped lift.
- Naming the existing FIT lift rest laps after matched lifts.
- Piste difficulty, resorts as entities, and piste areas with no centreline.

## Decisions

### A separate `alpine` group rather than widening `ski`
Lift-served descents are a different motion from nordic skiing: they are gravity-driven and downhill only, need a wider corridor, and are tested against a different speed range. A separate group keeps nordic segments, their duplicate checks and their candidates untouched, and lets `alpine` carry its own corridor and cap. Backcountry `ski` stays in `ski`: its tours climb and descend off-piste, and moving it would change existing efforts.
*Alternative:* put resort skiing into `ski`. That mixes nordic and downhill lines in one duplicate space and one board family, so it was rejected.

### A `kind` field on `feed.segment` rather than a new lift record
A lift needs everything a segment has (a public line, an importer, candidates, ridden checks, statuses, efforts at their tier, and removal with the track) and lacks only ranking and naming. An optional open-enum `kind` (`run` | `lift`, absent = `run`) reuses all of that. The kind goes into `Segments` and `SegmentCandidateRows` as a non-null column defaulting to `run`, so existing rows need no backfill beyond the default. The AppView drops a record with an unknown kind, as it does an unknown sport group.
*Alternative:* a `feed.lift` record with its own tables and matcher job. That duplicates the matcher, candidate staging and visibility rules for no behavioural gain, so it was rejected.

### Lifts only from the importer
A lift is excluded from leaderboards, so an athlete-made "lift" would be a way to hide a segment from ranking, and it would compete with the importer's line in duplicate checks. The indexer therefore accepts `kind: lift` only from `SEGMENT_IMPORTER_DID`. That is the same account check that already exempts the importer from the daily creation limit.

### Direction from drawn order
OSM's conventions are that downhill pistes are drawn top to bottom and aerialways bottom to top. The importer already emits candidates along node order, so `TrailTags.directions` returns forward only for `alpine`. The matcher is already direction-sensitive.
*Alternative:* read elevation (SRTM or OSM `ele`). That adds a dataset and a dependency, and it is unnecessary while the convention holds. A wrongly drawn way gives a candidate no one rides, so it is never published (see Risks).

### Matching tolerances per group and kind
Add `SegmentSportGroup.passTolerance(group, kind)`, returning the start/end radius and the corridor: 40/50 m for an `alpine` run, and today's 25/30 m for everything else, lifts included. Pass it to `SegmentGeometry.passes()` in both `replaceEfforts` and `replaceCandidateRiders`, so candidate riders and efforts stay one test. The duplicate test keeps its 30 m distance, because two published pistes 40 m apart are different runs.

### Kind is checked where ranking and naming are decided
- `rankable()` excludes lifts, so leaderboards and Mayors return no entries for a lift, through the existing empty-board path.
- `activitySegments` gives no `best`, `rank` or `notRanked` on a lift effort. Without this, `notRankedReasons` would report a lift ride as `IMPLAUSIBLE`.
- `mayPropose()` requires kind `run`, which covers both the indexer and `canProposeName`. The naming pass's `isActive` filter skips lifts too.
- `duplicatesOf(sport, line)` becomes `duplicatesOf(sport, kind, line)`. Both the indexer and `checkSegmentCandidates` use it.

### Speed cap and boards
The `alpine` cap is 120 km/h, the same as `ski`. Speed-skiing records are far above it, but resort GPS spikes and snowmobile or ski-patrol rides are not. `plausibleEffort()` already ORs over `SegmentSportGroup.all`, so adding the group covers it. `SegmentBoard.all` gains `snowboard`, so `alpine` boards split into `ski` and `snowboard` like any other board type. The lexicon board `knownValues` gain it too.

### Importer: one candidate per lift, a new format version
In `TrailTags`, `wayGroups` adds `alpine` for `piste:type=downhill`, falling back to `piste:name`. `relationGroups` adds `route=piste` + `piste:type=downhill`. Lifts don't go through trail joining or `CandidateCutter`'s junction cuts. A small `LiftRules` pass emits one candidate per qualifying `aerialway` way, using the existing id scheme (direction, the way, its ends) with `alpine-lift` in place of the group, so a lift's id never meets a run's along the same way and existing ids don't change. The candidate carries `kind`, the importer lexicon `candidate` gains it, and `IMPORTER_VERSION` goes up so `stage` refuses older files. `publish` writes the kind, and `refine` keeps it.

### Client
- `SegmentViewBasic` gains `kind`, and `SegmentKind` constants go in `core/lexicon`.
- In `feature/detail`, the presenter splits the `getActivitySegments` efforts by kind. Runs go through the existing `SegmentEffortGroups`. Lifts are aggregated in the `-api` module (like `SegmentEffortGroups.kt`) into one row per lift (name, count, total elapsed time, ordered by first ride), so the logic is unit-testable and the UI stays dumb.
- `ActivitySegmentsUi` draws a Lifts card. The segment screen hides the leaderboard, Mayor and naming sections when `kind == lift`.
- Creation needs no new branch: `SegmentSport.of` now returns `alpine` for resort days, and the record omits `kind`, which means `run`.

## Risks / Trade-offs

- **Downhill speed leaderboards reward speed on shared slopes** → The user chose full public boards. Mitigations already in place: the 120 km/h cap, per-athlete and per-activity leaderboard opt-outs, and the effort tier rules. Revisit if resorts or users object.
- **Older AppView ignores `kind`** → Every lift is `alpine`, a group an old AppView doesn't know, so it drops lifts rather than indexing them as runs. Deploy the AppView (migration V50) before any importer run that publishes alpine segments.
- **Older clients show a lift as an ordinary segment** → Its leaderboard and Mayors are empty and it has no naming, so the only artefact is a lift listed among efforts with a "PR" mark from an old client. Acceptable during rollout.
- **Wrongly drawn piste or lift ways** → Their candidates get no riders, so they are never published. Nothing wrong is served.
- **Piste centrelines drawn off to one side of a wide piste** → The 50 m corridor covers typical pistes. Very wide bowls mapped only as areas are out of scope and give no candidate.
- **An athlete creates an `alpine` "run" from the uphill part of a ski day (a lift ride)** → It would rank lift rides. It needs three distinct riders to become active, like any segment. A creation-time guard (refusing a climbing selection) can follow if this shows up; it isn't specced here.
- **The wider corridor lets nearby parallel pistes match the same pass** → Each match is a real pass of that line within 50 m. Nested and overlapping grouping on the activity page already handles several efforts over one stretch.

## Migration Plan

1. Land the lexicon and `core/lexicon` changes together (`alpine`, `kind`, `snowboard` board).
2. Deploy the AppView with `V50__segment_kind.sql`, which adds `kind varchar not null default 'run'` to `segments` and `segment_candidates`. It also enqueues a `track` rematch of every segment track, as `V43__rematch_segment_tracks` did (SQL can't tell a track's sport portably), so past resort days give candidate riders and efforts.
3. Release clients.
4. Re-extract resort regions with the new importer version, stage them, and publish once they are ridden.

Rollback: leave the AppView's new column in place (it is additive). An older AppView build ignores the column, and its group check drops `alpine` segments.
