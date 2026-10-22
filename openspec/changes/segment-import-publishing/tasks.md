# Tasks

## 1. Lexicon and AppView

- [ ] 1.1 Change `lexicons/app/activitytracker/feed/segment.json`'s `key` from `tid` to `any`, and say in its description that athletes' segments use TIDs and the importer's use candidate ids; update `SegmentRecord`'s KDoc to match; verify the `lexicon-auditor` agent reports no mismatch for `feed.segment`
- [ ] 1.2 Pass the configured importer DID into `Indexer` and `SegmentViews` (`Application.appView` and `main`), skip the creation limit in `upsertSegment` for that author, and have `checkSegment` report `DAILY_SEGMENT_LIMIT` as the importer's `remaining`; verify `SegmentsTest` cases where the importer's repo gains 11+ new segments in a day and all are indexed, an athlete's eleventh is still dropped, and the importer's `checkSegment` allowance stays 10
- [ ] 1.3 Leave published candidates out of `getRiddenSegmentCandidates` (a `NOT EXISTS` on the importer's `feed.segment` URI keyed by the candidate id, any status); verify `SegmentCandidatesTest` cases for a published candidate left out while the region's others are still listed, and an athlete's segment under the same rkey not hiding it
- [ ] 1.4 Update `.agents/rules/appview.md` (the importer's limit exemption, and the listing leaving out published candidates) and AGENTS.md's candidate sentence; verify `scripts/check-agent-sync.sh` and `./gradlew :server-appview:test` pass

## 2. Importer session and clients

- [ ] 2.1 Add `core-atproto` and the Ktor client engine to `server/segment-importer`, and an importer `Settings` read from `SEGMENT_IMPORTER_IDENTIFIER`, `SEGMENT_IMPORTER_PASSWORD`, `SEGMENT_IMPORTER_PDS_URL`, `APPVIEW_URL` and `APPVIEW_DID` (with the AppView's local defaults); fill unset ones from `docker/.env` in the `run` task; verify a `SettingsTest` covers defaults, and a missing identifier, password or PDS URL named in the error
- [ ] 2.2 Add `PasswordSession`, a `RequestAuthenticator` that signs in with `com.atproto.server.createSession` and the `CreateSession`/`RefreshSession` descriptors it needs in `core/lexicon` `atproto/Server.kt`, sends the access JWT, and on 401 refreshes or signs in again; verify a `PasswordSessionTest` on Ktor `MockEngine` covers first sign-in, reuse, refresh after 401, and a refused password
- [ ] 2.3 Build the importer's PDS `XrpcClient` (with `PasswordSession`) and AppView `XrpcClient` (with `ServiceAuthAuthenticator` for `APPVIEW_DID`); verify on `MockEngine` that an AppView call carries a token minted with `aud` = `APPVIEW_DID` and `lxm` = the method, and that `403 NotImporter` surfaces as an error saying the account isn't the configured importer

## 3. `stage`

- [ ] 3.1 Add the `stage <candidates.ndjson>` command: read the header (refusing an unknown `importerVersion` or a line of another region), upload in batches of 500 under a UTC-timestamp revision, print rejected ids with reasons, finish the run only after every batch succeeded, and print the staged count; update `USAGE`; verify a `StageTest` on `MockEngine` covers 1,200 candidates in three batches then one finish, a rejected candidate reported, a failed second batch with no finish, and an unknown version refused before any call

## 4. `publish`

- [ ] 4.1 Add the `publish --region <id>` command: page `getRiddenSegmentCandidates`, run `feed.checkSegment` for each, skip duplicates, `getRecord` then `createRecord` (`rkey` = candidate id, `validate: false`) a `feed.segment` with the candidate's sport, name and line, `source` `osm`, `license` `ODbL-1.0` and `createdAt` now; stop on the first transport or rate-limit error; print published, duplicate and already-present counts; update `USAGE`; verify a `PublishTest` on `MockEngine` covers a publish, a duplicate skipped, an existing record left untouched (no write), a `createRecord` conflict counted as already present, paging past 500, and a 429 stopping the run
- [ ] 4.2 Verify end to end on the local stack: stage a small candidates file, seed three athletes' rides over one candidate with `docker/scripts/seed-effort.sh`, run `publish`, and check `importer.test`'s repo holds one `feed.segment` under the candidate id that `getSegment` shows `active`, a second `publish` writes nothing, and `getRiddenSegmentCandidates` no longer lists it

## 5. Ops and docs

- [ ] 5.1 Add `scripts/segment-import.sh <region> <file.osm.pbf>` running `extract`, `stage` and `publish` through Gradle, stopping on the first failure; add the importer settings to `docker/.env.example`; verify `bash -n` passes and the script prints usage without arguments
- [ ] 5.2 Document in the local-stack skill: creating `importer.test`, putting its DID in `SEGMENT_IMPORTER_DID` and its credentials in the importer settings, fetching Vermont from Geofabrik, running the script, and what `publish` prints; add the `stage`/`publish` commands to AGENTS.md's command list; verify `scripts/check-agent-sync.sh` passes

## 6. Integration

- [ ] 6.1 Run `./gradlew check` and verify it passes
