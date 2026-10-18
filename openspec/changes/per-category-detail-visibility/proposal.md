# Proposal

## Why

Today an activity's privacy has two settings: who sees the activity, and who sees its charts. The charts setting only covers the `feed.activityStreams` record. The summary stats it implies (average and max heart rate, power, normalized power, work, calories, speed) and the exact start time sit on the `feed.activity` record, which anyone can read when the activity is Public. The per-lap and per-leg summaries inside the streams blob carry heart rate, power, speed and calories too. So an athlete who sets their charts to "Only me" to keep their heart rate private still publishes their average and max heart rate to everyone. Athletes coming from Strava expect to hide specific details (start time, heart rate, speed, power, calories, the map) per activity, with account-wide defaults. This change gives every kind of data we import its own visibility, applied everywhere that data appears.

## What Changes

- **Detail categories.** Each activity's data is grouped into nine categories: Start time, Heart rate, Power, Pace/Speed, Calories, Map, Physiology, Cadence & running form, and Dive. Every summary field, stream channel and lap/leg field belongs to one category or is core. Core data is always at the activity's visibility: distance, altitude, temperature, gears and e-bike, shifts, jumps, grit and flow, lengths, sets, the lap/leg structure and the dive summary.
- **One visibility per category.** Each category gets Public, Followers, Mutuals or Only me, never wider than the activity. Narrowing the activity narrows any wider categories with it.
- **Split by tier.** The `feed.activity` record keeps only the fields at the activity's own visibility. Fields in narrower categories go into a new `feed.activityDetails` record, one per narrower tier, in that tier's space. Streams are split the same way: one `feed.activityStreams` record per tier, holding that tier's channels and lap/leg fields. All of these records share the activity's rkey.
- **Hidden start time.** When Start time is narrower than the activity, the activity record's `startedAt` is the start of the local day. The exact start sits in the details record for the Start time tier.
- **Hidden map.** When Map is narrower than the activity, the activity record has no `polyline`. The trimmed polyline goes in the details record for the Map tier.
- **AppView merge.** The AppView indexes details records and streams from every tier and gives each viewer the union of the tiers they're admitted to. `activityView` gains the details the viewer may see. `getActivityStreams` returns the merged channels and says which tier each part came from. **BREAKING** (internal API): an activity's streams may now be several records in different places, rather than one record with one visibility.
- **Zones follow heart rate and power.** Zones and training load are placed at the narrower of the Heart rate tier, the Power tier and the training visibility, instead of the charts visibility.
- **Defaults per category.** `actor.activityDefaults` gains a visibility for each category. Settings lets the athlete set them. Recording, manual entry, import (including batch import) and edit let them override the defaults for each activity.
- **Removes charts visibility.** **BREAKING**: the charts picker and the charts default are removed entirely, and `chartsVisibility` is dropped from `actor.activityDefaults`. Categories the athlete has never set start at their default activity visibility. Existing activities are served where their data is stored today until the athlete edits them.

## Capabilities

### New Capabilities
- `feed/detail-visibility`: which category each kind of activity data belongs to, the visibility of each category and its cap at the activity's visibility, how an activity's data is split across tier records, how viewers are served the union of the tiers they're admitted to, and the per-category defaults.

### Modified Capabilities
- `feed/activity-visibility`: the requirements on charts visibility (its cap, where non-public charts are stored, the charts default, the charts picker on new and edited activities) are replaced by per-category detail visibility.
- `feed/activity-zones`: zones and training load are placed by the Heart rate and Power tiers instead of the charts visibility, and move when those tiers change.

## Impact

- **Lexicons:** a new `feed/activityDetails.json`. `actor/activityDefaults.json` gains per-category fields and loses `chartsVisibility`. `feed/defs.json#activityView` gains `details`. `feed/getActivityStreams.json` reports a tier for each part. Descriptions change on `feed/activity.json`, `feed/activityStreams.json` and `feed/activityZones.json`. All of these are mirrored in `core/lexicon` (`Records.kt`, `Nsid`).
- **Client data:** `ActivityDraft`, `ActivityDefaults` (`VisibilityChoice`), `ActivityRepository`, `PdsActivityStreamsStore`, `ActivityZonesStore`, and the place helpers in `Places.kt`. A new splitter turns a draft into its per-tier records, and a merger turns served parts back into one view. Moves become "write the narrower places, then strip the wider ones".
- **AppView:** a new `activity_details` table and indexer. `activity_streams` is keyed by URI and origin, so several tiers can hold one activity's streams at once. `Views.kt` and `ActivityStreamsViews.kt` merge per viewer. A Flyway migration is needed.
- **UI:** a per-category picker in Settings, in the record, manual and import forms (including batch import), and in Edit Activity. The feed card, the activity detail screen and the charts leave out what the viewer isn't given.
- **Known limit:** a hidden start time doesn't hide when the activity was posted. The rkey (a TID) and `createdAt` still show the upload time, as on Strava.
