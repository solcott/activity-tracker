# Tasks

Starts after `rename-kudos-to-likes` has landed: every name below (`feed.like`, likes table, `getLikes`, `likeCount`, `viewer.like`, `toggleLike`, `LocalLikes`) is the renamed one.

## 1. Lexicon and the `interactions` key

- [x] 1.1 In `lexicons/app/activitytracker/space/athlete.json`, add `app.activitytracker.feed.like` to `collections` and describe the `interactions` key (only the giver and the AppView read it; the AppView indexes only likes from it). Update the `likeCount`/`viewer.like` descriptions in `feed/defs.json`, which today say non-public activities have none, and `space/notifySpace.json` if it names the tiers. Verify with the `lexicon-auditor` agent and `./gradlew :core-lexicon:jvmTest`.
- [x] 1.2 Add `AthleteSpace.INTERACTIONS` in `core/lexicon` next to the tier keys. Keep it out of every "tiers" list (`AthleteSpaces.TIERS`, `SpaceSyncer.TIERS`), so profile details and activity streams never touch it. Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. AppView: access, sync and indexing

- [x] 2.1 Make `SpaceAccess` admit the AppView's own DID for `read` on `interactions`, and nobody else. Writes stay denied. Verify with new `SpaceAccess` tests for the managing-app scenarios "Interactions space", "AppView reads the interactions space", "AppView and an unknown tier" and "Unknown tier" (`./gradlew :server-appview:test`).
- [x] 2.2 Add a Flyway migration adding `origin` to the likes table (not null, defaulting to the repo origin), plus the column in `Tables.kt`. Use H2-portable SQL and never edit committed migrations. Verify that `./gradlew :server-appview:test` migrates on H2 and the local Postgres starts cleanly (`scripts/dev.sh`).
- [x] 2.3 Replace `Indexer.SPACE_COLLECTIONS` with collections allowed per space key: tiers allow activities and streams, `interactions` allows `feed.like`. Make like upserts and deletes carry the placement's origin, and a space delete remove only that origin's row. Extend `dropSpace` to likes. Keep rejecting self-likes. Verify with indexer tests for "Like in a tier space ignored", "Activity in an interactions space ignored", "Like deleted", "Interactions space deleted" (repo likes survive) and "Duplicate notification".
- [x] 2.4 Add `interactions` to `SpaceSyncer.isSyncable`, and make `backfill` list the collections allowed for the space's key. `notifySpace` then accepts it through `isSyncable`; keep the authority check. Verify with `SpaceSyncer` tests (with `FakeSpacesPds`) for "Athlete announces their interactions space" and "Someone else's interactions space".

## 3. AppView: serve-time like visibility

- [x] 3.1 Give `visibleTo` (`TierAccess.kt`) a form whose viewer is a column, correlated per row, alongside the constant-DID form. Remove `acceptsInteractions` for likes, but keep it, or an equivalent, for comments. Verify that existing `ActivityVisibilityTest` cases still pass.
- [x] 3.2 In `Views.hydrate`, count a like only when its giver passes `visibleTo` against the activity's tier and author, skipping the check for Public rows, and report `viewer.like` only while the viewer is admitted. In `getLikes`, return nothing to a viewer who may not see the activity, and list only admitted givers with keyset paging intact. Leave comments gated on Public. Verify with `ActivityVisibilityTest` cases for each scenario in "Who sees a like" (narrowed, widened, giver unfollows and refollows, one-way follow on Mutuals, follow indexed after the like, viewer who can't see) and "No comments on non-public activities".
- [x] 3.3 Check the count query's plan on Postgres with seeded follows and likes (`EXPLAIN ANALYZE` on a page of Followers activities). Verify that it uses the follow indexes and record the result in the PR description.

## 4. Client: like and unlike at every visibility

- [x] 4.1 Make `AthleteSpaces.ensure` handle `interactions` (create with the AppView as managing app for reads, repoint a stale one, announce with `notifySpace`), without adding it to `TIERS`. Verify with `AthleteSpaces` jvm tests for create, already-exists and repoint.
- [x] 4.2 Make `ActivityRepository.toggleLike` queue the like through the outbox to the repo for a Public view and to the `interactions` space otherwise, refusing early with `ScopeMissingError` (`requireSpacesFor`) when the session can't use spaces. Make unlike queue idempotent deletes of the rkey from both places, treating `SpaceNotFound` as done. Drop the "nobody can like a non-public one" guard. Verify with `core/data` jvm tests for each place, a stale view, unlike from both, and a session without the scope.
- [x] 4.3 Stop `ActivityManager`'s view projection from zeroing `likeCount` and `viewer.like` for non-public visibilities (comments stay zeroed), and check that `LocalLikes` overlays work unchanged on non-public views. Verify with `core/data` jvm tests.
- [x] 4.4 Show the like action and count on non-public activities in the feed, detail and likes list, and keep comment actions hidden there. Verify with presenter tests in `:app-presenters` (`FeedPresenterTest`, `ActivityDetailPresenterTest`) and on the desktop app with Compose Hot Reload, liking and unliking a Followers activity.
- [x] 4.5 Update `.agents/rules/data-and-auth.md` (likes' placement and unlike-from-both) and `CLAUDE.md`'s data-flow paragraph (the AppView also syncs `interactions` spaces). Verify with `scripts/check-agent-sync.sh`.

## 5. End to end and backlog

- [x] 5.1 On the local stack with the tunnel (`scripts/dev.sh --seed alice`, plus bob and carol): republish the declaration, sign bob in again, and have bob, alice's effective follower, like her Followers activity. Verify that bob's repo has no `feed.like`, his `interactions` space has one, and alice and bob see the count while carol, a non-follower, can't. Then widen the activity to Public and verify that carol sees bob's like.
- [x] 5.2 Rewrite `BACKLOG.md`: delete "Kudos and comments on non-public activities" (now "Likes and comments…" after the rename) and its bullet under "Private activities: what's left". Add a "Comments" entry: build `getComments` and the comment UI for every visibility, storing comments on non-public activities in the giver's `interactions` space and serving them by the same serve-time rule. Verify by reading the result.
- [x] 5.3 Run `./gradlew check`, and verify that it passes.

## Workflow follow-up

- Archive the change after `rename-kudos-to-likes` is archived, so the REMOVED requirement's renamed header exists in the main spec.
