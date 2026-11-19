# Tasks

## 1. Table and pin rules

- [x] 1.1 Add the next free Flyway migration `V<n>__segment_pins.sql` with `segment_pins` as in design.md decision 2 (portable SQL, `BIGINT` times, PK `(segment_uri, kind)`, index on `duplicate_of`), and the Exposed table `SegmentPins` in `db/`; verify `MigrationVersionsTest` passes and the migration applies on H2 (`./gradlew :server-appview:test --tests "*MigrationVersionsTest"`)
- [x] 1.2 Add `segments/SegmentPins.kt` with `statusPinOf(uri)` (a duplicate pin only while its target is indexed with the same sport group and kind) and `namePinInEffect(uri)` (free text, or a proposal whose URI and proposer carry neither `!hide` nor `!takedown`, checked against labels by URI and DID); verify with unit tests in a new `SegmentPinsTest` covering an applying and a dormant duplicate pin and a labelled pinned proposal

## 2. Pins win at every writer

- [x] 2.1 In `upsertSegment`, on a change, store the derived status in the status pin's `prior_status` and write the pinned status and target when a pin applies (efforts still recomputed, segment queued only if the pinned status is live); then re-apply pins naming this URI as their duplicate target; verify in `SegmentPinsTest` that a pinned-active segment re-lined onto an earlier one stays `active` with recomputed efforts, and that a pinned segment deleted and replayed comes back pinned
- [x] 2.2 In `recheckDuplicatesOf`, skip rows with an applying status pin; verify that a segment pinned `duplicate` of B stays so when B is pinned `archived`, and is derived again when B is deleted
- [x] 2.3 Guard `promote`, the archive step of `updateStatuses` and `activateImporterSegments` with "no status pin"; verify that a pinned-active segment older than 180 days with one rider isn't archived and a pinned-archived segment with 3 riders isn't promoted (`SegmentPinsTest`, driving `updateStatuses()`)
- [x] 2.4 In `recomputeCurrentName`, use the name pin while it's in effect, and make `recomputeNamesLabelled` also recompute segments whose name pin names a labelled proposal or proposer; verify that a pinned name beats an adopted one, survives a record rename, and falls back when its proposal is labelled `!takedown` and returns when the label is negated
- [x] 2.5 Make `mayPropose` false and the naming pass adopt nothing on a segment with a name pin (reject and expire still run); verify in `SegmentNamesTest` that a qualifying proposal isn't adopted while pinned, `canProposeName` is false, and voting is still allowed

## 3. Service

- [x] 3.1 Implement `SegmentPins` service `pinStatus`, `unpinStatus`, `pinName`, `unpinName`, `pinsOf`, `orphanedPins` returning `PinResult`, each in one transaction under the indexer's per-URI lock (segment and target, in URI order), applying the transitions in design.md decision 4; expose the lock from `Indexer` for it and wire the service in the AppView's composition root; verify every refusal in the spec's "Only valid pins are accepted" scenarios returns `Invalid` or `NotFound` and changes nothing
- [x] 3.2 Verify pin transitions end to end over `testApplication` (`SegmentPinsTest`): pinning `active` lists a proposed segment in a stranger's `searchSegments` and gives it a leaderboard; pinning `archived` empties the leaderboard and Mayors and hides it from search while its activity keeps the effort; pinning `duplicate` removes efforts and re-checks its own duplicates; unpinning an archived formerly-active segment restores its board, and unpinning an active pin on an old unridden segment leaves it `proposed` for the next status pass to archive
- [x] 3.3 Verify lifts (`AlpineSegmentsTest` or `SegmentPinsTest`): a duplicate lift pinned `active` is matched and listed with no leaderboard, rank or naming; a lift can't be pinned `duplicate` of a run; a lift's pinned name is served
- [x] 3.4 Verify that a pinned segment's handoff successor has no pins, and that `orphanedPins` lists pins on a deleted segment (`SegmentHandoffsTest` or `SegmentPinsTest`)

## 4. Docs and checks

- [x] 4.1 Update `.agents/rules/appview.md` (segments and names bullets: status and current name are derived unless pinned, `statusPinOf` is the rule for any new writer of `segments.status`, pins outlive their records) and the `CLAUDE.md` architecture paragraph where statuses and names are described; verify `scripts/check-agent-sync.sh` passes
- [x] 4.2 Run `./gradlew :server-appview:test` and `./gradlew check` through the gradle-runner agent and verify both pass
