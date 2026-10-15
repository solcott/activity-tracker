# Proposal

## Why

An activity's zone snapshots sit inside its `feed.activityStreams` record, and the AppView shows them only to the athlete or when the athlete's training details are public, because it can't tell who may see non-public training. So an athlete whose training is Followers or Mutuals shows zones to nobody but themselves. Zone bounds reveal thresholds (Coggan power zones are percentages of FTP), so they must never be wider than the training they come from. Giving zones their own record, stored where its visibility is, as activities and charts already are, lets them follow the tier again.

## What Changes

- New `app.activitytracker.feed.activityZones` record (rkey = the activity's) holding `activity`, `heartRateZones` and `powerZones`. It is stored at the narrower of the activity's charts visibility and the athlete's training visibility: in the repo when both are Public, otherwise in the athlete's tier space for that visibility. The collection is already in the `space.athlete` declaration.
- **BREAKING** Remove `heartRateZones` and `powerZones` from `feed.activityStreams`. Existing inline snapshots are dropped, not migrated: nothing has been deployed beyond local dev.
- Publishing reads the training visibility fresh when it queues the zones record.
- Moving an activity's charts moves its zones to the narrower of the new charts visibility and the training visibility.
- Narrowing training visibility first queues moves, in the outbox, for every zones record stored wider than the new tier, drains the outbox, and only then saves the profile. A failure leaves zones narrower than training, never wider. Widening or clearing training moves nothing.
- At session start the client sweeps its own zones records: any stored wider than its activity's charts and the current training allow is moved down, and strays left by interrupted moves are deleted.
- The AppView indexes `feed.activityZones` from the repo and tier spaces, takes its visibility from where it's stored, serves the narrowest copy while two exist, and returns zones from `feed.getActivityStreams` only to viewers that visibility admits. It no longer checks whether training is public.

## Capabilities

### New Capabilities

- `feed/activity-zones`: where an activity's zone snapshots are stored, who sees them, and how they follow changes to charts and training visibility.

### Modified Capabilities

- `profile/private-details`: "Stream zones follow public training" is removed, replaced by `feed/activity-zones`.
- `spaces/appview-syncer`: "Only activity data is indexed from spaces" adds `feed.activityZones` to what is indexed from tier spaces.

## Impact

- **Lexicon:** new `lexicons/app/activitytracker/feed/activityZones.json` (`#zoneSnapshot` moves there from `activityStreams.json`). `ActivityZonesRecord` and `Nsid.FEED_ACTIVITY_ZONES` in `core/lexicon`, added to `Nsid.indexedCollections`. `GetActivityStreams.Output` keeps its zone fields.
- **Client (`core/data`):** a zones store beside `PdsActivityStreamsStore` (put, move, delete everywhere); `ActivityRepository.publish` and `setStreamsVisibility`, `ActivityManager.setVisibility` and `delete`; `TrainingEdit`/`ProfileRepository.save` for the narrowing backfill; a session-start sweep; one mutex shared by the sweep, the backfill and charts moves.
- **AppView:** a Flyway migration adding an `activity_zones` table with its own `visibility` and dropping `heart_rate_zones`/`power_zones` from `activity_streams`; `Indexer.spaceCollections`, a zones index, and `ActivityStreamsViews` (drop `seesTraining`).
- **Docs:** `.agents/rules/data-and-auth.md`, `.agents/rules/appview.md`, the `space.athlete` declaration's description, and the "Zones for Mutuals" item in `BACKLOG.md`.
- **Local dev data:** existing zone snapshots disappear from charts; re-import to get them back.
