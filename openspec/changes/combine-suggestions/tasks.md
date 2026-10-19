# Tasks

## 1. Finding suggestions

- [ ] 1.1 Add `ActivityMatcher.continuations` (same sport by the part before `:`, not a dive, a gap of 2 h or less) beside overlap. CommonTests:
  - a ski lunch (11:45 → 13:00) matches;
  - runs ending 12:30 and starting 18:00 don't;
  - a run then a ride 10 minutes later don't;
  - two dives an hour apart don't;
  - `ride:mountain` matches `ride`.

  Verify: `./gradlew :core-data:jvmTest`.
- [ ] 1.2 Add the cue (design D2) from own tracks, falling back to the polyline and altitude. Tests:
  - 2 minutes and 50 m apart gives Switched devices;
  - 400 m higher after 25 minutes gives Shuttle or lift;
  - 1 h 15 min at the same spot gives Break;
  - an overlap gives Same activity on another device;
  - a trimmed polyline with no own track gives Break.

  Verify: `./gradlew :core-data:jvmTest`.
- [ ] 1.3 Add the Room `combine_dismissals` table with its migration and a DAO, storing pairs sorted. Test with the in-memory Room: dismissing (a, b) also hides (b, a). Verify: `./gradlew :core-data:jvmTest`.
- [ ] 1.4 Add `CombineSuggestions.for(activity)` (D1): the query window; filtering by membership, dismissals and the 7 days; the nearest candidate; resolving a member to its `combinedInto`. Tests with `MockEngine`:
  - a last-month ride gives nothing;
  - members of one combined day aren't paired;
  - a candidate that's a member is offered as its combined day;
  - a dismissed pair gives nothing.

  Verify: `./gradlew :core-data:jvmTest`.

## 2. Asking

- [ ] 2.1 Add the suggestion State (cue, the other activity, prompt or banner) and Events (Combine, NotNow, DontCombine) to `ActivityDetailScreen`, with `suggestOnOpen`. Owner only, started within 7 days, Combine through `flights` then reloading as the combined activity. Presenter tests:
  - prompt after a save;
  - a banner after Not now;
  - Don't combine hides it on both activities;
  - a follower sees nothing;
  - Combine adds to an existing combined day.

  Verify: `./gradlew :app-presenters:jvmTest`.
- [ ] 2.2 Open the saved activity with `suggestOnOpen` from the record-finish, manual-entry and single-file import paths, and list suggestions on a batch import's result. Presenter tests for each path. Verify: `./gradlew :app-presenters:jvmTest`.
- [ ] 2.3 Build the prompt sheet and banner in `composeApp` from the `ui/adaptive` wrappers, with the four cue strings. Verify: `./gradlew composeStabilityCheck`, plus desktop screenshots of the prompt and of the banner.

## 3. End to end

- [ ] 3.1 On the local stack:
  - import a morning and an afternoon ski file, then tap Combine on the prompt;
  - import two MTB runs 25 minutes apart, tap Not now, then check the banner on both pages and dismiss it;
  - import a two-dive logbook and check there's no prompt.

  Verify: notes of each check, plus `./gradlew check`.

## Workflow follow-up

- Depends on `combine-activities` and `combine-on-import` (`ActivityMatcher`, combining drafts).
