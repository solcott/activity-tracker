# Proposal

## Why

The split-up `segment-osm-import` item publishes an imported segment only once people actually ride it, so the map doesn't fill with segments nobody uses. Only the AppView can tell: it alone holds athletes' segment tracks, under the consent of `segment-track-consent`, and it must never serve them. This change lets the AppView hold staged candidate lines from the importer, count distinct riders on each from the segment tracks it already matches, and tell the importer only which candidates have been ridden enough. It needs no importer to build: the AppView side is tested on its own, and `segment-import-publishing` later stages and publishes from `server/segment-importer`.

## What Changes

- **The importer account.** A new AppView setting, `SEGMENT_IMPORTER_DID`, names the one account allowed to stage candidates and read which are ridden. Without it, those calls are refused.
- **Staging.** `importer.putSegmentCandidates` stores a region's candidates (id, sport group, name, line, source, licence) in batches, validated like segments; `importer.finishSegmentCandidates` ends an import run and removes the region's candidates that run didn't stage. Candidates are never served by any `feed.*` query, never matched for efforts, and never counted as duplicates of segments.
- **Ridership.** The matcher's existing track job also records which athletes' tracks pass each candidate of their sport group, by the same pass test as efforts; a newly staged or changed candidate is matched against past tracks. A candidate becomes *ridden* once a configured number of distinct athletes (`SEGMENT_CANDIDATE_RIDERS`, 3 by default) have passed it, and stays ridden.
- **What the importer learns.** `importer.getRiddenSegmentCandidates` lists a region's ridden candidates. It never says how many athletes, who, or when.
- **Consent.** What the AppView may do with segment tracks widens from "match segment efforts" to "match segment efforts and count riders on staged candidates". Rider rows are dropped with the tracks, like efforts.

## Capabilities

### New Capabilities
- `segments/segment-candidates`: Staged candidate segments in the AppView: who may stage and read them, how they're validated and replaced, how riders are counted, and what is revealed.

### Modified Capabilities
- `segments/segment-track`: the AppView may also use segment tracks to count riders on staged candidates, and drops those counts with the tracks.

## Impact

- **Lexicons:** a new `app.activitytracker.importer` namespace with `putSegmentCandidates`, `finishSegmentCandidates` (procedures) and `getRiddenSegmentCandidates` (query); `core/lexicon` models, descriptors and `Nsid`.
- **AppView:** `AppViewConfig` (`SEGMENT_IMPORTER_DID`, `SEGMENT_CANDIDATE_RIDERS`); Flyway migration for `segment_candidates`, `segment_candidate_cells`, `segment_candidate_riders`; a `candidate` job kind and rider recording in `SegmentMatcher`; cleanup in `dropSegmentTracks`; routes and views.
- **Docs:** AGENTS.md's list of private data the AppView holds, and `.agents/rules/appview.md`.
- **Dependencies:** none beyond the applied `segment-track-consent`, `segments-core` and `segment-creation`.
