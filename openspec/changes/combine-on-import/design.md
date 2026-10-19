# Design

## Context

How duplicates work at import today:
- `DiveMatcher.match` looks only at dives. `rememberOwnDives` loads the athlete's dives around the file's span through `getAuthorActivitiesByStart`.
- `duplicateInfo` offers `DuplicateAction.Ignore`, `ImportAnyway`, `Merge` and `Group` (the in-file actions leave out Merge).
- Merge works out `MergeGain`s from `rememberLoggedStreams` and calls `ActivityRepository.mergeInto`. Group publishes with `recordingOf`, and `DiveBatches` chains groups within a logbook.

`combine-activities` adds `ActivityManager.combine` and `ActivityCombiner`. It refuses a member that isn't sent yet, because it reads members' streams from the AppView.

## Goals / Non-Goals

**Goals:**
- One duplicate flow for every sport and every file format.
- No step at import that waits for the AppView.
- Old groups migrate once without the athlete doing anything.

**Non-Goals:**
- Detecting back-to-back recordings that don't overlap (`combine-suggestions`).
- Removing `recordingOf` from the lexicon and the AppView. That's a later clean-up, once migration has run everywhere.
- Undoing past merges.

## Decisions

### D1. `DiveMatcher` becomes `ActivityMatcher`
The algorithm stays the same: overlap of at least half the shorter, the 3-minute drift for zero-length entries, and the whole-hour shift within 10% length.
- **The sport filter goes.** The athlete decides whether a run that overlaps a walk is the same thing.
- **The whole-hour shift applies only to an import whose file has no UTC offset.** FIT and TCX carry UTC, so a match hours off there is a different activity.
- **Loading the log:** `rememberOwnDives` becomes `rememberOwnActivities` and drops its `sport` parameter. The window stays the file's span ±14 h.
- A log activity that's a member of a combined activity is matched through its `combinedInto`, so Combine adds to that combined activity.

### D2. Combining an activity that's still a draft
`ActivityManager.combine(into: OwnActivity, draft: ActivityDraft)`:
1. It queues the draft as its own activity, as `publishAll` does.
2. It builds the combined activity from the draft's local data (record, streams, route, laps) together with the logged member's stored data.

So the import never waits to be indexed.
- **The logged member must still be sent** (`cid` non-null), as before. When it isn't, Combine is disabled with a reason ("still uploading"), and Import anyway stays available.
- **Within a file:** `combine(drafts: List<ActivityDraft>)` builds from drafts only. `DiveBatches`' group chain becomes a combine chain: every copy of one activity in the file joins the first copy's combined activity.
- **Rejected: a deferred "combine once indexed" outbox entry.** It's a new outbox kind with its own retry rules, when the draft already holds everything the combiner needs.

### D3. Removing Merge and Group
These go:
- `ActivityRepository.mergeInto` and `mergeStreams`;
- `ActivityMerge.fillGaps(ActivityRecord, ActivityRecord)` and `addsChannels`;
- `MergeGain` and `rememberLoggedStreams`;
- `DuplicateAction.Merge` and `Group`, `groupTarget` and `mergeTarget`;
- the `recordingOf` parameter of publishing.

`ActivityMerge.shifted`, `fillMissing`'s resampling and `nearest` stay, because `ActivityCombiner` uses them. They may move into it, renamed.

- **Tests:** those that pinned merge behaviour go, except where the same behaviour now holds for the combined activity. Each removed requirement's scenario gets a matching combiner test: the bike computer adds power, the watch adds lengths, the logbook adds tank pressure, and so on.
- `DuplicateAction` becomes `Ignore | ImportAnyway | Combine`, with `Combine` the default.

### D4. "Add a recording…"
- `ImportActivityScreen` gains `combineInto: AtUri?`. With a target:
  - there's no duplicate search;
  - the visibility and category pickers are hidden, since the import takes the target's;
  - any file whose activity starts more than 12 h from the target is refused before saving;
  - a logbook of several entries offers only the entry that falls within 12 h of the target.
- Saving calls `combine(target, draft)`, then returns to the combined activity's page.

### D5. Migrating legacy groups
- **When it runs:** `LegacyGroupMigration` runs in `SessionScope` once the session is signed in. It records, per DID, in Room, that migration finished.
- **What it does:**
  1. It pages through the athlete's own activities (`getAuthorActivities`), collecting those with `recordings`.
  2. For each primary with no `combinedInto`, it calls `combine(primary, recordings)` starting from the primary.
  3. It rewrites each recording without `recordingOf`, through the existing `queueUngroup` record rewrite, without its feed-cache changes.
- **If interrupted:** writing the combined activity first means a run interrupted before the clears still hides the recordings, by both rules. On the next run the primary is already a member, so only the clears remain.
- **Failures:** a failed group is logged and left. The finished flag is set only when every group succeeds, so the next sign-in retries.
- **Lexicon:** `feed.activity#recordingOf` gets the description "Legacy: …clients no longer write it". `RecordingsUi` stays for groups not yet migrated.

## Risks / Trade-offs

- [Matching every sport flags more false positives, such as a commute ride overlapping a logged walk] → The notice names the matched activity, and Import anyway is one tap. Combine is the default only because overlapping recordings by one athlete are almost always the same activity.
- [Removing Merge loses the "single activity, no copies" outcome some athletes may prefer] → The combined activity is the single activity people see. Members are hidden from feeds and totals, and split keeps them recoverable.
- [Migration on a large log pages the whole author feed] → It runs once per account in the background, and groups were only ever created from dive imports, so there are few.
- [Combine is disabled while the logged activity is still uploading] → Rare, because it's sent within seconds while online. Import anyway, then "Combine with…" later, covers it.

## Migration Plan

1. Ship `ActivityMatcher` and the new actions.
2. The migration runs at each athlete's next sign-in.
3. A later change removes `recordingOf` handling from the AppView, `RecordingsUi` and the lexicon once no unmigrated groups remain.

**Rollback:** combined activities split cleanly, and the cleared `recordingOf` links can be rewritten from the members list if ever needed.
