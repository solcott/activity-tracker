# Proposal

## Why

Athletes don't think to combine recordings, and the cases where they'd want to are easy to spot:
- a ride that ends at 10:40 and another that starts at 10:42 from the same spot;
- MTB runs 20 minutes apart with each one starting higher than the last ended;
- a ski morning and afternoon with lunch between.

`combine-activities` and `combine-on-import` let them combine by hand and at import (for overlapping recordings). This change spots recordings that belong together after any save, including back-to-back ones the import check never flags, and asks.

## What Changes

**Spotting a likely combine**
- After an athlete saves an activity (an app recording, a manual entry or an import), the app looks for another of their activities that it likely belongs with:
  - **Same activity on another device:** their times overlap by at least half the shorter. This covers saves that the import check doesn't, such as an app recording made while a watch also recorded.
  - **Continuation:** the same sport, with one starting no more than 2 hours after the other ended. Dives aren't suggested, because repeat dives are separate dives.
- The wording of a suggestion follows what the athlete's own data shows:
  - **"Switched devices?"** when the second starts within a few minutes of where and when the first ended;
  - **"Shuttle or lift between runs?"** when it starts well above where the first ended;
  - **"Continue after a break?"** otherwise.

**Asking**
- Right after saving, the athlete is asked to combine the activity with the likely match, or to add it to the match's combined activity. Their choices are **Combine** and **Not now**.
- Until they choose, the owner's page of both activities shows a banner with **Combine** and **Don't combine**. Don't combine stops suggesting that pair.
- Suggestions only look at activities that started in the last 7 days, so an old log doesn't fill with banners.
- Activities that are already members of the same combined activity, or that the athlete said not to combine, are never suggested.

## Capabilities

### New Capabilities
- `feed/combine-suggestions`: spotting an athlete's activities that likely belong in one combined activity, recorded at the same time or one after another, and asking them, after saving and on the activities' pages.

### Modified Capabilities
<!-- None. -->

## Impact

- **core/data:**
  - `ActivityMatcher` (from `combine-on-import`) gains `continuations`;
  - a `CombineSuggestions` service: candidates from `getAuthorActivitiesByStart`, the cue from each member's own track or the record polyline and altitude, filtered by membership and dismissals;
  - a Room table of dismissed pairs, with a migration.
- **Screens:**
  - the record-finish, manual-entry and import save flows open the saved activity with a suggestion prompt;
  - `ActivityDetailScreen` State and Events gain the banner;
  - the prompt sheet and the banner are built in `composeApp`.
- **No lexicon or AppView change.** Dismissals stay on the device (see design).
