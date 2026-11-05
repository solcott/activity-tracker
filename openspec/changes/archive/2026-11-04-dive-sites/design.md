# Design

## Context

See proposal.md for motivation. The parts of the existing system this change leans on:

- **Dive positions.** A dive without GPS records keeps its entry and exit positions, or else the logbook site's position, as its private track in `self` (`feed/dive-data`). While segment matching or grouping is on, every activity with a full track gets a `feed.segmentTrack` with its privacy zones cut out (`segments/segment-track`). Those tracks are what the AppView may use for matching (`usableSegmentTracks()`).
- **Matcher and status.** `SegmentMatcher` drains `segment_match_jobs` (`track`, `segment`, `candidate`, `tiers`, `group`) and runs an hourly status pass and naming pass. Track cells are already indexed (`SegmentTrackCells`, 100 cells per degree, `core/location/SegmentCells.kt`).
- **Naming.** Naming is `mayPropose()` (`segments/SegmentNames.kt`), shared by the indexer and `canProposeName`, plus `SegmentNaming`. It runs over `SegmentNameProposals` and `SegmentNameVotes`. Votes live in the `interactions` space, and adding a collection to a space declaration forces every athlete to sign in again (lexicons rule).
- **Boards.** Mayors boards (`api/SegmentViews.kt` `mayors()`, `boardOf`, `rankable()`) already count efforts per athlete per window with leaderboard opt-outs.
- **Detail categories.** `DetailCategory` has `map` and `dive` categories. Dive summary fields of a narrower tier live in a `feed.activityDetails` record of that tier.
- **Importer.** `OsmData` keeps only ways and relations of the trail network and their node coordinates. It keeps no tagged nodes.
- **Database.** The latest migration is V48 (V49 is a PostgreSQL-only vendor one). `alpine-segments` takes V50, so this change takes the next free number when it lands.

## Goals / Non-Goals

**Goals:**
- Reuse the segment consent, track, job queue, status, board and naming machinery, adding a point-shaped place beside the line-shaped segment.
- Never serve a dive position, and never let a site's conditions or regulars reveal a dive the viewer couldn't otherwise see.
- No new space collections, so no re-sign-in.

**Non-Goals:**
- Using exit positions or underwater tracks for matching, or matching by the logbook's site text.
- Linking a dive to a site by hand, or overriding a match.
- A browse map, depth or time boards, photos, and site descriptions.

## Decisions

### A new `feed.diveSite` record, not a segment kind
A segment is a directional line with a length, efforts, timing and leaderboards. A site is a point with a radius and none of those. Folding sites into `feed.segment` would mean special-casing almost every segment rule. A separate record gets its own small indexer and tables, while sharing status, job and board helpers through code.
*Alternative:* `feed.segment` with `kind: site` and a one-point line. That was rejected because it breaks the length, duplicate (Fréchet) and effort invariants.

### Reuse `feed.segmentNameProposal` and `feed.segmentNameVote`
The proposal's `segment` field is an AT-URI. A dive site URI there is told apart by its collection. The vote's subject is the proposal, so the vote needs no change at all. This keeps the `interactions` space declaration as it is, so nobody signs in again. The lexicon descriptions are updated to say "a segment or dive site". In the AppView, the naming code is generalized over the subject: `mayPropose`, the voter check and the adoption denominator take a `NamedPlace` (segment: efforts; site: visits). The existing proposal, vote and history tables hold site URIs unchanged.
*Alternative:* `feed.diveSiteNameProposal` / `feed.diveSiteNameVote`. That is cleaner naming but forces a space-declaration change and a re-sign-in, so it was rejected.

### Visits from the existing segment track, under segment-matching consent
The user chose segment matching as the consent. The `track` job, which already loads a usable segment track, also computes the dive's visit when the activity's sport is `dive` and its subSport isn't a pool or indoor one. The entry is the first position of the first piece. The cut track has already dropped anything in a privacy zone, so a shore dive starting inside a zone uses its first position outside it, or has no visit. A new `site` job, enqueued when a site is indexed or moved, matches the site against tracks in its cells. Dives use only `usableSegmentTracks()`, never the grouping consent.

### Storage
`dive_sites` (uri, creator, name, current_name, lat/lon at 1e-7, radius_m, source, license, attribution, status, duplicate_of, first_indexed_at), `dive_site_cells` (the cells the radius touches; at most 4 for a 500 m radius at 100 per degree), and `dive_site_visits` (site, activity, athlete, visibility, started_on, plus the dive's `min_temperature`, `visibility_cm`, `max_depth_cm` and the Dive category's tier). Copying those three values and their tier onto the visit, recomputed by the `tiers` job and on activity or details changes, makes conditions a filtered aggregate over one table rather than a join into activity JSON.

