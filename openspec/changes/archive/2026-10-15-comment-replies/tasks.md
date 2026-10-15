# Tasks

## 1. Lexicons and the Kotlin mirror

- [x] 1.1 Add `#replyRef` (`root`, `parent`, both strong refs) and the optional `reply` field to `lexicons/app/activitytracker/feed/comment.json`, and update the `audience` description: a reply's audience is capped by its parent's, and a comment is stored by its audience. Verify the JSON parses and matches the `lexicon-change` checklist.
- [x] 1.2 In `feed/defs.json`, add `#threadView` (`uri`, `root?`, `replies`, `replyCount`, `repliesCursor?`) and the `root?`, `parent?` (strong refs) and `audience?` fields to `#commentView`. In `feed/getComments.json`, add the `root` param and the `threads` output, and describe the three modes and the `uris`+`root` `InvalidRequest` (design decision 4).
- [x] 1.3 Mirror the lexicon changes in `core/lexicon` (`Records.kt`, `Views.kt`, `Queries.kt`):
  - `ReplyRef` and `CommentRecord.reply: ReplyRef? = null`;
  - `ThreadView`, and the new `CommentView` fields;
  - `GetComments.Params.root` and `Output.threads`;
  - `Visibility.narrower(a, b)`.

  Extend `RecordSerializationTest` with a reply round-trip, a record without `reply` decoding to null, and `narrower` over every pair. Verify that `./gradlew :core-lexicon:jvmTest` passes.

## 2. AppView indexing

- [x] 2.1 Add `server/appview/src/main/resources/db/migration/V22__comment_threads.sql`:
  - `thread_uri`, backfilled from `uri` and then `NOT NULL`;
  - nullable `parent_uri`, and nullable `thread_cid` and `parent_cid` for the strong refs (design decision 2);
  - `comments_thread_idx (subject_uri, thread_uri, created_at)`.

  Add the columns to `Comments` in `Tables.kt`. Verify that `./gradlew :server-appview:test` migrates H2 cleanly.
- [x] 2.2 In `Indexer`, set `thread_uri` and `parent_uri` from `CommentRecord.reply`, and skip a reply whose `root` or `parent` isn't a `feed.comment` URI with an rkey. Add indexer tests for a reply, a reply to a reply (same thread), and a reply naming a `feed.like` (not indexed). Verify that `./gradlew :server-appview:test` passes.

## 3. AppView serving

- [x] 3.1 In `Views.kt`, left-join the thread's root in `admittedComments()`, and extend `commentsVisibleTo` so a reply also needs the viewer admitted to an indexed root's audience. Test the spec's `A reply's audience is capped by its parent's` scenarios, including the wider reply from another client, and that `hydrate`'s count follows. Verify that `./gradlew :server-appview:test` passes.
- [x] 3.2 Implement page mode as thread pages: group listed rows by `thread_uri`, keyset on `(MIN(created_at), thread_uri)`, and per thread return the root or a placeholder, the three oldest listed replies, `replyCount` and `repliesCursor`. Keep `comments` as the page's listed top-level comments. Put the tests in `CommentPagingTest`:
  - first, last and size-limited pages;
  - a reply arriving between pages;
  - the placeholder's place;
  - a deleted, a hidden, and an inaccessible root;
  - no thread when none of its replies are listed;
  - a root on another activity.

  Verify that `./gradlew :server-appview:test` passes.
- [x] 3.3 Add thread mode (`root`), paging a thread's replies oldest first, and make `uris` with `root` an `InvalidRequest`. Have lookup mode return replies with `root` and `parent`. Test paging 80 replies, `NotFound` for a hidden activity, and a reply looked up by URI. Verify that `./gradlew :server-appview:test` passes.
- [x] 3.4 Add a test that, for the athlete and for another viewer, the comment count equals the non-hidden comments and replies across every thread page and every reply page, with placeholders not counted (`The count matches the pages`). Verify that `./gradlew :server-appview:test` passes.
- [x] 3.5 Run the thread-page query against the docker stack's Postgres (`scripts/dev.sh`) with `EXPLAIN`. Record in this task whether it uses `comments_thread_idx`, and whether the per-thread reply fetch needs the window-function fallback (design decision 4).
  - Result (Postgres 17, 2026-10-14; one activity seeded with 2,000 threads and 11,002 comments in all, 40,000 on other activities, rolled back): the thread grouping doesn't use `comments_thread_idx`. It bitmap-scans `comments_subject_idx` for every row on the activity, sorts them by `thread_uri`, aggregates and keeps the top 30: 36 ms. That's inherent to placing threads by `MIN(created_at)` over listed rows, since `HAVING` can't seek, so its cost grows with the activity's comments, not the page. Worth revisiting (say, a stored per-thread minimum) only if activities get far more comments.
  - The per-thread reply fetch for a page of 30 threads (165 rows) uses `comments_thread_idx`, one index scan per thread, in under 1 ms, so it doesn't need the window-function fallback. Thread mode (`root`) also uses `comments_thread_idx`.
- [x] 3.6 Update `.agents/rules/appview.md` on threads, the root's audience cap, and the `GROUP BY` keyset, and check that `./gradlew :server-appview:test` still passes.

