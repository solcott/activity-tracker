# Proposal

## Why

`osm-trail-candidates` turns an OSM extract into candidate lines and `segment-candidate-ridership` tells which candidates people actually ride, but nothing publishes them yet. This change is the last piece of the split-up `segment-osm-import` roadmap item: it stages a region's candidates from the importer, and publishes each ridden one as a `feed.segment` from a service account, so well-ridden trails get segments nobody had to create.

Design, specs and tasks are written once `osm-trail-candidates` and `segment-candidate-ridership` are applied: this change drives the importer's output into the AppView's staging calls and publishes what they report.

## What Changes

- **The service account.** The importer signs in to its own account (its own DID, an app password) on a PDS, gets service-auth tokens for the AppView's importer calls, and writes segments to its repo with `putRecord`. The AppView's `SEGMENT_IMPORTER_DID` names this account.
- **`stage`.** A new importer command uploads a region's candidates file in batches (`importer.putSegmentCandidates`) and finishes the run (`importer.finishSegmentCandidates`).
- **`publish`.** A new importer command lists the region's ridden candidates, checks each with `feed.checkSegment`, and publishes those that duplicate no live segment, with `source: osm` and `license: ODbL-1.0`. The record key is derived from the candidate id, so publishing is idempotent and re-running never doubles a segment.
- **What happens after.** A published candidate is matched like any segment, from past tracks, so it turns `active` quickly. Candidates that duplicate a published or athlete segment stay unpublished. The AppView stops listing a candidate once its segment is indexed. Whether a trail edit in OSM updates or replaces a published segment is decided in design.
- **Limits.** The importer's segments are exempt from the 10-per-day creation limit, or the importer paces itself under it; decided in design.
- **One region first,** chosen in design, with the steps to run it written down.
- **Ops.** The service account on the local PDS (`docker/scripts`), scheduling `extract`/`stage`/`publish`, and docs in the local-stack skill.

## Capabilities

### New Capabilities
- `segments/segment-import`: How staged candidates are published: from which account, when, how duplicates and re-runs are handled, and how published segments relate to their candidates.

### Modified Capabilities
- `segments/segment-records`: segments from the importer account and the creation limit.
- `segments/segment-candidates`: a candidate stops being listed once its segment is published.

## Impact

- **Importer:** `stage` and `publish` commands, PDS sign-in and service auth through `core/atproto`.
- **AppView:** the creation-limit rule for the importer DID; linking indexed importer segments to their candidates.
- **Ops:** the service account locally and in production, credentials, a schedule.
- **Dependencies:** `osm-trail-candidates`, `segment-candidate-ridership`; `segment-attribution` should land first so imported segments show their credit.
