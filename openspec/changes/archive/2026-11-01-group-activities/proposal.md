# Proposal

## Why

Athletes who ride, run, hike, ski or paddle together today post separate activities with nothing linking them. On Strava they'd show as one group outing, with each athlete's page showing who they were with. That is one of the most social parts of an activity feed, and it's missing. Grouping needs the AppView to compare athletes' routes and times, which this app only does with consent. So it has to be opt-in, and an athlete must be able to take any single activity out of its group.

## What Changes

- **Opt-in.** A new "Group activities" setting has three choices:
  - **Off**, the default and the same as never having chosen.
  - **Connected**: group me only with athletes I follow or who follow me.
  - **Anyone**: group me with any athlete who has also opted in.

  It is stored in a new `actor.groupPrefs` record in the athlete's `self` space, never in the repo. The app asks once and Settings can change it.
- **Pairing.** The AppView pairs two athletes' activities when all of these hold:
  - both athletes allow it;
  - an effective follow exists either way, if either athlete chose Connected;
  - neither has blocked the other;
  - the activities are in the same sport group (`SegmentSport`), so a Ride and an MTB ride pair, but a ride and a run don't;
  - they overlap in time for most of the shorter one, and their routes stay close for most of that overlap.

  Each activity lists only its own direct partners. Groups are not chained.
- **Shared track.** Matching uses the existing privacy-zone-trimmed `feed.segmentTrack` in the `self` space. Clients now write it while either segment matching or grouping is on, and the AppView uses it only for the purposes the athlete consented to.
- **Leaving a group.** An athlete can leave the group on any one of their activities, and later rejoin. The choice is a `groupExcluded` mark on that activity's segment track. Leaving removes the activity from every partner's page and stops it pairing again.
- **Who sees a partner.** `feed.defs#activityView` gains `group`: the activity's partners that this viewer may see. A partner shows only when all of these hold:
  - the viewer may see the partner's activity at the narrowest of its visibility and its Start time and Map categories;
  - neither the viewer nor the partner has blocked the other;
  - the viewer hasn't muted the partner.
- **Activity page.** The page shows a "Together with" section, plus Leave group / Rejoin group on the owner's own page.
- **Feed card.** The card shows a short "with Bob and 2 others" line.
- **Drops.** The AppView drops pairs when grouping goes off, the activity leaves the group, its track is deleted, the `self` space is deleted or the account is deleted.

## Capabilities

### New Capabilities

- `feed/group-activities`: the opt-in levels and how they're asked and saved, the pairing rule, the time-and-route match, leaving and rejoining, which viewers see which partners, the activity page section and feed card line, and what the AppView drops and when.

### Modified Capabilities

- `segments/segment-track`: segment tracks are written while segment matching **or** grouping is on, and deleted only when both are off. The AppView keeps and uses track data for each purpose only while that purpose's consent is on, so turning segment matching off drops efforts and candidate riders but keeps tracks used for grouping. A segment track also carries the activity's group exclusion, kept across rewrites like the leaderboard exclusion.

## Impact

- **Lexicons:** new `actor.groupPrefs` (`self` space). `feed.segmentTrack` gains `groupExcluded`. `feed.defs#activityView` gains `group` (a new `#groupPartner` view). `space/athlete.json` lists `actor.groupPrefs`. All of these are mirrored in `core/lexicon` and `Nsid`.
- **Re-sign-in:** athletes must sign in again to pick up the new `space:` scope before the setting is offered, the same as segment matching.
- **AppView:**
  - A Flyway migration adds `group_prefs`, `activity_group_pairs` and `segment_tracks.group_excluded`.
  - Self-space indexing of `actor.groupPrefs`.
  - Usable-track queries split per purpose.
  - A `group` job kind in the segment matcher, plus a pure time-and-route match function.
  - Drops in `SegmentIndex`.
  - Batched `group` hydration in `Views.hydrate`.
- **Client:**
  - `SegmentTracks` and `SegmentTrackSweeper` follow either consent.
  - A new `GroupPrefsRepository`.
  - Leave and rejoin rewrite the segment track.
  - A Settings choice and a one-time ask.
  - The detail page's Together-with section and leave action.
  - The feed card line.
  - Fixtures, UI tests and screenshot references.
- **Out of scope:**
  - Tagging athletes by hand on indoor or GPS-less activities.
  - Lift-served skiing and other types `SegmentSport` doesn't group.
  - "Never group me with this athlete", short of a block.
  - Notifications when someone is grouped with you.
  - Group-level likes or comments.
