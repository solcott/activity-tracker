# Tasks

## 1. Matching every sport

- [x] 1.1 Rename `DiveMatcher` to `ActivityMatcher`, drop the sport filter, and allow the whole-hour shift only for drafts whose file has no UTC offset (design D1). Update `DiveMatcherTest` into `ActivityMatcherTest`, adding:
  - an Edge ride overlapping a watch ride;
  - a run that overlaps a ride by under half;
  - a FIT ride two hours off (no match);
  - a UDDF dive two hours off (match).

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 1.2 Turn `rememberOwnDives` into `rememberOwnActivities` without the sport parameter, and resolve a matched member to its `combinedInto`. Update `ImportActivityPresenterTest` and `ImportLogbookPresenterTest`. Verify: `./gradlew :app-presenters:jvmTest`.

## 2. Combining drafts

- [x] 2.1 Add `ActivityManager.combine(into, draft)` and `combine(drafts)` (D2). They queue each draft as its own activity and build the combined activity from the drafts' local data plus the logged member's stored data, refusing an unsent logged member. Tests:
  - an Edge draft combined with a stored watch ride;
  - a third draft added to a combined ride;
  - two logbook drafts combined;
  - an unsent logged member refused.

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 2.2 Port each removed merge requirement's scenario to a combiner test that shows the combined activity has that data:
  - the bike computer adds power;
  - pedals add cycling dynamics;
  - the watch adds lengths, legs and sets;
  - the logbook adds tank pressure, alerts and gas switches;
  - each member keeps its track, source and devices.

  Verify: `./gradlew :core-data:jvmTest`.

## 3. Import screen

- [x] 3.1 Replace `DuplicateAction` with `Ignore | ImportAnyway | Combine` (Combine the default) through `OwnDives`, `DiveBatches` (the group chain becomes a combine chain) and `ImportActivityPresenter`. Disable Combine with a reason while the logged activity is unsent. Presenter tests:
  - the default is Combine;
  - Ignore saves nothing;
  - Import anyway saves on its own;
  - Combine on a single file and on a logbook.

  Verify: `./gradlew :app-presenters:jvmTest`.
- [x] 3.2 Update `DuplicateNotice.kt` and `ImportPreviewUi.kt` for the three actions, removing the merge-gain text and strings, and explaining Combine ("Keeps both recordings as one activity"). Verify: `./gradlew composeStabilityCheck`, plus a desktop screenshot of a flagged ride's notice.

## 4. Add a recording from the activity page

- [x] 4.1 Add `combineInto` to `ImportActivityScreen` (D4): no duplicate search, no visibility pickers, the 12-hour check, logbook entries filtered to the target's window, and saving through `combine(target, draft)`. Presenter tests:
  - the phone GPX after a dead watch is combined;
  - a file three days off is refused with its reason.

  Verify: `./gradlew :app-presenters:jvmTest`.
- [x] 4.2 Add "Add a recording…" to the owner's actions on the activity page, opening the import screen with the target. Verify: a presenter test that the event navigates with `combineInto`, plus a desktop screenshot of the action.

## 5. Removing Merge and Group

- [x] 5.1 Delete these merge-only paths (D3):
  - `ActivityRepository.mergeInto` and `mergeStreams`;
  - `ActivityMerge.fillGaps(record)` and `addsChannels`;
  - `MergeGain` and `rememberLoggedStreams`;
  - `groupTarget` and `mergeTarget`;
  - publishing with `recordingOf`.

  Keep or move the helpers `ActivityCombiner` uses. Delete or port their tests. Verify: `./gradlew check` passes with detekt finding no unused code.
- [x] 5.2 Mark `feed.activity#recordingOf` legacy in its lexicon description and the Kotlin KDoc. Remove "Duplicates beyond dives" from `BACKLOG.md`, and add a line about removing `recordingOf` handling once groups have migrated. Update `.agents/rules/activity-files.md` where it describes merge and group. Verify: `scripts/check-agent-sync.sh`, and `grep -rn "mergeInto\|DuplicateAction.Group" core app composeApp` finds nothing.

## 6. Migrating legacy groups

- [x] 6.1 Add `LegacyGroupMigration` in `SessionScope` (D5): page own activities, combine each primary that isn't a member yet with its recordings, clear each recording's `recordingOf`, and record completion per DID in Room only when every group succeeds. Tests with `MockEngine`:
  - one group becomes a combined activity with both members, and their `recordingOf` is cleared;
  - a run interrupted after the combine finishes the clears on the next run;
  - a failure leaves the flag unset;
  - a second run does nothing.

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 6.2 Start the migration after sign-in from the session graph. Verify: a test that a `SessionGraph` start schedules it once, plus `./gradlew check`.

## 7. End to end

- [x] 7.1 On the local stack:
  - import a watch ride, then the same ride from a second file, and keep Combine;
  - import a two-computer Subsurface logbook from `samples/dive-logs` and keep Combine;
  - add a phone GPX from the activity page;
  - seed a legacy group (publish a dive with `recordingOf` from a test client) and sign in again.

  Check each result in a follower's timeline. Verify: notes of each check, plus `./gradlew check`.

## Workflow follow-up

- Depends on `combine-activities`. Apply after `combined-stream-sources` or alongside it, since they touch different files.
