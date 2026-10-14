# Tasks

Each group renames one layer per the mapping table in design.md. Modules below the one being changed won't compile until their own group is done, so verify each group with its own module's tests.

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Rename `lexicons/app/activitytracker/feed/kudos.json` to `feed/like.json` (`git mv`), set `id` to `app.activitytracker.feed.like` and the description to "A like on an activity."; verify with `jq .id`.
- [x] 1.2 Rename `feed/getKudos.json` to `feed/getLikes.json`, set `id` to `app.activitytracker.feed.getLikes`, rename the output property `kudos` to `likes` (and in `required`), and reword the description ("Athletes who liked an activity, …"); verify with `jq`.
- [x] 1.3 In `feed/defs.json`, rename `kudosCount` to `likeCount` (property and `required`), rename `viewerState.kudos` to `like` ("URI of the viewer's like record, if any."), and reword the visibility description; verify `grep -i kudos lexicons -r` is empty.
- [x] 1.4 In `core/lexicon`, rename `Nsid.FEED_KUDOS`/`FEED_GET_KUDOS` to `FEED_LIKE`/`FEED_GET_LIKES` (values `feed.like`/`feed.getLikes`) and update `indexedCollections`; rename `KudosRecord` to `LikeRecord`, `ActivityView.kudosCount` to `likeCount`, `ActivityViewerState.kudos` to `like`, and `GetKudos` (and its `Output.kudos`) to `GetLikes`/`likes`. Reword their KDocs.
- [x] 1.5 Update `RecordSerializationTest` (`"likeCount":0`) and add a check that `LikeRecord` emits `$type` `app.activitytracker.feed.like`; verify `./gradlew :core-lexicon:jvmTest` passes.

## 2. AppView

- [x] 2.1 Add `server/appview/src/main/resources/db/migration/V17__rename_kudos_to_likes.sql`: `ALTER TABLE kudos RENAME TO likes`, rename `kudos_subject_idx` and `kudos_author_subject_idx` to `likes_subject_idx` and `likes_author_subject_idx`, then `DELETE FROM likes` with a comment explaining why (old `feed.kudos` rows are no longer indexed or deletable). Leave `V1`/`V3` untouched.
- [x] 2.2 Rename `db/Tables.kt` `object Kudos : Table("kudos")` to `object Likes : Table("likes")`, and update `index/Indexer.kt` (the `Nsid.FEED_LIKE` upsert branch, the `require` message "like on own activity", the delete branch, `deleteActor`).
- [x] 2.3 Update `api/Views.kt` (`views.likes`, `GetLikes`, `likeCounts`, `viewerLikes`, `likeCount`, `ActivityViewerState(like = …)`, comments), `api/Routes.kt` (`GetLikes.method` → `views.likes`) and the `api/TierAccess.kt` KDoc.
- [x] 2.4 Rename kudos to likes in `AppViewTest` and `ActivityVisibilityTest` (test names, fixtures, and JSON/assertions on `likeCount`, `viewer.like`, `getLikes`); verify `./gradlew :server-appview:test` passes, which also runs V17 on H2 in PostgreSQL mode.

## 3. Client data layer

- [x] 3.1 Rename `core/data/.../feed/LocalKudos.kt` to `LocalLikes.kt` (`git mv`), and rename the class to `LocalLikes` and `withKudosOverrides` to `withLikeOverrides`, with fields and KDocs.
- [x] 3.2 Update `ActivityRepository` (`toggleLike`, `localLikes`, `likeMutex`, `LikeRecord`, `Nsid.FEED_LIKE`, KDoc), `ActivityManager` (`likeCount = 0`, `viewer?.copy(like = null)`, KDoc) and `FeedRepository` (`likes(uri)` via `GetLikes`, `localLikes`, comments).
- [x] 3.3 Rename `LocalKudosTest` to `LocalLikesTest`, and rename kudos to likes in `ActivityRepositoryTest`, `ActivityVisibilityTest`, `FilteredFeedTest`, `OwnActivitiesByStartTest` and `RecentActivityTypesTest`; verify `./gradlew :core-data:jvmTest` passes.

## 4. Screens and presenters

- [x] 4.1 In `:app-screens`, rename `UiError.LoadKudos` to `LoadLikes`, `ActivityListEvent.ToggleKudos` to `ToggleLike`, `ActivityDetailEvent.ToggleKudos`/`ToggleKudosList` to `ToggleLike`/`ToggleLikesList`, `KudosList`/`kudosList` to `LikesList`/`likesList`, and the KDocs in `ActivityDetailScreen`, `FeedScreen` and `ProfileScreen`.
- [x] 4.2 In `:app-presenters`, update `ActivityListEvents`, `ActivityDetailPresenter` (`localLikes`, `showLikes`, `withLikeOverrides`, …), and the `SingleFlight` KDoc; also the `UseSingleFlight` KDoc in `detekt-rules`.
- [x] 4.3 Rename kudos to likes in the presenter tests (`ActivityDetailPresenterTest`, `ActivityDetailManageTest`, `EditActivityPresenterTest`, `FeedFilterPresenterTest`, `FeedPresenterTest`, `ImportLogbookPresenterTest`, `ProfilePresenterTest`, `SessionTestFixture`); verify `./gradlew :app-presenters:jvmTest :detekt-rules:test` passes.

## 5. UI

- [x] 5.1 In `composeApp` `strings.xml`, rename the keys and text per design.md: `like_count` plurals ("%1$d like" / "%1$d likes"), `like_content_description` ("Like"), `error_load_likes`, `detail_show_likes`/`detail_hide_likes`/`detail_like`/`detail_liked`/`detail_no_likes`, and "likes and comments" in the three delete-dialog strings.
- [x] 5.2 Update `ActivityDetailUi`, `ActivityCard`, `ActivityItems`, `Labels`, `AppButtons`, `AppIcon` and `Color.kt` (`LikeButton`, `onLike`, `rememberLikesList`, `Res.string.*`, comments); verify `./gradlew :composeApp:jvmTest :desktopApp:compileKotlin` passes. Then check in the desktop app (`hot-reload.md`) that a feed card shows "1 like"/"2 likes" and the detail screen's like button and list work.

## 6. Specs and docs

- [x] 6.1 Reword kudos to likes in `BACKLOG.md` (including `likes.subject_uri`), `README.md`, `.agents/rules/adaptive-ui.md`, `appview.md`, `compose-screens.md`, `data-and-auth.md`, and the `lexicon-change` skill (sibling example `feed/like.json`); verify `scripts/check-agent-sync.sh` passes.

## 7. Integration

- [x] 7.1 Run the `lexicon-auditor` on `app.activitytracker.feed.like`, `feed.getLikes` and `feed.defs`, and fix anything it reports.
- [x] 7.2 Verify `git grep -il kudos -- . ':!openspec/changes/archive' ':!openspec/changes/rename-kudos-to-likes' ':!server/appview/src/main/resources/db/migration/V1__init.sql' ':!server/appview/src/main/resources/db/migration/V3__drop_self_kudos.sql' ':!openspec/specs'` is empty; the main spec is fixed at archive (see Workflow follow-up).
- [x] 7.3 Run `./gradlew format` and then `./gradlew check`; verify both pass.

## Workflow follow-up

- Archive the change. Then, in `openspec/specs/feed/activity-visibility/spec.md`, retitle the scenario "Activity made private after kudos" to "Activity made private after likes" (the delta had to keep the old title to match), and verify `git grep -il kudos -- openspec/specs` is empty.
