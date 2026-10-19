# Design

## Context

Today an activity is one recording. Its parts:
- **The record:** in the repo when it's Public, otherwise in its tier's space.
- **Detail records and streams:** `feed.activityDetails` and one `feed.activityStreams` per tier holding its channels (`ActivityLayout`).
- **Private records in `self`:** its track (`feed.activityTrack`), original file and devices.

Clients write all of it through the outbox. The AppView only indexes it.

Two features come close to combining:
- **`recordingOf` grouping.** `Views.shownInFeeds()` hides a same-visibility recording, `recordingsOf()` lists it, and `ActivityManager` ungroups it and moves recordings with their primary's visibility.
- **`ActivityRepository.mergeInto`.** `ActivityMerge.fillGaps`, `fillMissing` and `shifted` move another recording's data onto an activity's timeline: `offsetSeconds` from the start, nearest-sample resampling.

Both are reused here. Neither is removed in this change (see proposal: `combine-on-import`).

## Goals / Non-Goals

**Goals:**
- A combined activity that every reader (AppView, feeds, export, later segments and totals) handles as an ordinary activity, with no composition step on read.
- Members stay byte-for-byte as they were, so splitting needs no restore step.

**Non-Goals:**
- Letting the athlete choose sources: they're ranked by default here, and `combined-stream-sources` adds choosing.
- Import-time combining, or removing Merge and Group (`combine-on-import`).
- Detection and suggestions (`combine-suggestions`).
- Totals, segments and leaderboards themselves. This change only makes sure a member is never shown as an activity of its own, so they'll leave members out when they're built.
- Combining other athletes' activities, such as a group ride.

## Decisions

### D1. Membership lives only on the combined record
`feed.activity#combined` is `{ members: [at-uri] (2..10), sources: [#channelSource] }`. Members get no back-link (`partOf`).
- **Why:** combining then writes a single new record and never rewrites a member, so a member can't point at a combined activity that's gone. Splitting is one delete.
- The AppView works out the reverse link at index time.
- **Rejected:** a `partOf` on each member, written alongside the combined record. That's N+1 writes that can disagree, and every member's record gets rewritten.

`#channelSource` is `{ channel: string (open knownValues: the streamData channels plus position, laps, legs, sets, lengths, dive), members: [at-uri] }`, highest rank first.
- The ranking is stored even when it's the default, so every client rebuilds the same thing, and so `combined-stream-sources` only has to rewrite this list.
- `position` ranks the members whose GPS track and polyline are used.

### D2. The client builds the combined activity and stores the result
A new pure `ActivityCombiner` in `core/data` takes each member's whole record (`effectiveRecord` over its details), its stream data, its track points and the rankings. It returns:
- a combined `ActivityRecord`;
- one `ActivityStreamData`;
- a GPX track;
- the polyline and its gaps.

`ActivityManager.combine` then writes these through the existing `ActivityLayout.split` / `queueRecords` / `streams.put` / `tracks.put` paths under a new TID rkey, at the visibility that rkey's record has.

- **Rejected: building at read time on the AppView.** The AppView would have to read every member's streams across tiers on each `getActivityStreams`, and every future consumer (segments, totals, export) would have to build too. Storing the result costs one extra copy of the data and a rebuild whenever membership or ranking changes. Both are rare.

### D3. Timeline and choosing a member per sample
- **Timeline:**
  - The combined start is the earliest member's start.
  - Each member's samples are shifted by `offsetSeconds = member.startedAt - combined.startedAt` (`ActivityMerge.shifted`).
  - The combined `time` array is every member's sample times together, deduplicated, in whole seconds.
  - The result goes through the existing stream resampling (`feed/stream-resampling`) to stay under the blob limit.
- **Choosing a member:** for each channel and each time `t`, walk the channel's ranked members and take the first whose span covers `t` and whose nearest sample (`ActivityMerge.nearest`) is within 5 s and isn't null. If none qualifies, the sample is null.
  - Between back-to-back members the timeline has no samples at all, because no member has a time there.
