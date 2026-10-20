# Design

## Context

What the earlier changes leave in place:
- **From `combine-on-import`:** `ActivityMatcher.match` for overlap, and `ActivityManager.combine` for both stored activities and drafts.
- **From `combine-activities`:** `activityView.members` and `combinedInto`.

Saves happen in three places: finishing a recording (`ActivityRecorder` → `ActivityRepository.publish`), manual entry, and import (`ImportActivityPresenter`). Each one then shows the athlete their saved activity. Own tracks are readable from `self` (`ActivityTrackStore`), and the record carries a trimmed `polyline` and the streams carry `altitude`.

## Goals / Non-Goals

**Goals:**
- Suggestions are cheap: one `getAuthorActivitiesByStart` query per save or page open, and only for activities from the last 7 days.
- No false prompts on logbook dives or two-a-day runs.

**Non-Goals:**
- Suggesting another athlete's activities, such as a group ride.
- Combining automatically. Combining always waits for the athlete.
- Syncing dismissals across devices (D3).

## Decisions

### D1. Finding candidates
`CombineSuggestions.for(activity)`:
1. It queries `getAuthorActivitiesByStart(actor = self, since = start − 2 h − 12 h, until = end + 2 h)`.
   - The 12 h covers a candidate's own length, since the query filters by start.
2. Candidates:
   - an overlap of at least half the shorter (`ActivityMatcher.sameAs`) gives **Overlap**;
   - otherwise, the same `sport` (the part before `:`, so `ride:mountain` matches `ride`), not a dive, with a gap of 2 h or less, gives **Continuation**.
3. It removes:
   - pairs within the same combined activity;
   - two combined activities;
   - a member paired with another combined activity's member;
   - dismissed pairs;
   - activities started more than 7 days ago.
4. It returns the nearest candidate in time. For a candidate that's a member, the target becomes its `combinedInto`.

- **Why 2 h:** it covers a ski lunch, shuttles and device swaps, but not a morning and evening double. A slightly longer lunch would be missed.
  - **Rejected: 4 h.** Real two-a-day runs (a 7:00 run, then another at 10:30) would be prompted.

### D2. The cue
For continuations, the cue compares the earlier activity's last position and altitude with the later one's first, each taken from:
- the athlete's own `self` track if there is one;
- otherwise the record polyline's endpoint and the streams' first or last altitude.

The cues:
- **Device switch:** within 5 min and 300 m.
- **Shuttle or lift:** 100 m or more higher at the start.
- **Break:** anything else.

The polyline is trimmed by privacy zones, so a cue near home falls back to Break. That only changes the wording, never whether a suggestion appears.

### D3. Dismissals live on the device
- A Room table `combine_dismissals(did, uri_a, uri_b)`, with the pair stored sorted, plus a Room migration.
- **Rejected: a private record in the `self` space.** That's a new lexicon, a new collection in `space.athlete`, and a re-sign-in for the scope, all so a banner doesn't show twice across devices for at most 7 days.
- **"Not now"** stores nothing: the banner keeps showing.

### D4. Where it's asked
- **After a save:** the screen showing the saved activity (the record-finish and import success paths open `ActivityDetailScreen`) passes `suggestOnOpen = true`. The presenter then shows the suggestion as a prompt sheet (`AppAlertDialog` or a bottom sheet wrapper) rather than a banner.
- **Batch imports:** these ask once, on the import's result list. Each activity with a suggestion is listed and can be combined from there.
- **On a page:** the banner appears in the owner's part of the page.
  - It runs `CombineSuggestions.for` on open, only for an owner's activity started within 7 days.
  - Combine goes through `flights`, then reloads the page as the combined activity.

## Risks / Trade-offs

- [Two runs 90 minutes apart that the athlete wants kept separate] → One Don't combine on either page ends the suggestion, and it's never applied automatically.
- [An activity just saved but not yet indexed is missing from the query] → After a save the app has the saved activity locally and queries for the others, which are already indexed. A candidate saved seconds earlier on another device may be missed, and its page shows the banner later.
- [A dismissal on one device doesn't reach another] → Accepted (D3). It's limited to activities from the last 7 days.
