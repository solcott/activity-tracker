# Proposal

## Why

`osm-trail-candidates` turns an OSM extract into candidate lines, and `segment-candidate-ridership` tells which of them people actually ride, but nothing publishes them yet. This change is the last piece of the split-up `segment-osm-import` roadmap item. It stages a region's candidates from the importer and publishes each ridden one as a `feed.segment` from a service account, so well-ridden trails get segments nobody had to create. `segment-attribution` already credits OSM on the segment screen.

## What Changes

- **The service account.** The importer signs in to its own account (a DID and an app password) on a PDS. It gets service-auth tokens there for the AppView's importer calls and `feed.checkSegment`, and writes segments to its own repo. The AppView's `SEGMENT_IMPORTER_DID` names this account.
- **`stage`.** A new importer command uploads a region's candidates file in batches of 500 (`importer.putSegmentCandidates`), reports the candidates the AppView rejected, and then finishes the run (`importer.finishSegmentCandidates`).
- **`publish`.** A new importer command lists the region's ridden candidates and checks each with `feed.checkSegment`. It publishes each one that duplicates no live segment as a `feed.segment`, with `source: osm` and `license: ODbL-1.0`. The record key is the candidate id, and publishing never overwrites an existing record, so re-running never doubles or rewrites a segment.
- **`feed.segment` record keys.** The lexicon's key changes from `tid` to `any`. Athletes' segments keep TID keys; the importer's segments are keyed by candidate id.
- **Published candidates leave the listing.** `getRiddenSegmentCandidates` stops listing a candidate once a segment from the importer under its id is indexed.
- **No creation limit for the importer.** The 10-per-day limit stays on athletes. The importer's account is exempt, because pacing a region's hundreds of ridden trails under it would take months.
- **Published segments are left alone.** The importer never edits or deletes a segment it published. An OSM edit that changes a candidate's id stages a new candidate, which is published only if it duplicates no live segment.
- **What happens after.** A published segment is matched like any other, including against past tracks. Its candidate already had enough riders, so it turns `active` on its first match.
- **Ops.** A local `importer.test` service account, a script that runs `extract`, `stage` and `publish` for one region (Vermont first), and docs in the local-stack skill.

## Capabilities

### New Capabilities
- `segments/segment-import`: How staged candidates are published: from which account, which candidates, how duplicates and re-runs are handled, and what the importer never does to a segment it published.

### Modified Capabilities
- `segments/segment-records`: the creation limit doesn't apply to the importer account.
- `segments/segment-candidates`: a candidate stops being listed once its segment is published.

## Impact

- **Importer (`server/segment-importer`):** the `stage` and `publish` commands; an app-password session and service auth on top of `core/atproto`'s `XrpcClient` and `ServiceAuthAuthenticator`.
- **Lexicons:** `feed.segment`'s key becomes `any`. No Kotlin model change.
- **AppView:** the creation limit skips the importer DID, and `getRiddenSegmentCandidates` leaves out published candidates.
- **Ops:** the service account locally (`docker/scripts`), its credentials in `docker/.env`, and a run script. A production schedule and account are set up when production exists.
- **Dependencies:** `osm-trail-candidates`, `segment-candidate-ridership` and `segment-attribution`, all applied.
