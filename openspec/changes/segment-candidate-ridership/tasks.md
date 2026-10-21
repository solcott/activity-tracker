# Tasks

## 1. Lexicons

- [x] 1.1 Add `lexicons/app/activitytracker/importer/{defs,putSegmentCandidates,finishSegmentCandidates,getRiddenSegmentCandidates}.json` per the design (limits: 500 per batch and page, ids and regions ≤ 64), and mirror them in `core/lexicon` (models, `XrpcProcedure`/`XrpcQuery` descriptors, `Nsid` constants, not in `indexedCollections`); verify a `core/lexicon` round-trip test with `AtJson` and `./gradlew :core-lexicon:jvmTest` pass, then run the `lexicon-auditor` agent on the new NSIDs

## 2. Config and storage

- [x] 2.1 Add `segmentImporter` (`SEGMENT_IMPORTER_DID`) and `segmentCandidateRiders` (`SEGMENT_CANDIDATE_RIDERS`, ≥ 1, default 3) to `AppViewConfig` and pass both through `server-appview`'s `run` task from `docker/.env`; verify `AppViewConfigTest` covers set, unset and invalid values
- [x] 2.2 Add Flyway `V32__segment_candidates.sql` (V31 went to `segment_leaderboards`) (`segment_candidates`, `segment_candidate_cells`, `segment_candidate_riders`, indexes, a header comment in the style of V29/V30) and the Exposed tables in `db/Tables.kt`; verify `MigrationVersionsTest` and `./gradlew :server-appview:test` pass on H2

## 3. Staging calls

- [x] 3.1 Implement candidate validation (reusing `validLine` and the name rules; `[a-z0-9-]{1,64}` for id and region) and upsert (same line keeps riders and `ridden`; changed line or sport clears them, replaces cells, enqueues a `candidate` job); verify a `SegmentCandidatesTest` covers the bad-line batch, re-staging unchanged, and re-staging changed
- [x] 3.2 Implement `finishSegmentCandidates` (delete the region's rows of other revisions with cells and riders); verify the "trail removed from OSM" scenario test, and that another region is untouched
- [x] 3.3 Add the three routes behind `requireViewer` plus the importer check (`403 NotImporter`, also with no importer configured) and `getRiddenSegmentCandidates` paging by id; verify tests for the importer, an athlete, no importer, 501 candidates, and that the listing holds no counts, athletes or dates

## 4. Counting riders

- [x] 4.1 Add `SegmentJobs.CANDIDATE` and its matcher handler (enqueue `track` jobs for usable tracks sharing the candidate's cells); verify a `SegmentMatcherTest` case where staging a candidate over three athletes' past rides leads to three riders and `ridden`
- [x] 4.2 Record riders in the `track` job after `replaceEfforts` (candidates of the group in the track's cells, `SegmentGeometry.passes`, replace the track's rider rows, sticky ridden check against `segmentCandidateRiders`); verify tests for the wrong sport group, a partial pass, private-activity tracks counting, and the third-rider promotion
- [x] 4.3 Delete rider rows wherever track-derived data goes (`trackToMatch`'s `none()`, `dropTracks`, `dropSegmentTracks`); verify the "candidate riders dropped with the tracks" scenario and that a ridden candidate stays ridden
- [x] 4.4 Verify candidates stay out of segment features: tests that `searchSegments`, `getActivitySegments` and `checkSegment` ignore a staged candidate and that it never gets efforts or affects a segment's status

## 5. Docs

- [x] 5.1 Update AGENTS.md's architecture paragraph (candidate riders are private data the AppView holds, dropped with segment tracks; the importer calls) and `.agents/rules/appview.md`; add `SEGMENT_IMPORTER_DID` and `SEGMENT_CANDIDATE_RIDERS` to the local-stack skill's env notes; verify `scripts/check-agent-sync.sh` passes
- [x] 5.2 Run `./gradlew check` and verify it passes