- **Distance carries on:** whenever the member supplying `distance` changes, later values from the new member are offset by `combined[t-1] - member[t]`, so the curve never jumps back. `speed` isn't offset. `altitude` isn't corrected: devices calibrate differently, so a step can show at a handover (see Risks).
- **Default ranking:** per channel, members with more non-null samples rank higher, and ties go to the earlier start.
  - For non-sample kinds (laps, sets, lengths, dive), a member that has any ranks above one that has none.
  - For `position`, the longer track ranks higher.

### D3b. Coverage, not file spans
A file's span can outlast its recording. A bike computer that died keeps its start-to-save span and may save a few points much later. Grouping by spans made a watch that took over count as recording at the same time: no legs, and one device's totals only.

- **`CombinedCoverage.of`:** each member's stretches of samples (streams, else track), cut at gaps over `CombinedRoute.BREAK_SECONDS` (2 min). Stretches under a minute are dropped unless nothing else is left.
- **Pieces and groups:** the stretches are `Piece`s, and groups are formed from pieces (D4), not members. Back-to-back groups of the same members merge, so a shared auto-pause isn't a new leg.
- **What follows from coverage:**
  - streams take samples only inside a member's pieces;
  - the route keeps only covered points;
  - elapsed time ends at the last piece;
  - a member's totals are shared among its groups by covered seconds (D5).
- Checked against a real ride: an Edge 840 that died 34 minutes in, then a Fenix 7s. Elapsed (5,136 s), distance, heart rate, power and track points match Strava's gotoes merge of the same two files.

### D4. Legs, laps and the map
- **Groups:** pieces (D3b) are sorted by start, and each one overlapping the group's span by at least half the shorter of the two joins that group. Every other piece starts a new group.
- **One group (all at the same time):**
  - no legs;
  - laps, sets, lengths and dive data come from the top-ranked member that has them, shifted onto the combined start.
- **Several groups (back to back):**
  - each group becomes a `#leg` with its start, elapsed and moving time, distance and heart rate summary;
  - each group's laps and other non-sample data come from its own top-ranked member, concatenated in order;
  - a member that was already multisport keeps its legs, nested in order.
- **Map:** each group's positions come from its top-ranked `position` member.
  - **Privacy zones:** they're applied to each group on its own (`privacy.forPublish`), because each group may start or end at home.
  - **Polyline:** the groups are concatenated. `polylineGaps: [integer]` lists the indices of the decoded points after which the line breaks.
  - **GPX track:** written with one `<trkseg>` per group.
- **Rejected: splitting the polyline into several encoded strings.** That breaks every existing reader of `polyline`. With a gaps index, old readers still draw the whole line, with straight joins.

### D5. Summary figures
The timeline is cut into stretches where the set of members recording doesn't change.
- **Elapsed time:** last end minus first start.
- **Moving time and calories:** in each stretch, from the top-ranked `speed`/`distance` member, in proportion to its elapsed time in that stretch, then added up over the stretches.
- **Distance:** the final combined `distance` sample. With no distance channel, each group's top-ranked member's distance, added up.
- **Elevation gain:** each stretch's top-ranked `altitude` member's gain. It's taken whole when the member is wholly inside the stretch, and otherwise worked out from its altitude samples there.
- **Averages and maxima:** worked out from the combined channels: heart rate, power, cadence, speed, temperature.
- **Taken from the top-ranked member for the matching channel:** `device`, `sensors`, sub-sport and the zones record's bounds.
- **Training load:** added up over the groups.

### D6. The AppView
- **Table (migration):** `activity_members(member_uri, combined_uri, author_did)`, primary key `(member_uri, combined_uri)`, plus an index on `combined_uri`.
  - Indexing a combined record replaces its rows.
  - A URI goes in only when its DID is the record's author and the list fits 10, so the input is treated as untrusted.
