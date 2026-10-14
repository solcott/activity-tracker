# Proposal

## Why

Followers, Mutuals and Only-me activities can't be liked. Clients hide the action, and the AppView serves no counts or likers for them. That was always meant to be a temporary restriction until likes had somewhere private to live, and most of what athletes share with their followers is exactly what they want feedback on. A like on a non-public activity can't be a public repo record, because the record would reveal the activity and the giver's interest in it. So this change gives each athlete a private `interactions` space for such likes, which only the giver and the AppView can read. The AppView then shows each like to whoever may see its activity now.

Depends on `rename-kudos-to-likes`, which must land first: this change uses `feed.like`, `getLikes`, `likeCount` and `viewer.like`.

## What Changes

- A new space key, `interactions`, in `app.activitytracker.space.athlete`. Only the giver and the AppView may read it, and the managing app denies every write, as for every key.
- Liking a non-public activity writes `feed.like` to the giver's `interactions` space through the outbox. The client creates the space on first use and announces it to the AppView. Liking a Public activity still writes a repo record. A like never moves after it's written. Unliking deletes the record from both places, which is idempotent, so the client never needs to know where it was stored.
- The space declaration adds `feed.like` to its `collections`, so athletes must sign in again to like non-public activities. A session without the `space:` scope gets `ScopeMissingError` for those likes, as it does for non-public visibilities, and can still like Public activities.
- The AppView syncs `interactions` spaces like tier spaces. From them it indexes only `feed.like`; from tier spaces it still indexes only activities and their streams.
- **Serve-time visibility for likes.** A like, from the repo or a space, counts and is listed for a viewer only while both the viewer and the like's giver may see the activity at its current tier. Likes follow the activity both ways. Narrowing it hides likes from viewers who can no longer see it. Widening it shows them to the new audience, including likes given while it was non-public.
- The "No likes or comments on non-public activities" requirement is replaced. Comments on non-public activities, and the comments feature in general, are left to a later change.
- The likes table gets an `origin` column (the repo, or the space a like was synced from), so a space delete or a space drop removes only that space's likes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-visibility`: the "No likes or comments on non-public activities" requirement is replaced by requirements for liking activities at every visibility, where likes are stored, and who sees them. Comments on non-public activities stay unavailable.
- `spaces/managing-app`: `checkUserAccess` admits the AppView, and no one else, to read an athlete's `interactions` space.
- `spaces/appview-syncer`: the AppView syncs `interactions` spaces and indexes only `feed.like` from them, and `notifySpace` accepts them.

## Impact

- **Lexicons**: `space/athlete.json` (`feed.like` in `collections`, and the `interactions` key in its description); `feed/defs.json` descriptions of `likeCount` and `viewer.like`; `space/notifySpace.json`, if its description lists the tiers. No new record types or queries.
- **core/lexicon**: an `AthleteSpace.INTERACTIONS` constant.
- **AppView**:
  - `SpaceAccess` (the new key);
  - `SpaceSyncer.isSyncable`;
  - `Indexer` (collections allowed per space key, and like writes and deletes from spaces);
  - `Views` and `TierAccess` (serve-time like visibility in counts, `viewer.like` and `getLikes`, replacing `acceptsInteractions`);
  - a Flyway migration for `likes.origin`;
  - `dropSpace` covering likes;
  - tests.
- **Client data**:
  - `ActivityRepository.toggleLike`: place by visibility, delete from both places;
  - the outbox's space target for the `interactions` space;
  - `AthleteSpaces`: create and announce `interactions`;
  - `ActivityManager`'s view projection, which stops zeroing likes for non-public visibilities;
  - `LocalLikes`.
- **UI**: the like action and count shown on non-public activities. Comment actions stay hidden there.
- **Docs**: `BACKLOG.md` (remove the entry, and add "Comments" as a follow-up that reuses this storage model), `.agents/rules/data-and-auth.md`, `CLAUDE.md`'s data-flow paragraph.
- **Re-sign-in**: required once for non-public likes, because the space declaration changes.
