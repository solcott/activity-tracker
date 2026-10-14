# Design

## Context

See proposal.md for why. A kudos is a public `app.activitytracker.feed.kudos` record in the giver's repo. The AppView indexes it from Jetstream into the `kudos` table (`V1__init.sql`, with `kudos_subject_idx` and `kudos_author_subject_idx`; `V3__drop_self_kudos.sql` deletes self-kudos). It serves `kudosCount` and `viewer.kudos` on `activityView`, and the list through `getKudos`. On the client, `ActivityRepository.toggleKudos` writes through the outbox, and `LocalKudos` overlays the viewer's own toggles on stale AppView reads. Feed pages are cached in Room as `AtJson`-encoded `ActivityView` JSON (`FeedItemEntity.json`), and outbox rows store their collection NSID as a string.

`Nsid.NAMESPACE` is a placeholder and nothing is deployed, so the only data affected is on developers' local stacks and devices.

## Goals / Non-Goals

**Goals:**
- "Kudos" doesn't appear in code, wire names, SQL, strings or docs, apart from committed migrations and archived changes.
- Behaviour is identical: every test passes after a mechanical rename, with no assertion changes beyond names.

**Non-Goals:**
- Reading, migrating or deleting existing `feed.kudos` records in repos.
- Changing what likes do: likes on non-public activities are the next change, and comments are a later one.
- Changing the like icon or the amber `tertiary` color.

## Decisions

**A clean NSID change, with no compatibility.** `feed.kudos` becomes `feed.like`, and the AppView indexes only `feed.like`. Old `feed.kudos` records stay in local PDS repos but are ignored. We considered also indexing `feed.kudos`, or migrating records client-side, but either would mean permanent dual-NSID code to protect data that exists only in dev stacks. The record is singular (`feed.like`, like `feed.comment` and Bluesky's `app.bsky.feed.like`), and the query is plural (`getLikes`, like `getComments`).

**Wire and Kotlin names:**

| Old | New |
|---|---|
| `feed.kudos` / `Nsid.FEED_KUDOS` / `KudosRecord` | `feed.like` / `Nsid.FEED_LIKE` / `LikeRecord` |
| `feed.getKudos` / `Nsid.FEED_GET_KUDOS` / `GetKudos` (output `kudos`) | `feed.getLikes` / `Nsid.FEED_GET_LIKES` / `GetLikes` (output `likes`) |
| `activityView.kudosCount` | `likeCount` |
| `viewerState.kudos` / `ActivityViewerState.kudos` | `like` |
| AppView `Kudos` table object, `views.kudos` | `Likes`, `views.likes` |
| `toggleKudos`, `LocalKudos`, `withKudosOverrides`, `FeedRepository.kudos` | `toggleLike`, `LocalLikes`, `withLikeOverrides`, `FeedRepository.likes` |
| `ActivityListEvent.ToggleKudos`, `ActivityDetailEvent.ToggleKudos` / `ToggleKudosList`, `KudosList`, `kudosList`, `UiError.LoadKudos` | `ToggleLike`, `ToggleLikesList`, `LikesList`, `likesList`, `UiError.LoadLikes` |
| UI `KudosButton`, `onKudos`, `rememberKudosList` | `LikeButton`, `onLike`, `rememberLikesList` |

Test names and fixtures (for example `seedBobsRunWithAlicesFollowKudosAndComment`, `LocalKudosTest`) follow the same mapping.

**Strings.** Resource keys are renamed (`kudos_count` → `like_count`, `kudos_content_description` → `like_content_description`, `error_load_kudos` → `error_load_likes`, `detail_*_kudos` → `detail_*_likes`, `detail_kudos_given` → `detail_liked`). The text uses real singular and plural forms: "1 like" / "%d likes", "Like (%d)", "Liked (%d)", "Show likes", "Hide likes", "No likes yet.", "Couldn't load likes", and "Its likes and comments go with it." in the delete dialogs.

**AppView migration: rename in place, then empty.** A new `V17__rename_kudos_to_likes.sql` runs `ALTER TABLE kudos RENAME TO likes`, renames both indexes to `likes_subject_idx` and `likes_author_subject_idx` with `ALTER INDEX … RENAME TO …` (both work in Postgres and in H2's PostgreSQL mode), and runs `DELETE FROM likes`. Every existing row is a `feed.kudos` record that the AppView will no longer index or delete, so keeping them would leave counts that can never go down. The columns (`uri`, `author_did`, `subject_uri`, `subject_cid`, `created_at`, `indexed_at`) don't say kudos and are kept. We rejected drop-and-recreate because it would repeat the V1 DDL, and a rename keeps the index definitions in one place. `V1`/`V3` are committed and stay untouched.

**Client local state.** Room's schema doesn't change (`FeedItemEntity` stores JSON), so `AppDatabase.version` isn't bumped. Cached rows with `kudosCount` and no `likeCount` fail to decode, and `FeedRepository.observe` already skips those with a warning until the next refresh rewrites them. Outbox rows queued for `feed.kudos` would still be sent but never indexed. Both are acceptable for dev-only data, so there is no Room migration.

## Risks / Trade-offs

- [A hit is missed] → Finish with `git grep -il kudos -- . ':!openspec/changes/archive' ':!server/appview/src/main/resources/db/migration/V1__init.sql' ':!server/appview/src/main/resources/db/migration/V3__drop_self_kudos.sql'`, which must come back empty, apart from the single scenario title described below. Then run the `lexicon-auditor`.
- [The archived spec keeps the scenario title "Activity made private after kudos"] → OpenSpec matches MODIFIED scenarios by name, so the delta keeps the old title and changes only its body. After archiving, retitle it "Activity made private after likes" directly in `openspec/specs/feed/activity-visibility/spec.md`.
- [A local stack's AppView and repos disagree after the migration] → Old kudos stay in PDS repos, and the AppView shows zero likes. Re-like in the app, or reset the stack with `scripts/dev.sh down`.

## Migration Plan

Land everything in one commit series, applied together: lexicons, then `core/lexicon`, the AppView with V17, the client, and docs. Nothing is deployed, so there is no rollout or rollback beyond reverting the commits. A dev database that has already run V17 needs a stack reset to go back.