- **Valid members:** these checks run at read time, because a member may be indexed after its combined activity. A member counts when:
  - it exists, by the same author and at the same visibility;
  - it isn't itself combined;
  - its claiming combined activity has the smallest rkey (that is, the earliest TID) among its claims.
- **`shownInFeeds()`:** leaves out activities with a valid membership, as well as today's `recordingOf` rule.
- **Views:**
  - `activityView.members`: a new `#memberView` with uri, startedAt, device, sport and elapsed time.
  - `activityView.combinedInto`: the combined activity's URI, on a member.
- **Likes and comments:**
  - `likeCount` and `commentCount` count subjects in `{combined} ∪ valid members`, under the existing `admittedLikes`, `admittedComments` and `commentsVisibleTo` rules.
  - `getLikes` and `getComments` on a combined activity take the same set.
  - A comment hide (`feed.commentHide`) from the athlete applies wherever the comment is.

### D7. Client operations
- **Combine:**
  1. `ActivityManager.combine(into, other)` drains the outbox.
  2. It refuses when any member has no `cid` (still unsent), since its streams can't be read yet. This is the same limit `mergeInto` has.
  3. It moves `other` to `into`'s visibility with the existing visibility-move code (generalised from recordings to members).
  4. It reads each member with `places.read`, plus `feed.streams` (the owner's parts) and the `self` track.
  5. It builds the combined activity (D2) and queues its writes.
  6. It updates the feed cache: members out, combined activity in.
- **Into an activity that's already combined:** the new member is added and the combined activity is rebuilt under the same rkey.
- **Remove a member or split:**
  - removing a member rewrites the combined activity without it;
  - splitting deletes the combined activity's record, details, streams and track;
  - members are never touched.
- **Delete:**
  - with members, it runs today's `queueDelete` for each member and then for the combined activity;
  - deleting a member also rebuilds or splits its combined activity.
- **Visibility:** `setVisibility` adds `members` to its targets, next to `recordings`.
- **Edit:** works as today, on the combined record only. The `combined` field is kept, because `edit` copies the record.

### D8. UI
- **New `CombinePickerScreen`** across `:app-screens`, `:app-presenters` and `composeApp`, following `new-screen`.
  - It loads `getAuthorActivitiesByStart` from 12 h before to 12 h after the activity's start.
  - It leaves out the activity itself, its members, other combined activities and other activities' members.
  - Each row shows sport, start, duration and device.
- **Activity page:**
  - a "Combine with…" action in the owner's actions, next to delete and ungroup;
  - on a combined activity, a Members card (next to `Recordings`) with remove-member and split actions;
  - on a member, a "Part of …" link.
- **Delete dialog:** `ActivityDetailEvent.Delete(withRecordings)` also covers members.

## Risks / Trade-offs

- [The combined copy goes stale when a member changes later (only visibility moves can change a member here)] → A visibility move goes through the combined activity, which moves its members with it. Editing a member's own title or photos doesn't affect the combined activity. A member's streams never change after import.
- [An older client's `edit` drops the unknown `combined` field when it rewrites the record] → There are no released clients yet, and `lexicons.md` already requires unknown fields to round-trip. A test pins that `ActivityRecord` keeps `combined`.
- [Altitude steps at a handover between devices] → Accepted for now. The athlete can rank one altitude source first (`combined-stream-sources`). A later change could offset altitude the way distance is.
- [Very long combined days (10 members at 1 Hz) go over the 5 MB blob] → The existing resampling keeps it under the limit, and each member keeps its full-resolution data.
- [Comments written for an audience on a member] → `#audience` is checked against the viewer exactly as on the activity. The combined activity only widens which subjects are gathered, never who may see them.

## Migration Plan

- The lexicon changes add fields only, and the Flyway migration adds a table. Older AppView rows need no backfill, because no combined records exist yet.
- **Rollback:** delete the combined records. The members are untouched and show in feeds again once the table rows are gone.

## Open Questions

- The 5 s nearest-sample tolerance and the elevation-gain threshold for a partial stretch can be tuned against real Edge and Fenix pairs during apply. Neither changes the specs.
