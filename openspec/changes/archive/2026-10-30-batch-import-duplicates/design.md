# Design

## Context

See proposal.md for why. The current state:

- `ActivityMatcher.matchSpans` (`core/data`) matches each import against the log first, then against earlier activities in the batch. It takes `MatchSpan(startedAt, elapsed, zoneless)`.
  - The rule is overlap by half the shorter span, plus the zone-shift rule.
  - Both log and batch use a sorted sweep (`Nearby`), so thousands against thousands stays quick.
  - It returns `ActivityMatch(of, hoursApart)`. Nothing records how close the match is, or the sports.
- `BatchSelection.action` (`feature/record` presenter) defaults every duplicate to Combine when `canCombine`, else Ignore. `isSelected` is `action != Ignore`.
  - The single-file path (`singlePreview`) uses the same `BatchSelection`.
- `batchPlan` follows Combine actions through `rootOf` to group copies.
  - An item's chain can end at an earlier item the athlete ignored. That item becomes a root no one saves under, and the items pointing at it combine with each other only.
- `DuplicateInfo.existing` (`ExistingActivityPreview`: title, start, device) is filled only for log matches. An in-batch match carries only `inBatchIndex`.
- A logged `ActivityRecord` has `sport`, `startedAt`, `elapsedTime`, `distance` (m) and `device`. An `ImportedActivity` summary has the same.

## Goals / Non-Goals

**Goals:**
- Exact copies and sports are decided once, in the matcher, and the presenter turns them into defaults.
- No extra reads: exact copies are judged from what the scan and the log search already hold.
- At batch scale, matching stays a single sorted sweep.

**Non-Goals:**
- Comparing file contents, or a logged activity's stored source file. That would mean reading every source from the `self` space.
- Changing which activities count as duplicates: the overlap and zone-shift rules stay as they are.
- Choosing which copy of an exact pair to keep beyond "the first". The athlete can still swap them by hand.

## Decisions

### 1. The matcher reports `exactCopy` and `sameSport`

`MatchSpan` gains three optional fields:
- `sport`
- `distanceMeters`
- `device`

`ActivityMatch` gains `exactCopy: Boolean` and `sameSport: Boolean`. A match is an exact copy when all of these hold:
- `hoursApart == 0`
- the starts are within 1 s
- the elapsed times are within 1 s
- the distances are within 0.5%, or both are null
- the devices are equal, or either is null

A logged record's distance is a whole number of metres, and an import's is a `Double`, so 0.5% absorbs rounding.

The alternative was a content hash of each file. It only works within a batch, since a logged activity's source isn't loaded, and it misses a copy that Strava re-encoded. The summary signature catches both cases.

### 2. Candidates prefer an exact copy

Among the log candidates, an exact copy wins over the first overlap. The same holds among earlier batch candidates. The log still wins over the batch.

This matters when an Edge recording precedes two identical watch copies. Without the preference, the second watch copy matches the Edge and combines, instead of being recognised as a copy of the first watch file.

### 3. Sport for matching is the type an item saves as by default

A batch item's `MatchSpan.sport` is its export type's sport when the export gives one, else its file's sport. A logged activity uses `record.sport`. Only the sport is compared; sub-sports are ignored, so mountain and road rides count as the same.

### 4. Defaults live in `BatchSelection`, which knows whether it's a batch

`BatchSelection` gains `batch: Boolean`, false on the single-file path. Its default for a match is:
- **An exact copy:** Ignore.
- **Otherwise, in a batch with `!sameSport`:** Import anyway.
- **Otherwise:** the current rule (Combine when it can, else Ignore).

`toggled` re-selects an exact copy as Import anyway, and the existing `selectedAction` is used for the rest.

Keeping single-file behaviour means `feed/import-duplicates`'s existing scenarios (a run combining with a walk) stay true.

### 5. A chain skips ignored copies

In `rootOf`, when the activity an item would combine into is ignored, the chain continues through that item's own match. An ignored exact copy then stands for what it copies, and nothing combines under an activity that isn't saved.

### 6. Bulk action is one event over the shown duplicates

`ImportActivityEvent.ShownDuplicatesAction(action)` sets that action for every row `ImportBatch.shown` holds that has a duplicate. A Combine that `canCombine` refuses becomes Import anyway. The control shows only on the Duplicates filter, as three buttons below Select/Deselect all. It isn't a segmented control: the shown duplicates can each be set differently, so no one choice is "selected".

### 7. The matched activity is one preview type for both kinds

`ExistingActivityPreview` gains:
- `type`
- `elapsed`
- `distanceMeters`

The presenter fills it for an in-batch match too, from that item's summary and default type. `ImportItemPreview` gains `matched: ExistingActivityPreview?`. The sheet shows it in a two-column comparison under the stats, labelled "Already in your log" or "Earlier in this import", plus "Exact copy" when it is one. `DuplicateInfo` gains `exactCopy`, which the row label and the summary count use.

## Risks / Trade-offs

- **[Risk] Two genuinely different activities start in the same second, with the same length and distance.** For example, a treadmill session logged manually with round numbers. → It would default to Ignore. The row says "exact copy", and the athlete can tick it back.
- **[Risk] Strava trims or re-times an upload.** For example, a cropped activity. → It's then not an exact copy: it defaults to Combine with the original, which is the safe default.
- **[Trade-off] Cross-sport defaults differ between a single file and a batch.** Accepted. A single import shows its one duplicate prominently, while a batch could merge a day silently.
