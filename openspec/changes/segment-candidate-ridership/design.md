# Design

## Context

`SegmentMatcher` runs `track` jobs that fetch a usable segment track's blob, record its cells in `segment_track_cells`, and replace its activity's efforts with its passes (`SegmentGeometry.passes`) over live segments of its sport group that share a cell. A `segment` job enqueues `track` jobs for every usable track sharing a cell with a new or changed segment. `dropSegmentTracks`/`dropTracks` in `SegmentIndex.kt` are the one path that removes everything derived from an athlete's tracks. Statuses use the same "count distinct authors, promote, never demote" shape (`promote`). Privileged callers already exist: moderator routes check the caller and throw `XrpcError(403, "NotModerator", …)`.

The segment-track consent text says routes are shared "to find segments"; counting riders to decide which trail segments to publish falls within that, so the prompt doesn't change. The spec that limits the AppView's use does, hence the `segments/segment-track` delta.

## Goals / Non-Goals

**Goals:**
- Reuse the existing matcher pass and job queue; no second matching loop.
- Reveal to the importer one bit per candidate (ridden or not), after a threshold, never counts.
- Keep rider rows strictly derived from usable tracks, dropped on the same path as efforts.

**Non-Goals:**
- The importer's staging and publishing commands, the service account and its PDS login: `segment-import-publishing`.
- Hiding a candidate once a segment over it is published: also `segment-import-publishing`, which knows the link.
- Any client change.

## Decisions

**The AppView is the staging store.** Candidates are public OSM-derived lines, so holding them is no privacy cost, and the AppView is the only place that can match them against tracks. *Alternatives:* (a) a privileged "count riders on this line" query called per candidate: synchronous, would fetch blobs per request, and lets the caller probe arbitrary lines for activity. Rejected. (b) Publish every candidate as a `proposed` `feed.segment` and let statuses sort them: puts tens of thousands of unused records in a public repo, runs into the 10/day limit, shows proposed efforts on athletes' activities, and archives most after 180 days. Rejected; that's the clutter this item avoids.

**Tables (Flyway V31, H2-portable).**
- `segment_candidates`: `id` (PK, ≤ 64), `region` (≤ 64), `revision` (≤ 64), `sport`, `candidate_name`, `line`, `distance_m`, bbox `e7` columns, `data_source`, `license`, `ridden` (boolean), `staged_at`; indexes on `(region, revision)` and `(region, ridden, id)`.
- `segment_candidate_cells (cell, candidate_id)`, like `segment_cells`.
- `segment_candidate_riders (candidate_id, track_uri, author_did)`, PK `(candidate_id, track_uri)`, indexes on `track_uri` and `author_did`.

**Validation reuses `validLine` and the name rules** from `SegmentRecordIndex.kt`, so a candidate that stages can be published as a segment unchanged. `id` and `region` must match `[a-z0-9-]{1,64}`. Rejections are returned per candidate rather than failing the batch, so one bad OSM way doesn't stall a region.

**Re-staging.** An upsert with the same line and sport keeps `ridden` and riders and just updates name, revision and `staged_at`. A changed line or sport clears riders and `ridden`, replaces cells and enqueues a `candidate` job. `finishSegmentCandidates` deletes `region = ? AND revision <> ?` rows with their cells and riders.

**Counting in the `track` job.** After `replaceEfforts`, `replaceCandidateRiders` finds candidates of the track's group sharing its cells, tests `SegmentGeometry.passes` on the resampled candidate line, deletes the track's rider rows and inserts one per candidate passed. Then, for those candidates, the ridden check: `count(distinct author_did) >= candidateRiders` sets `ridden = true`. `trackToMatch`'s `none()` and `dropTracks` delete rider rows by `track_uri`; `dropSegmentTracks` also by `author_did`. A new `candidate` job kind (`SegmentJobs.CANDIDATE`) mirrors `matchSegment`: enqueue `track` jobs for usable tracks sharing the candidate's cells. *Alternative:* a separate candidate-only job that matches just the candidate against each track. Better for big imports (it skips re-matching segments), but needs a second blob-fetch path; deferred until import volume shows it matters (see Risks).

**Ridden is sticky.** Like `active`, so the importer's view doesn't flicker as athletes toggle matching, and so a drop in riders can't be timed to learn who left.

**Lexicons in a new `app.activitytracker.importer` namespace,** since they're no feed feature and no client calls them: `putSegmentCandidates` (input `{region, revision, candidates[≤500]}`, output `{rejected: [{id, reason}]}`), `finishSegmentCandidates` (input `{region, revision}`, output `{removed}`), `getRiddenSegmentCandidates` (params `region`, `cursor`, `limit` ≤ 500; output `{candidates: [#candidate], cursor?}`), with a shared `#candidate` def. Mirrored in `core/lexicon` with `Nsid` constants, but not added to `Nsid.indexedCollections` (they're calls, not records).

**Authorization.** `AppViewConfig.segmentImporter: Did?` from `SEGMENT_IMPORTER_DID`; `SEGMENT_CANDIDATE_RIDERS` (≥ 1, default `SegmentMatcher.DEFAULT_ACTIVE_ATHLETES`). Routes use `requireViewer`, then compare the caller with the importer DID and throw `XrpcError(403, "NotImporter", …)`; an unset importer refuses everyone the same way.

## Risks / Trade-offs

- [Staging a region enqueues a `track` job per nearby track, each re-matching segments too] → Jobs are keyed by subject, so a track is matched once however many candidates it passes near. If a first import's backlog is too slow, add the candidate-only job described above.
- [Many candidates per trail mean more pass tests per track] → Cell lookup limits candidates to those near the track; passes are cheap compared with the blob fetch.
- [The importer, holding the DID's credentials, could stage lines to probe where athletes go] → It learns only ridden-at-3+ for lines it staged, the same bit any athlete learns when a proposed segment turns active. The DID is our own service account, set by the operator.
- [Rows linger for regions never finished] → They stay staged until a later run finishes the region; harmless, and visible by region in the table.

## Migration Plan

Additive migration; no backfill: candidates exist only once staged. Rolling back removes the routes; the tables can stay until a later migration drops them.
