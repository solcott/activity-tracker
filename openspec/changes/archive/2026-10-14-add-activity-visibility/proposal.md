# Proposal

## Why

Every activity is a public repo record today. Anyone can read it from the athlete's repo or the firehose, and the AppView serves it to everyone. Athletes need to share some activities only with followers or mutuals, or keep them to themselves. Follow requests (`followPolicy: approve`) shipped recently, and they mean little while every activity stays public.

## What Changes

- Each activity gets a visibility: **Public**, **Followers**, **Mutuals** or **Only me**, using the existing `actor.defs#visibility` values. A Public activity stays a `feed.activity` record in the athlete's repo. A non-public one is a `feed.activity` record in the athlete's `app.activitytracker.space.athlete` tier space for its visibility (`followers`, `mutuals`, `self`), and never in the repo.
- The athlete picks the visibility when recording, entering manually, importing (single and batch) and editing, on every platform. Changing it later moves the record: it's written to the new place before it's removed from the old one. Making a public activity non-public warns that copies already published can't be recalled.
- Charts (`feed.activityStreams`) keep their own visibility, which can't be wider than the activity's. Narrowing the activity narrows its charts too. Non-public streams move into the tier space for their visibility, alongside non-public activities.
- Athletes can set a **default activity visibility** and a **default charts visibility** in Settings, stored in a new `actor.activityDefaults` record in their `self` space. Both default to Public when unset. New activities start from these defaults, and each can be overridden.
- **The AppView becomes a member and registered syncer of athletes' tier spaces, `self` included.** It mints Spaces delegation tokens as its own `did:web`, which gives its DID document an `#atproto` signing key and a syncer service entry. It admits itself in `checkUserAccess` and registers with `com.atproto.space.registerNotify`. It indexes activities and streams from `notifyWrite` callouts and `listRepoOps`, and reconciles periodically. A new `app.activitytracker.space.notifySpace` procedure lets a client tell the AppView a tier space exists.
- **The AppView now holds non-public data.** It stores non-public activities and streams, and serves each one only to the viewers its tier admits: the timeline, an athlete's activities, `getActivity`, activity counts, media and charts. It still never stores `actor.privateDetails`.
- Activities are identified everywhere by their logical URI `at://{did}/app.activitytracker.feed.activity/{rkey}`, wherever the record is stored. Moving an activity between tiers keeps its URI, so interactions stay attached.
- Activity views gain a `visibility` field. Clients show it, for example as a lock badge.
- **Kudos and comments are unavailable on non-public activities in this change.** Clients hide the actions. The AppView ignores public kudos and comments whose subject isn't public, and doesn't serve them.

### Non-goals

- **In-space kudos and comments (follow-up change).** Kudos and comments on a non-public activity should become records in the commenter's own repo inside the activity's tier space. That needs `checkUserAccess` to admit tier members for `write`. A move between tiers strands those records in the old space: the athlete can't move or delete other members' records, so they stay readable to the old tier. This change keys interactions by the logical activity URI so the follow-up needs no data migration, and it records the follow-up in `BACKLOG.md`.
- Migrating existing non-public `feed.activityStreams` records out of repos. Shared deployments don't exist yet, and local data is throwaway. The AppView keeps enforcing the visibility of legacy repo stream records.
- Recalling public copies of an activity that is later made non-public.
- Hiding activities from search engines or using privacy zones. Those are separate concerns.

## Capabilities

### New Capabilities
- `feed/activity-visibility`: per-activity visibility, where each activity and its charts are stored, moves between tiers, the charts cap, default visibilities, the pickers, and the restrictions on non-public activities (no kudos or comments).
- `spaces/appview-syncer`: the AppView as a member and registered syncer of athletes' tier spaces: its signing identity, joining and registering, handling `notifyWrite`, reconciling, and serving space-held activities only to the viewers each tier admits.

### Modified Capabilities
- `spaces/managing-app`: read access by tier also admits the AppView's own DID for every tier, `self` included. The DID document gains an `#atproto` verification method and a syncer service entry.

## Impact

- **Lexicons**: `feed.activity` description (it may live in a tier space), `space.athlete` collections (add `feed.activity` and `actor.activityDefaults`), new `actor.activityDefaults` record, new `space.notifySpace` procedure, `feed.defs#activityView` gains `visibility`. Mirrored in `core/lexicon` and `Nsid`.
- **AppView**: Flyway migration (a `visibility` column on `activities` and `activity_streams`, plus a space sync state table), a space syncer service (credentials, `registerNotify` renewal, `notifyWrite` route, `listRepoOps` pull, reconcile job), visibility filters in `Views` and `ActivityStreamsViews`, blob proxying for space-held media and streams, `SpaceAccess` admitting its own DID, `did.json` and config for the signing key. It reuses `core/atproto`'s Spaces signing code, which needs a small exposure.
- **Clients**: `ActivityRepository`, `ActivityManager`, `ActivityStreamsStore` and the outbox gain space-targeted writes and moves. `AthleteSpaces` notifies the AppView. There's a new defaults store. Visibility pickers go on Record, Manual, Import and Edit, a defaults section in Settings, badges on feed cards and detail, and kudos and comment actions are hidden on non-public activities.
- **Local stack**: the AppView signing key in `docker/.env`. The syncer needs the tunnel, as managing-app callouts already do.
- **Docs**: `AGENTS.md` and `.agents/rules/appview.md` drop "stores nothing private" in favour of tier-scoped storage. `BACKLOG.md` "Private activities" is rewritten, and a new in-space kudos and comments entry is added.
- **Risk**: Spaces is an alpha. The syncer path (a `did:web` delegation issuer, `registerNotify`, space blobs) is untested, so a spike comes first, and it's the go/no-go for this design.