### Visibility reuses `visibleTo`
A visit's tier is the narrowest of the activity's and Map's, stored as `visibility`. Listing, regulars and `activityView.diveSite` filter by it with the existing `visibleTo` and block rules. Conditions also require the stored Dive tier to admit the viewer. The AppView then reads at most the 2,000 newest such rows and computes the median, extremes and per-month buckets in Kotlin, which keeps the SQL H2-portable and the work bounded.

### Status and duplicates
The status pass gains a site step: promote at the configured distinct-athlete threshold, and archive after 180 days, sharing `DEFAULT_ACTIVE_ATHLETES` and `ARCHIVE_AFTER`. A duplicate is a centre within 50 m of an earlier live site. That is deliberately tighter than the radius, so neighbouring reefs can coexist, and overlapping radii are resolved per dive by nearest centre. The daily limit has its own counter, like `SegmentCreations`, and the importer is exempt.

### Regulars reuse the Mayors board shape
`getDiveSiteRegulars` runs the Mayors counting query (`boardOf`) over `dive_site_visits` instead of efforts: per-athlete counts in a window, opt-outs from `actor.segmentPrefs` and `groupExcluded`-style segment-track exclusion, ties by the date the count was reached, the viewer's own entry, and paging. Its windows are all time (the default) and this or last year, since dives are far sparser than rides.

### Importer: sites outside the candidate pipeline
Segment candidates exist so the importer publishes only lines that someone actually rides, which keeps unridden OSM trails out of the network. OSM dive sites are few, are deliberately mapped dive spots, and become archived anyway if nobody dives them. So `extract-sites` writes a small NDJSON sites file (its own format version). `publish-sites` checks them in pages through `importer.checkDiveSites` (importer-only, like `checkSegmentCandidates`) and writes `feed.diveSite` under the site's id. `OsmData` gains a pass that keeps tagged nodes, and way or relation centroids, for the dive tags. `scripts/segment-import.sh` gets a `--sites` option.

### Client
A new `feature/divesite` (`-api`, `-presenter`, `-ui`), scaffolded with the `new-screen` skill:
- `DiveSiteScreen`: map pin and radius, names, conditions, top regular, dives.
- `CreateDiveSiteScreen`: name, a radius slider over a map, the public warning, the duplicate check, the outbox write.
- `DiveSiteRegularsScreen`.

The naming UI (`SegmentNamesUi`, `ProposeSegmentNameScreen`) moves to `:app-common-ui` / `:app-common-api`, taking a place URI. Segments and sites both open it, and `feature/segment` keeps working through the shared module (module-boundary rule). The dive page (`feature/detail`) shows `activityView.diveSite` and the create-site action. Site writes go through `ActivityRepository`'s outbox like segment creation.

## Risks / Trade-offs

- **One- or two-position dive tracks may not count as usable segment tracks, or may never be written** → Task 2.1 checks the client writes them and `usableSegmentTracks()` accepts a single piece of one position. If it doesn't, extend the usability rule for dives only, without loosening it for other sports.
- **Surface GPS drifts from the true entry (a boat drift, a late fix)** → The radius (100 m by default for athletes, 150 m for OSM) absorbs typical drift. Nearest-centre resolution keeps overlapping sites sane.
- **Publishing a site exposes a precise point near where an athlete was** → Creation refuses entries inside zones or the hidden start, warns that the site is public, and names the source dive only when it and its Map category are Public. A visit itself is never served beyond its tier.
- **Small-sample conditions** → They are drawn only from dives the viewer may already see, so they reveal nothing new. The dive count is shown so viewers can judge them.
- **Reusing the segment name records is a naming wart** → It is documented in both lexicon descriptions. Renaming the collections later would cost the same re-sign-in we're avoiding now.
- **OSM `sport=scuba_diving` is also put on shops and clubs** → The extract excludes `shop`, `amenity`, `club` and `building` features. Sites nobody dives are archived after 180 days.

## Migration Plan

1. Land the lexicons and `core/lexicon` (`feed.diveSite` added to `Nsid.indexedCollections`).
2. Deploy the AppView with the dive-site migration, which also enqueues a `track` rematch for opted-in athletes' dive tracks (as V43 did, filtered to dive activities), so past dives get visits once sites exist.
3. Release clients.
4. Run `extract-sites` / `publish-sites` per region.

Rollback: an older AppView doesn't subscribe to `feed.diveSite` and ignores proposals naming a site URI it hasn't indexed. The new tables are additive.
