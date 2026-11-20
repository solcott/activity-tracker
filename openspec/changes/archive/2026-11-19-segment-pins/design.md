# Design

## Context

Status and the current name are stored in columns on `segments` (`status`, `duplicate_of`, `current_name`), and every reader keys off those columns. Readers include `served`/`listedTo` (`api/Place.kt`), search, `rankable()`/`rankedSegment()` and `getActivitySegments`' rank (`api/SegmentViews.kt`), the matcher's `LIVE_STATUSES` checks (`SegmentMatcher.kt`, `index/SegmentGroups.kt`), `nameable`/`mayPropose` (`SegmentNames.kt`), handoff staging (`index/Handoffs.kt`), and every view that serves `current_name`. A few places write those columns:

| Writer | Where | Sets |
|---|---|---|
| `upsertSegment` | `index/SegmentRecordIndex.kt` | `status`, `duplicate_of` on a new or changed line/sport/kind; `current_name` = record name, then `recomputeCurrentName` |
| `recheckDuplicatesOf` | same | `status`, `duplicate_of` of each duplicate of a deleted/archived/changed original |
| `updateStatuses` → `promote`, archive | `segments/SegmentMatcher.kt` | `proposed` → `active`; `proposed` → `archived` (+ recheck) |
| `activateImporterSegments` | same | importer `proposed` → `active` |
| `recomputeCurrentName` | `segments/SegmentNames.kt` | `current_name` |

`Indexer` serializes writes per AT-URI with a striped lock (`lockedOn`). The matcher's passes run in their own transactions. The `admin-site` change, written in parallel, adds `APPVIEW_ADMINS` roles and an `admin_actions` audit log. Its operator actions are the callers here.

## Goals / Non-Goals

