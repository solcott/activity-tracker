# Design

## Context

See proposal.md for the motivation and specs for the behaviour. What exists today that this builds on:

- **Segment tracks** (`segments/segment-track`).
  - Clients write a privacy-zone-trimmed `feed.segmentTrack` per GPS activity in `self` while `actor.segmentPrefs.matching` is on. The blob holds E7 positions and seconds from the track's start, never wall-clock time.
  - The client side is `SegmentTracks`, `SegmentTrackSweeper` and `SegmentTrackStore`, whose `setExcluded` re-puts the record with the same blob through the outbox.
  - The AppView indexes these tracks into `segment_tracks` (`leaderboard_excluded`) and gates every read through `usableSegmentTracks()` (joins `segment_prefs.matching = true`).
- **Matcher.** `SegmentMatcher` drains the `segment_match_jobs` queue (`track`, `segment`, `candidate` and `tiers` kinds).
  - `matchTrack` fetches the blob from the space (`SpaceBlobs`), writes `segment_track_cells` (0.01° cells, `SegmentCells`), then replaces efforts and candidate riders.
  - `trackToMatch` skips members of combined activities and types with no `SegmentSport` group.
- **Change detection.** `index/MatchInputs.activityChanged` queues `track` or `tiers` jobs when an activity, its details or its streams change.
- **Tiering.** `effortVisibility` reduces the activity's tier with its Map and Pace/Speed category tiers (`DetailCategory.currentTiersOfChannels` over `activity_details` and `activity_streams`). `retier` refreshes it.
- **Exact start.** `activities.started_at` is from the row's own record, so it is the local midnight when Start time is narrower. The exact start is on the narrower tier's `activity_details` record, which the AppView holds.
- **Visibility helpers:** `visibleTo(viewer)` (`api/TierAccess.kt`), `effectiveFollow()` (`api/ActorDetails.kt`), and `blocked`, `notBlocked` and `notMuted` (`api/Blocks.kt`).

## Goals / Non-Goals

**Goals:**
- Reuse the segment track and matcher pipeline. There is no second trimmed-route record and no second job loop.
- Keep each consent's derived data separable, so turning one purpose off drops exactly what it produced.
- Cheap reads: pairs are precomputed, and each page of activity views adds one batched query.

**Non-Goals:**
- Real-time grouping. Pairs appear when the matcher runs (seconds to minutes after both tracks are indexed), the same as efforts.
- Group objects with ids, names or membership lists. The data model is pairs.
- Back-pressure on huge group rides beyond a cap on partners scanned (see Risks).

## Decisions

### D1. Consent in a new `actor.groupPrefs`, leave mark on the segment track
`actor.groupPrefs` (`literal:self`, in `self`) holds `mode` (an open string: `off`, `connected` or `anyone`; unknown counts as off) and `updatedAt`.

The per-activity leave mark is `feed.segmentTrack#groupExcluded`, written by a `SegmentTrackStore.setGroupExcluded` that mirrors `setExcluded`. It goes through the outbox and re-puts the record with its existing blob, keeping `excludeFromLeaderboards`. Any rewrite (`put` from the sweeper, a re-trim) carries both marks forward from the stored record.

- *Alternative: fields on `actor.segmentPrefs`.* This would avoid a new collection and so avoid signing in again. It was rejected because it muddles two consents in one record and in every drop path, and leaving needs a scope change anyway if it were a separate record.
- *Alternative: a separate `feed.groupLeave` record per activity.* Rejected: the segment track already carries per-activity choices (leaderboard exclusion), and a track-less activity can't be grouped anyway.

Cost: `space/athlete.json` lists `actor.groupPrefs`, so athletes sign in again before the setting is offered (`canUse`).

### D2. One track, purpose-gated reads
The client writes segment tracks while `matching || mode != off` (`SegmentTracks`, `SegmentTrackSweeper` and the backfill gate). On the AppView:

- `usableSegmentTracks()` keeps its meaning: segment consent on. Efforts and candidate riders use it.
- A new `groupableTracks()` joins `group_prefs` with `mode in (connected, anyone)` and `segment_tracks.group_excluded = false`.
- `matchTrack` loads a track when it is usable for **either** purpose. It writes cells for any such track, runs efforts and riders only if `usableSegmentTracks` admits it, and queues or runs grouping only if `groupableTracks` admits it.
- `trackToMatch`'s `none()` drop branch splits per purpose. It drops efforts and riders when segment consent fails and pairs when group consent fails. It drops cells only when neither holds.

### D3. Pairs table, matched by a `group` job
Table `activity_group_pairs`:

| Column | Notes |
|---|---|
| `activity_a`, `activity_b` | `a < b` lexicographically, primary key `(a, b)` |
| `author_a`, `author_b` | for drops |
| `visibility_a`, `visibility_b` | each side's *partner tier* |
| `matched_at` | |

There are indexes on `activity_b` and on each author. The partner tier is the narrowest of the activity's tier and its Start time and Map category tiers. A new `pairVisibility(row)` beside `effortVisibility` computes it with the same `currentTiersOfChannels`. `retier` refreshes it on `tiers` jobs.

A new job kind, `GROUP`, keyed by the track URI:
- Queued from `matchTrack` after cells are written, when the track is groupable.
- Queued from `MatchInputs.activityChanged` when start or sport changes (a `startedAt` change now counts in `MatchInputs`).
- Queued for all of an athlete's groupable tracks when their `group_prefs` goes from off to on.

`matchGroup(trackUri)` runs in two steps:
1. **Candidates (SQL).** Groupable tracks of *other* authors whose activity is not a combined member, whose `SegmentSport` group matches, whose `[start, end]` window overlaps, and that share at least one cell (`segment_track_cells` join). The candidate count is capped (e.g. 200, nearest by start).
2. **Pair rule (SQL)** on each candidate:
   - neither author has blocked the other (`blocked()`);
   - when either mode is `connected`, an `effectiveFollow()` exists in either direction.

   Then the blobs are fetched (cached per job) and the pure `GroupMatch.together(a, b)` is run.

The job then replaces that track's activity's pairs: it deletes the activity's rows and inserts the matches. Because the job of whichever track is indexed second sees the first, each pair is found once both tracks exist. A rematch of either side rewrites it.

Absolute times: `exactStart(activity)` takes the activity's `startedAt` from `effectiveRecord` merged over **all** of its `activity_details` rows. That is internal and never served. Each point's absolute time is `exactStart + t`.

- *Alternative: compare `activity_streams` lat/lng channels.* Rejected: the streams aren't privacy-trimmed the same way, and using them would be a new purpose for data held under different consent.

### D4. `GroupMatch.together`, a pure function
Inputs are two lists of pieces of `(lat, lng, absSeconds)`. Steps:
1. Overlap `O = [max(startA, startB), min(endA, endB)]`. Require `|O| >= 300 s` and `|O| >= 0.5 * min(elapsedA, elapsedB)`.
2. Sample `O` every 30 s. At each sample, interpolate each track's position only if the sample falls inside a piece (privacy-zone gaps give no position).
3. Over samples where both have a position (`shared`), require `shared >= 10` and at least 60% of them within 100 m (haversine). A paddle uses 150 m (rafts spread out).

All thresholds are named constants in one object, like `SegmentSportGroup`, and tuned by tests.

### D5. Pair rule enforced at match time *and* read time
Follows, blocks and modes change without a re-match.
- At read time, `hydrate` filters pairs by:
  - both authors' current `group_prefs` mode is not off;
  - `connected` implies an effective follow either way;
  - the two authors haven't blocked each other.
- At index time:
  - a `group_prefs` change to off, `dropSpace(self)` and `deleteActor` delete the author's pairs;
  - a block deletes the pair rows between the two authors (cheap and final);
  - follow changes rely on the read-time filter. A later re-follow shows the pair again with no rematch, which matches "pairing follows changes".

