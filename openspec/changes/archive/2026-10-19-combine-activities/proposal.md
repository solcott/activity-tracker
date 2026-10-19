# Proposal

## Why

An athlete often has more than one recording of what was really one activity:
- a watch and a bike computer on the same ride;
- a watch that died partway, so they finished on their phone;
- MTB runs with a shuttle between them;
- a ski day saved before lunch and started again after.

Today the only options are an import-time Merge, which fills gaps and loses the other recording, and Group (`recordingOf`), which shows one feed card but leaves the activity's figures and charts as the primary's alone. Group is the Strava model, a stack of separate activities, and it will double-count once totals, segments and Local Legends exist. This change adds one real combined activity, built from its members, which can still open each member on its own.

This is the first of four changes:
1. `combine-activities` (this one): the model, the AppView, building the combined activity, and combining by hand from the activity page.
2. `combined-stream-sources`: choosing which member each channel comes from.
3. `combine-on-import`: Combine replaces Merge and Group at import, for every sport, and existing groups move to the new model.
4. `combine-suggestions`: detecting recordings that belong together and asking.

## What Changes

**The combined activity**
- The athlete's combined activity is a new `feed.activity` record. Its new `combined` object lists its members (2 to 10 of the athlete's own activities) and, for each channel, the members ranked as its sources.
  - This change ranks the sources itself. `combined-stream-sources` lets the athlete re-rank them.
  - Members are not rewritten: each keeps its record, streams, track, original file and devices.
- The client builds the combined activity from its members. For each channel, at each moment, it uses the highest-ranked member that has a sample then.
  - **Recordings at the same time:** the combined activity is one recording with the best channels.
  - **Recordings back to back:** they join end to end. Distance carries on from the total so far.
- Summary figures are worked out again from the combined data:
  - Elapsed time runs from the first start to the last end, gaps included.
  - Moving time is the members' moving time added up.
- Back-to-back members show as legs on the combined activity's charts. The new `polylineGaps` field marks where its map line breaks, so a shuttle or a lunch break isn't drawn as a straight line.
- Title, description, photos, sport and visibility start from the member combined into (the one whose page the athlete started from), and the athlete edits them on the combined activity.

**Feeds and the AppView**
- Feeds show the combined activity in place of its members.
- The combined activity's page lists each member, and each member opens on its own page. A member's page links back to the combined activity.
- Members have the combined activity's visibility. Combining moves each member to it, and changing the combined activity's visibility moves its members with it.
- Likes and comments on a member count as the combined activity's, for viewers who may see it.

**Changing a combined activity**
- On their own activity's page, the athlete can "Combine with…" one of their other activities from the same day, or add one to an activity that's already combined.
- They can also remove a member, split the combined activity back into its members, or delete it with or without its members.
- A combined activity can't be a member, and an activity belongs to at most one combined activity.

## Capabilities

### New Capabilities
- `feed/combined-activities`: covers several things together:
  - one combined activity built from the athlete's recordings of the same activity, recorded at the same time or one after another;
  - how its data and figures come from its members;
  - how feeds, visibility, likes and comments treat it and its members;
  - combining, splitting and deleting by hand.

### Modified Capabilities
<!-- None. Merge and Group keep their requirements until combine-on-import replaces them. -->

## Impact

- **Lexicons:**
  - `feed/activity.json` gains `combined` (members, ranked sources) and `polylineGaps`.
  - `feed/defs.json` `activityView` gains `members` (a new `#memberView`) and `combinedInto`.
- **core/lexicon:** the `ActivityRecord` and `ActivityView` mirrors, a `CombinedActivity` model, and `DetailCategory` for the new fields.
- **core/data:**
  - a new composer that builds the combined record, streams, track and details from members' data, reusing `ActivityMerge`'s offset and resampling helpers;
  - `ActivityManager` gains combine, remove-member, split and delete-with-members; `setVisibility` and `edit` cover members;
  - the feed cache drops members once they're combined.
- **server/appview:**
  - a Flyway migration adds an `activity_members` table;
  - `ActivityIndex` indexes members from the combined record, as untrusted input;
  - `Views.kt` hides members from feeds, serves `members` and `combinedInto`, and counts members' likes and comments;
  - `getComments` and `getLikes` include members'.
- **Screens:**
  - `ActivityDetailScreen` and its presenter gain combine, split and remove-member events;
  - a new picker screen lists the athlete's other activities from that day;
  - `composeApp` gets the members list (alongside `RecordingsUi`), the picker UI and the "Part of…" link.
- **Unchanged:** today's import Merge and Group keep working until `combine-on-import`.
