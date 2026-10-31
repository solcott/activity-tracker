# Tasks

Each group lands its own tests. Test fixtures are synthetic (`FitFixtures`, `BatchFixtures`, generated GPX), never real activity files.

## 1. Matcher in `core/data`

- [x] 1.1 Add `sport`, `distanceMeters` and `device` to `MatchSpan` (optional), and fill them in `ImportedActivity.matchSpan()`.
  - Verify: `:core-data:jvmTest` compiles, and existing `ActivityMatcherTest`s pass unchanged.
- [x] 1.2 Add `exactCopy` and `sameSport` to `ActivityMatch`, judged as in design §1 for both log and batch matches. A zone-shifted match is never an exact copy.
  - Verify: `ActivityMatcherTest` cases:
    - same start, length and distance on one device → exact
    - a second off → exact
    - two seconds off → not exact
    - distance 1% off → not exact
    - different devices → not exact
    - one with no device → exact
    - ride vs walk overlap → `sameSport` false
    - mountain vs road ride → `sameSport` true
- [x] 1.3 Prefer an exact copy among each import's log candidates, and again among its earlier batch candidates (design §2).
  - Verify: an `ActivityMatcherTest` with an Edge ride at 8:58 and two identical watch copies at 9:00 matches the second copy as an exact copy of the first.

## 2. Defaults and planning in `feature/record` presenter

- [x] 2.1 Give `BatchSelection` `batch: Boolean` and the default rule of design §4: exact copy → Ignore; cross-sport in a batch → Import anyway; else as today.
  - Set `batch` true from the batch path and false from `singlePreview`.
  - `toggled` re-ticks an exact copy as Import anyway.
  - Verify: `BatchSelectionTest` covers each default and the toggle, and `ImportCombinePresenterTest`'s run-and-walk case still combines.
- [x] 2.2 Make `rootOf` continue through an ignored item to that item's own match (design §5).
  - Verify: a `BatchSelectionTest` plan where C combines into B, B is an ignored exact copy of A, and A is selected groups A with C.
- [x] 2.3 Build each batch item's `MatchSpan` with its default type's sport (export type, else file type), its distance and its device.
  - Verify: an `ImportBatchPresenterTest` where a Strava export and a Garmin export both hold the same GPX ride shows the second as an exact copy with Ignore, and saves one ride.
- [x] 2.4 Handle `ImportActivityEvent.ShownDuplicatesAction(action)`: set the action for every shown duplicate, using Import anyway where Combine can't apply.
  - Verify: an `ImportBatchPresenterTest` filters to duplicates, sends Ignore, and saves only the new activities. `BatchSelectionTest` covers Combine with a logged match still uploading (no CID), which falls back to Import anyway; the presenter fixture can't stage an unsent logged activity.
- [x] 2.5 Fill `ExistingActivityPreview` (now with `type`, `elapsed`, `distanceMeters`) for both log and in-batch matches. Set `DuplicateInfo.exactCopy`, and `ImportItemPreview.matched` for an opened duplicate.
  - Verify: an `ImportBatchPresenterTest` opens an in-batch duplicate and finds the earlier copy's type, start, length, distance and device in `preview.matched`.

## 3. Contracts, UI and samples

- [x] 3.1 Extend the `feed/record` API:
  - `DuplicateInfo.exactCopy`
  - the new `ExistingActivityPreview` fields
  - `ImportItemPreview.matched`
  - an `ImportBatch.exactCopies` count
  - the `ShownDuplicatesAction` event
  - Verify: `./gradlew :feature-record-api:composeStabilityCheck` passes (every field stable).
- [x] 3.2 Add the UI pieces:
  - Label exact copies on rows ("Exact copy of …").
  - Count them apart in the batch summary.
  - Add the bulk-action buttons on the Duplicates filter.
  - Add the comparison block in the item sheet ("Already in your log" or "Earlier in this import", plus "Exact copy").
  - Add every string to `strings.xml`, with plurals for counts.
  - Verify: `ImportBatchUiTest` finds the exact-copy label and count, sends `ShownDuplicatesAction` from the control, and finds the comparison's fields in the preview sample.
- [x] 3.3 Update `:app-fixtures` record samples:
  - an exact copy in the duplicates batch
  - a preview sample with `matched`
  - Regenerate the import screenshot references and look at them.
  - Verify: `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes.

## 4. Docs and checks

- [x] 4.1 Update `.agents/rules/activity-files.md`'s Duplicates paragraph: exact copies, the sport rule for batches, and the chain through ignored copies.
  - Verify: `scripts/check-agent-sync.sh` passes.
- [x] 4.2 Import both real exports on the desktop app against the local stack, and cancel before saving.
  - Check the exact-copy count is close to the overlap between them, ski-day walks default to Import anyway, and Ignore-all on the Duplicates filter leaves only the new activities selected.
  - Verify: the counts and defaults seen, reported without any activity details.
  - Seen (October 2026):
    - 2,358 activities.
    - 1,085 duplicates within the import, 996 of them exact copies (Ignore by default), so 1,362 selected.
    - Ignore on the Duplicates filter left 1,273 selected: exactly the new activities.
    - Nothing saved.
    - Cross-sport defaults are covered by tests and weren't picked out by eye among 2,358 rows.
- [x] 4.3 Run `./gradlew check`.
  - Verify: it passes.

## Workflow follow-up

- Archive with `/opsx:archive batch-import-duplicates` once applied.