### D6. Serving: `activityView.group`
A new `feed.defs#groupPartner { author: actor.defs#profileViewBasic, uri }` and `activityView.group: groupPartner[]` (optional, absent when empty, capped at 20 by most recent `matched_at`). `Views.hydrate` runs one query: pairs where `activity_a in page` or `activity_b in page`, partner side joined to `activities`. It is filtered by:
- `visibleTo(viewer)` applied to the partner's stored partner tier (the `visibleTo` overload taking a visibility column and an author column);
- `notBlocked(viewer, partnerAuthor)` and the viewer's `notMuted`;
- D5's pair-rule filter.

A partner whose activity row is gone is skipped. Profiles are hydrated with the page's existing actor batch.

### D7. Client
- **`GroupPrefsRepository`** (`core/data/.../segments/`), modelled on `SegmentPrefsRepository`. `GroupMode { NEVER_ASKED, OFF, CONNECTED, ANYONE }`, with `current`, `saved`, `load`, `refresh`, `shouldAsk`, `canUse` and a direct-write `save`. Its `saved` merges into `repairRoutesOnSave` so the sweeper runs after either setting changes.
- **"Tracks wanted"** becomes one shared predicate, `TrackConsent.wanted = matching == ON || group in {CONNECTED, ANYONE}`, used by `SegmentTracks.put` and `SegmentTrackSweeper`. The sweeper deletes all tracks only when it is false.
- **`SegmentTrackStore`** gains `groupExcluded(activity): Boolean?` and `setGroupExcluded`. `StoredSegmentTrack` gains `groupExcluded`, and `put` takes and keeps both marks.
- **Settings** gain a three-way `GroupActivitiesSetting` beside `SegmentMatchingSetting` in `feature/settings`.
- **The ask.** The feed's one-time ask (where `SegmentPrefsRepository.shouldAsk` is consumed) gains a grouping ask, shown after the segment ask is answered, never both at once.
- **Detail page.** `feature/detail` gets an `ActivityGroup` section (`activityView.group`, avatar row, each opens `ActivityDetailScreen(partner.uri)`), and an owner-only Leave group / Rejoin group action through `rememberSingleFlight`. The mark shows at once from local state until the AppView catches up, as segment name votes do.
- **Feed card.** `ActivityCard` (`:app-common-ui`) shows a "with …" line, as a plural string resource.

## Risks / Trade-offs

- **Big group rides are O(n²).** 50 riders means each track job compares 49 candidates. → Candidate cap, per-job blob cache, and the cell and time prefilter in SQL. The `together` sampling is linear in duration.
- **Exact start comes from details the viewer can't see.** The pair tier includes Start time, so a viewer who can't see Bob's start never sees Bob as a partner. The match still *uses* the hidden start, which is within the athlete's consent (the AppView already holds it).
- **Privacy zones at a shared start.** If both live in the same zone the pieces start later. The overlap ratio is measured on the activities' elapsed times, not the shown parts, and a long hidden stretch may drop below 10 shared samples. → Acceptable: privacy wins. Covered by a scenario.
- **Re-sign-in friction.** The new scope blocks the setting until the athlete signs in again. → Same UX as segment matching (the setting explains it).
- **Leave choice of a deleted or re-created track is forgotten.** By design, the same as leaderboard exclusion. Documented in the segment-track delta.
- **Stale pairs after a follow ends** are hidden at read time, but rows linger until a rematch. → Harmless, since they are never served. A rematch on either side or a mode change clears them.

## Migration Plan

1. Flyway `V47__group_activities.sql`:
   - `group_prefs(did PK, mode, updated_at)`;
   - `activity_group_pairs` and its indexes;
   - `ALTER TABLE segment_tracks ADD group_excluded BOOLEAN NOT NULL DEFAULT FALSE`.
   - H2-portable SQL.
2. Ship the AppView first. With no `group_prefs` rows nothing pairs, and `activityView.group` stays absent. Old clients ignore the field.
3. Ship clients. Athletes opt in after signing in again. Turning grouping on backfills tracks only if segment matching was off.
4. Rollback: clients stop offering the setting. The AppView can drop the tables, and tracks written for grouping alone are deleted by the sweeper once the setting reads Off.

## Open Questions

- The exact distance and percentage thresholds (100 m and 60%, 150 m for paddling) are tuned against real recordings during implementation. They don't change the specs' "about 100 m for most of the overlap".
