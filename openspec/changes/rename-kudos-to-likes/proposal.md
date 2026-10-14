# Proposal

## Why

The app calls a like on an activity "kudos" (Strava's word). We want the plain, familiar word "likes" everywhere: the lexicon, the AppView, the client and the UI. The next change, likes on non-public activities stored in the giver's own private space, should use "likes" from the start. So this rename lands first, while the namespace is still a placeholder and nothing is deployed.

## What Changes

- **BREAKING** The record `app.activitytracker.feed.kudos` becomes `app.activitytracker.feed.like` (`lexicons/app/activitytracker/feed/like.json`), with the same shape (`subject` strong ref, `createdAt`).
- **BREAKING** The query `app.activitytracker.feed.getKudos` becomes `app.activitytracker.feed.getLikes`, and its output array `kudos` becomes `likes`.
- **BREAKING** In `feed/defs.json`, `activityView.kudosCount` becomes `likeCount` and `viewerState.kudos` becomes `viewerState.like`. Descriptions that say kudos say likes.
- No compatibility for old `feed.kudos` records: the AppView stops indexing them, and clients stop writing them.
- The AppView renames its `kudos` table and indexes to `likes` in a new Flyway migration. The Kotlin mirror, indexer, views and routes follow the new names.
- The client renames `toggleKudos`, `LocalKudos`, the detail screen's kudos list, events and errors, and all user-facing strings ("Give kudos", "%d kudos", "No kudos yet.", …) to likes, with correct singular and plural forms ("1 like", "2 likes").
- The `feed/activity-visibility` spec, `BACKLOG.md`, `README.md`, the `.agents/rules` and the `lexicon-change` skill say likes.
- Behaviour doesn't change: who may like what, counting athletes once, no self-likes, and no likes on non-public activities all stay as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-visibility`: the requirement "No kudos or comments on non-public activities" is renamed to "No likes or comments on non-public activities", and its text and scenarios name likes and `getLikes`. The rule itself is unchanged.

## Impact

- **Lexicons**: `feed/kudos.json` → `feed/like.json`, `feed/getKudos.json` → `feed/getLikes.json`, `feed/defs.json`.
- **`core/lexicon`**: `Nsid` (`FEED_LIKE`, `FEED_GET_LIKES`, `indexedCollections`), `Records.kt` (`LikeRecord`), `Views.kt` (`likeCount`, `ActivityViewerState.like`), `Queries.kt` (`GetLikes`), `RecordSerializationTest`.
- **AppView**: new migration `V17__rename_kudos_to_likes.sql`, `db/Tables.kt`, `index/Indexer.kt`, `api/Views.kt`, `api/Routes.kt`, `api/TierAccess.kt` comment, `AppViewTest`, `ActivityVisibilityTest`.
- **Client data**: `ActivityRepository`, `ActivityManager`, `FeedRepository`, `LocalKudos.kt` → `LocalLikes.kt`, and the `core/data` jvm tests.
- **Screens and presenters**: `UiError`, `ActivityListEvent`, `ActivityDetailScreen`, `FeedScreen`, `ProfileScreen`, `ActivityListEvents`, `ActivityDetailPresenter`, `SingleFlight` KDoc, the presenter tests, and the `detekt-rules` `UseSingleFlight` KDoc.
- **UI**: `strings.xml`, `ActivityDetailUi`, `ActivityCard`, `ActivityItems`, `Labels`, `AppButtons`, `AppIcon`, `Color.kt`.
- **Local data**: on upgrade, cached feed rows written with `kudosCount` no longer decode and are dropped until the next refresh. Pending outbox `feed.kudos` writes are still sent, but the AppView ignores them. Both are dev-only effects, since nothing is deployed.
