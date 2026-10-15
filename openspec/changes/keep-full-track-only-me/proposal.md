# Proposal

## Why

Every activity with GPS carries a full-resolution GPX `track` blob on its `feed.activity` record, but nothing reads it: no client code, and no AppView route (`activity_media` indexes photos only). For a Public activity, anyone can download it with `com.atproto.sync.getBlob`, exact start and end points included. That's the leak the privacy-zones work is about to address. Meanwhile `spaces/appview-syncer` claims the AppView serves a non-public activity's track, which it doesn't. File export (`export-activity-files`) needs the full track, but only for the athlete. So it belongs where only the athlete reads it.

## What Changes

- **BREAKING** `feed.activity` loses `track`. The record keeps `polyline` (the simplified route that previews and maps draw), which privacy zones will trim later.
- New record `app.activitytracker.feed.activityTrack`, same rkey as its activity, holding the full-resolution GPX blob. It's always stored in the athlete's `self` (Only me) space, whatever the activity's visibility, and never in the repo.
- It's written through the outbox alongside the activity (same `PendingBlob` upload, placed in `self`). Edits, merges (`OwnDives` `MergeGain.Track`) and ungroups read it from `self` with the athlete's session. Delete removes it, as `ActivityManager.delete` already does for streams.
- Visibility changes don't move it: it's Only me already.
- The AppView neither indexes nor serves it. The space declaration lists the collection so the session can write it, and its description says the AppView ignores it.
- `spaces/appview-syncer` "Space-held media and charts served by tier" drops "and track".
- No backwards compatibility: there's no migration, and existing local data is reset.

## Capabilities

### New Capabilities
- `feed/activity-track`: where an activity's full-resolution track is stored, who can read it, and how it follows the activity's writes and deletes.

### Modified Capabilities
- `spaces/appview-syncer`: the AppView no longer claims to serve a non-public activity's track, and doesn't index `feed.activityTrack`.

## Impact

- Lexicons: `feed/activity.json` (remove `track`), new `feed/activityTrack.json`, `space/athlete.json` (collections + description), `Nsid`, the `core/lexicon` models. Republish local lexicons (`publish-lexicons.sh`), then sign in again for the new space grant.
- `core/data`: `ActivityRepository` (publish/edit `PendingBlob` for `track`), `OutboxProcessor` (a blob for a second record), `ActivityManager.delete`, `OwnDives` merge, `LocalRewrites` (drop `track` carry-over), `Places`.
- AppView: remove any `track` handling from indexing and views, plus tests.
- Follow with the `lexicon-change` skill.
- Precedes `export-activity-files` and the privacy-zones work.
