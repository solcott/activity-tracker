# Proposal

## Why

Batch import finds duplicates by overlap alone, whatever their sport, and every duplicate defaults to Combine. That works for one file. At account-export scale it doesn't:

- An athlete's Strava and Garmin Connect exports largely hold the same uploads. Importing both, or importing an export after earlier single imports, yields about a thousand pairs that are the very same recording. Each defaults to Combine, which adds a combined activity with nothing to combine.
- A walk recorded during a ski day overlaps it, so the two chain into one combined activity unless the athlete notices.
- Changing what happens to a thousand duplicates takes a thousand taps.

## What Changes

- **Exact copies:** a duplicate that is the same recording is flagged as an exact copy and defaults to Ignore. "Same recording" means:
  - the same start to within a second,
  - the same length,
  - the same distance,
  - and the same device when both name one.

  Within a batch, the first copy is kept. Against the log, the logged activity is kept.
- **Cross-sport overlaps:** an overlap between different sports is still flagged. In a batch, though, it defaults to Import anyway instead of Combine. A chain of default choices therefore never merges activities of different sports.
- **Bulk duplicate actions:** with the list filtered to duplicates, the athlete can set Ignore, Import anyway or Combine for every shown duplicate at once. Combine falls back to Import anyway for a duplicate that can't be combined yet.
- **Compare in the preview:** a duplicate's preview shows what it matched, either the logged activity or the earlier copy in the batch. It shows that match's sport, start, length, distance and device next to the duplicate's own, and marks an exact copy.
- **Single-file import is unchanged:** a single import still defaults to Combine for any overlap, as `feed/import-duplicates` says today. An exact copy of a logged activity is flagged and defaults to Ignore there too.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/import-duplicates`:
  - It recognises exact copies and their Ignore default.
  - The default action depends on whether the duplicate is an exact copy and, in a batch, on whether the sports match.
- `feed/batch-import`:
  - The duplicates filter gains bulk actions.
  - The item preview shows the matched activity beside the duplicate.

## Impact

- **`core/data` `ActivityMatcher`:**
  - A match records whether it's an exact copy and whether the two share a sport.
  - Matching rules otherwise stay the same: overlap by half, the zone-shift rule, any sport flagged.
- **`feed/record`:**
  - Contract (`ImportActivityScreen.kt`): `DuplicateInfo` gains the exact-copy flag. `ExistingActivityPreview` gains sport, length and distance. Batch summary counts are added, with a new bulk-action event.
  - Presenter: `BatchSelection` defaults follow the new rules, and the bulk-action event is handled.
  - UI: the row label, a bulk-action control on the Duplicates filter, and a comparison block in the item sheet.
- **Other:** strings, `:app-fixtures` samples and screenshot references.
- No lexicon, AppView or outbox changes.