**Goals:**
- Pins take effect everywhere status or name matters, with no change to any reader.
- Derivation and pins can't fight. No pass undoes a pin, and a pin doesn't leave derived state inconsistent (efforts, duplicates' re-checks, groups, elevation).
- Replays and record edits can't drop a pin.

**Non-Goals:**
- Routes, pages, roles and the audit log belong to `admin-site` and `admin-segments`.
- Pins on dive sites. `dive_sites` has the same shape, and the table and service can grow a kind later.
- Telling viewers a value is pinned. That would need a lexicon change.
- Re-checking later-indexed live segments when an unpinned segment comes back live (see Risks).

## Decisions

### 1. Pins write the effective value into the existing columns

`segments.status`, `duplicate_of` and `current_name` keep meaning "what is served". Each writer in the table above computes its derived value as today, then lets the pin win. Readers stay untouched.

*Alternative:* keep the columns derived and make readers use `coalesce(pin, derived)` through a join. That touches some 15 query sites in five files, and missing one silently breaks a rule. The column approach changes five writers, each easy to test.

### 2. Table: one row per segment and pin kind

```
segment_pins(
  segment_uri   TEXT    NOT NULL,
  kind          TEXT    NOT NULL,   -- 'status' | 'name'
  status        TEXT,               -- active | archived | duplicate (kind = status)
  duplicate_of  TEXT,               -- target URI when status = duplicate
  name          TEXT,               -- pinned name (kind = name), trimmed, ≤ 100 graphemes
  proposal_uri  TEXT,               -- the proposal the name came from, if any
  proposer_did  TEXT,               -- its proposer, for the label check
  prior_status  TEXT,               -- the derived status at pin time, or as re-derived by a change while pinned
  pinned_by     TEXT    NOT NULL,   -- operator DID
  pinned_at     BIGINT  NOT NULL,
  note          TEXT,               -- ≤ 500 chars
  PRIMARY KEY (segment_uri, kind)
)
index on duplicate_of
```

Portable SQL, epoch-millis `BIGINT`, next free `V<n>` at implementation. `admin-site` also adds a migration, so take the number when the branch is cut. The note and operator DID are operator data, not athletes' private data, so nothing joins `Indexer.HELD_ROWS`.

*Alternative:* nullable pin columns on `segments`. Rejected because `segments` rows are deleted with the record, and pins must outlive that (decision 5).

### 3. A pure "apply pins" step at each writer

`SegmentPins.kt` has `JdbcTransaction.statusPinOf(uri): StatusPin?`. It returns the pin only while it *applies*: a `duplicate` pin's target is indexed with the same sport and kind (decision 6). Each writer uses it as follows.
- `upsertSegment`: when `changed`, compute `newStatus`/`newDuplicateOf` as now and store `newStatus` in the pin's `prior_status`. If a pin applies, write the pinned status and target instead. Efforts are still deleted and the segment re-queued if the pinned status is live. Then call `applyPinsNaming(uri)` (decision 6).
- `recheckDuplicatesOf`: select duplicates `where duplicate_of in originals and no applying status pin`. A pinned duplicate keeps its pin's target.
- `promote` / archive in `updateStatuses`, `activateImporterSegments`: add `and not exists (status pin on uri)`. A pin never holds `proposed`, so the importer backfill would skip pinned rows anyway. The guard keeps that explicit.
- `recomputeCurrentName`: first check the name pin. If it is in effect (free text, or a proposal URI and proposer DID that pass the same `!hide`/`!takedown` checks as `unlabelledProposal()`, read from `LabelStore` by URI and DID so they work after the proposal row is gone), use its name. Otherwise fall back to the adopted proposal, then the record name. `recomputeNamesLabelled` also recomputes segments whose name pin names a labelled proposal or proposer.
- `SegmentNaming.updateSegment`: no adoption when a name pin exists. Reject and expire still run. `mayPropose`: false when a name pin exists. `canProposeName` follows, since it calls `mayPropose`.

### 4. Pinning and unpinning re-use the derived side effects

Service `SegmentPins(db, jobs, clock, importer, indexer lock)`:
- `pinStatus(segment, status, duplicateOf?, by, note)`: validate (spec "Only valid pins are accepted"), record `prior_status` = current derived status, write the row, then move the segment from its old effective status to the new one:
  - becomes live from not-live (`active` from archived/duplicate): `jobs.enqueueSegment` so past tracks are matched.
  - becomes `duplicate`: `deleteEfforts { segmentUri eq uri }`, which also recomputes elevation, then `recheckDuplicatesOf([uri])`, `rederiveGroups([uri])`.
  - becomes `archived` from live: `recheckDuplicatesOf([uri])`, `rederiveGroups([uri])`. Efforts are kept.
  - `active` from `proposed`: status only. Both are live, so matching and segment groups are unchanged, as with the status pass's promotion.
- `unpinStatus(segment, by)`: delete the row and derive again as `recheckDuplicatesOf` does for one row: run `duplicatesOf(sport, kind, line, before = first indexed)`. If none, take `active` when `prior_status = active` (kept current by decision 3's re-line rule), else `liveStatus`, then `promote([uri])`. Apply the same transition side effects.
- `pinName(segment, name | proposalUri, by, note)` / `unpinName`: write or delete the row, then `recomputeCurrentName`.
- `pinsOf(segment)`, `orphanedPins(cursor)`: reads for the admin page.

Each call runs in one `db.tx` under the indexer's per-URI lock for the segment, plus the target for a duplicate pin, locked in URI order. This stops a concurrent Jetstream upsert from interleaving. The matcher's hourly pass may still run concurrently. Its guarded updates (`and not exists pin`) re-read the row, so the worst case is a pin that commits just after a pass, which is fine. Results are a sealed `PinResult` (`Pinned`, `Unpinned`, `NoPin`, `NotFound`, `Invalid(reason)`) that `admin-site` maps to page messages and audit outcomes. The service takes the operator `Did` but doesn't check roles. The service lives in `segments/` and doesn't depend on the `admin` package.

*Alternative:* make the status pass apply pins. That leaves up to an hour of stale status, and indexing could flip it back in between.

### 5. Pins outlive their segment's record

Deleting a segment keeps its `segment_pins` rows. `upsertSegment` applies them again when the URI is indexed again. This keeps pins safe through Jetstream cursor rewinds (delete then create replayed) and through an athlete deleting and re-writing a record. A handoff successor has a new URI, so it starts with no pins. The handoff's names are its record's and adopted ones, and a pinned name isn't carried, matching "pins hold on a URI". `orphanedPins` lets the admin page show and clear pins on gone URIs.

### 6. A duplicate pin is dormant while its target is missing or mismatched

`statusPinOf` returns a `duplicate` pin only while its target row exists with the same sport and kind. While it doesn't, the segment is derived normally. The target's deletion runs `recheckDuplicatesOf([target])`, which then treats the pinned row as unpinned and can promote it to live, possibly as the handoff's successor. When the target is indexed again (`upsertSegment` of the target), `applyPinsNaming(target)` re-applies every pin naming it and moves those segments to `duplicate` with the usual side effects. This keeps pins replay-safe without an operator re-pinning.

*Alternative:* delete the pin when its target goes. That is simpler, but a cursor rewind would then silently lose an operator's decision.

### 7. Pinning `active` and lifts

A pin bypasses only `promote` and the 180-day archive. Everything downstream keys off `status = active` as before. Kind-based rules (`SegmentKind.isRanked` in `rankedSegment`, `nameable`) still exclude lifts, so a pinned-active lift is matched and listed but never ranked or named. The service refuses a duplicate pin across kinds, so lift pins only target lifts. Elevation's switch to effort values still uses `SEGMENT_ACTIVE_ATHLETES` distinct athletes. That is independent of status, so a pinned-active segment with one rider keeps terrain elevation.

## Risks / Trade-offs

- [An unpinned segment comes back live while a later-indexed segment that took its place (promoted when it was archived) is also live, so two overlapping active segments exist] → Accepted. Demoting the later one would delete efforts athletes now see. The admin page can pin either `duplicate`. Unpin's result says when the segment's line duplicates a later live segment, using the same `duplicatesOf` test without `before`, so the operator sees it.
- [Pinning `duplicate` deletes efforts that unpinning can only partly restore. Tracks whose athletes have since turned matching off are gone] → The admin page warns before a duplicate pin. Archive is the non-destructive choice.
- [An operator pins `active` on spam to promote it] → Operators are trusted by role. Every call is in the audit log, and labels still take the segment down (`served` checks `!takedown` first).
- [A writer added later sets `segments.status` without consulting pins] → `SegmentPinsTest` covers each writer. The appview rule doc names `statusPinOf` as the rule for any new status writer.
- [Free-text pinned names aren't labelable records, so moderation can't hide them through Ozone] → Only operators write them. A `!takedown` on the segment itself still hides the whole segment.

## Migration Plan

One additive Flyway migration (new table, no backfill). Rollback: unpin all, or drop the code. Pinned values stay in the columns until something derives them again. The naming pass resets names within the hour. Pinned statuses keep their value until the segment is changed or re-checked, since nothing demotes an `active` and the status pass touches only `proposed`. So unpin everything through the service before rolling back. The table can stay.

## Open Questions

- Whether `admin-segments` wants pins listed with each segment's current derived status ("what it would be if unpinned"). The service can compute it from `prior_status` and `duplicatesOf` without schema change, so it is deferrable.