## 4. Client data

- [x] 4.1 In `Places.kt` and `ActivityRepository.comment`, take an optional `replyTo: CommentView`, compute the audience as the narrower of the activity's visibility and the parent's, and place it and check the scope by that audience. Build `ReplyRef` from `replyTo.root ?: replyTo` and `replyTo`. Add `root` to `SentComment`. Test in `core/data` jvmTest:
  - a Followers reply on a Public activity goes to the interactions space;
  - a Public reply on a Public activity goes to the repo;
  - a scope refusal writes nothing;
  - a reply to a reply keeps its root.

  Verify that `./gradlew :core-data:jvmTest` passes.
- [x] 4.2 Have `FeedRepository.comments` support thread pages and thread mode (`root`), applying `LocalComments` overlays to roots and replies alike. Extend `CommentPagesTest` and `LocalCommentsTest` for a deleted or hidden reply, a sent reply settling through a lookup, and the count delta including replies. Verify that `./gradlew :core-data:jvmTest` passes.
- [x] 4.3 Update `.agents/rules/data-and-auth.md`: a comment is placed by its audience, and replies are capped by their parent. Verify the rule file matches the code paths it names.

## 5. Screen, presenter and UI

- [x] 5.1 In `ActivityDetailScreen.kt`, add `CommentThread`, change `CommentsPaging.items` to threads, and add `replyingTo` and the `ReplyTo`, `CancelReply` and `LoadMoreReplies` events. Verify that `./gradlew :app-screens:compileKotlinJvm composeStabilityCheck` passes.
- [x] 5.2 In `app/presenters/.../detail/Comments.kt` and `ActivityDetailPresenter`, keep thread pages and per-thread reply pages, and derive the shown threads with overlays, local placeholders, and sent replies placed at the end of their thread. `SendComment` sends a reply while `replyingTo` is set, clearing it on success and keeping it on refusal. `LoadMoreReplies` runs under `SingleFlight`. Extend `ActivityDetailCommentsTest` with the scenarios from:
  - `Reply to a comment`, including cancel;
  - `A thread comes with its first replies`;
  - `The rest of a thread's replies are listed in pages`, including failure and retry;
  - `A sent comment shows at once`, the two sent-reply scenarios.

  Verify that `./gradlew :app-presenters:jvmTest` passes.
- [x] 5.3 In `CommentsUi.kt`:
  - render threads as root or placeholder rows, indented replies, sent replies, and a "View n more replies" or retry row;
  - add a "Replying to @handle" bar with cancel to `CommentField`;
  - add Reply to `commentActions` under the spec's conditions (none on hidden, placeholder or sent comments);
  - make `LoadMoreNearTheEnd` count threads.

  Verify on the desktop app with Compose Hot Reload: a thread with more than three replies, view more, reply to a reply, and a placeholder. Take screenshots.
  - Result (2026-10-15, as alice on her public Ride, seeded with bob's comment and 5 replies from carol, and dave's deleted comment with carol's reply): the thread showed 3 indented replies and "View 2 more replies", which loaded replies 4 and 5 and went away; "Comment unavailable" showed with carol's reply under it; Reply on carol's 2nd reply showed "Replying to @carol.test", and sending put alice's reply at the end of bob's thread with no Reply on it, which the AppView indexed with carol's reply as parent and bob's as root (count 8 → 9). Checked through the semantic tree: `take_screenshot` returned only black frames on this machine (screen recording not permitted), so there are no screenshots.

## 6. Integration

- [x] 6.1 Run `./gradlew check`, and run the `lexicon-auditor` agent on `feed.comment`, `feed.defs` and `feed.getComments`. Verify both are clean.
  - Result (2026-10-15): `check` passes. The lexicon auditor found the layers consistent; its one note, V22's header saying `feed.comment#reply` rather than `#replyRef`, names the record's `reply` field and stays, since V22 is already applied to the local Postgres. To satisfy detekt, the reply presenter tests are in `ActivityDetailRepliesTest` and the AppView's thread tests in `CommentThreadsTest` (both on shared bases), and the comment queries moved to `api/CommentQueries.kt`.
- [x] 6.2 End to end on the local stack, with alice, bob and carol seeded:
  - bob comments on alice's Followers activity, and carol replies;
  - bob deletes his comment, and alice and carol see the placeholder with carol's reply;
  - carol replies to a Followers comment after alice makes the activity Public, and the reply lands in carol's interactions space, not her repo.

  Verify each against the AppView's `getComments` and the PDS records.
  - Result (2026-10-15, on alice's Followers "Comments e2e"): bob's comment and carol's reply, written to their interactions spaces, were listed as one thread for alice, bob and carol, and dave got `NotFound`. Once bob's delete synced, alice and carol got the placeholder (only `uri`, `replies` and `replyCount`) with carol's reply. For the last scenario the desktop app, signed in as alice, made the activity Public and replied to carol's Followers reply. The client code is the same for any replier. The reply went to alice's interactions space with audience `followers` (none in her repo), with carol's reply as parent and bob's deleted comment as root. Alice and carol saw it, and dave, now able to see the activity, saw neither reply.

## Workflow follow-up

- Run `/opsx:archive` once the tasks are done and reviewed.
