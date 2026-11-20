# Proposal

## Why

A segment's status (`segments/segment-records`) and its current name (`segments/segment-names`) are derived and nothing else can change them. The derivation is sometimes wrong in ways only a person can see: two segments on the same climb that the 30 m test doesn't catch, a junk segment that will sit `proposed` for 180 days, a real trail that will never reach three riders, an adopted name that is accurate but unwanted, or an importer name that is wrong. Operators need to settle these by hand, durably. The planned admin site (`admin-site`, with its later `admin-segments` page) needs something to call that does this. Today the only fix is a Flyway migration that the next derivation pass undoes.

## What Changes

- A new AppView table, `segment_pins`, holds operator pins on segments. Each pin records the segment, what is pinned, who pinned it (the operator's DID), when, and an optional note. A segment can have a status pin, a name pin, both or neither.
- **Status pins**: `active`, `archived`, or `duplicate` of a named segment of the same sport group and kind. A pinned status beats the derived one wherever status matters. The status pass doesn't promote or archive the segment, indexing and duplicate re-checks don't change it, and every view, search, matching, naming, Mayors and leaderboard rule that keys off status reads the pinned value. Nothing new is served: views show the status as usual and never say it is pinned.
- **Pinning `active`** skips the distinct-athletes threshold and the 180-day archive. The segment is matched, searchable and, for a `run`, ranked and nameable at once. On a lift it means matched and listed, never ranked or named (`segments/segment-lifts`).
- **Pinning `archived`** keeps the segment's efforts but adds no new ones. They disappear from leaderboards and Mayors while the pin holds and come back on unpin. **Pinning `duplicate`** deletes its efforts, as a derived duplicate loses them. Unpinning matches past tracks again. Either one re-checks the segments that duplicated it, as an archive does today.
- **Name pins**: free text, or the name of one of the segment's proposals. A pinned name beats adopted names in every view and search. While a name is pinned, the naming pass adopts nothing on that segment and new proposals aren't accepted. A label (`!hide`, `!takedown`) on the pinned proposal or its proposer still wins: the pin stops applying and the name falls back as it does today.
- Pins hold until an operator unpins them, through record edits, re-lines and Jetstream replays. Unpinning derives the value again. A segment that was `active` before a status pin, and hasn't been re-lined since, comes back `active`.
- A duplicate pin applies only while its target is indexed with the same sport group and kind. Otherwise the segment's status is derived as if unpinned, until the target returns.
- Pin and unpin are an internal Kotlin service in the AppView (`SegmentPins`). The `admin-site` change's operator actions call it and record each call in its audit log. There is no XRPC method, no lexicon change and no client change.

## Capabilities

### New Capabilities
- `segments/segment-pins`: operator pins on a segment's status and name: what can be pinned, how a pin beats the derived value, how long it holds, what unpinning restores, and the internal service that pins and unpins.

### Modified Capabilities
- `segments/segment-records`: a segment's status is derived unless pinned. The status pass, duplicate detection and duplicate re-checks leave a pinned status alone. Deleting a segment keeps its pins for its URI.
- `segments/segment-names`: the current name is a pinned name in effect, else the derived one. Proposals aren't accepted or adopted while a name is pinned.
- `segments/segment-lifts`: a lift's status can be pinned like a run's, with no change to its no-ranking and no-naming rules.
- `segments/segment-leaderboards`: a board exists while the segment is `active`, derived or pinned. An archive or duplicate pin empties it, and an unpin restores it.

## Impact

- **AppView** (`server/appview`): a new Flyway migration for `segment_pins`. New `segments/SegmentPins.kt` holds the service and the pin rules. Pins are applied where status and the current name are written: `upsertSegment` and `recheckDuplicatesOf` (`index/SegmentRecordIndex.kt`), `updateStatuses`/`promote`/`activateImporterSegments` (`SegmentMatcher.kt`), `recomputeCurrentName`, `mayPropose` and the naming pass (`SegmentNames.kt`, `SegmentNaming.kt`). Readers don't change. They already read `segments.status` and `segments.current_name`, which now hold the effective value.
- **`admin-site` / `admin-segments`**: they consume the service. This change adds no routes or pages.
- **Rules docs**: `.agents/rules/appview.md` and the `CLAUDE.md` architecture paragraph mention pins where statuses and names are described.
- No lexicon, client, iOS, importer or migration-of-data change.
