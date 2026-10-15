# Tasks

## 1. Lexicons and core/lexicon

- [x] 1.1 Add `lexicons/app/activitytracker/feed/commentHide.json`. It's a record with `key: "any"` and `subject` (at-uri, the comment) and `createdAt`, and its description gives the rkey rule. Add an optional `hidden` to `feed/defs.json#commentView`, and say in `commentCount`'s description that hidden comments aren't counted. Verify that the JSON parses (`python3 -m json.tool`) and that `lexicon-auditor` in 5.2 finds no mismatches.
- [x] 1.2 Add `app.activitytracker.feed.commentHide` to `space/athlete.json` `collections`, and update its description, which says what the AppView indexes from `interactions`. Verify that `docker/scripts/publish-lexicons.sh` publishes it on the local stack.
- [x] 1.3 In `core/lexicon`, add:
  - `Nsid.FEED_COMMENT_HIDE`, included in `indexedCollections`;
  - `CommentHideRecord(subject: AtUri, createdAt)` with `$type`;
  - `CommentView.hidden: Boolean? = null`.

  Verify with round-trip cases in `RecordSerializationTest` (`./gradlew :core-lexicon:jvmTest`).
- [x] 1.4 In `core/atproto`, add a public `suspend fun commentHideRkey(comment: AtUri): String` (suspending, because WebCrypto on the web has no blocking hash). It takes the first 20 bytes of SHA-256 over the URI string and encodes them as lowercase unpadded base32. Verify with a jvm test that it's 32 characters, a valid record key, stable for a fixed URI, and different for two comments (`./gradlew :core-atproto:jvmTest`).

## 2. AppView

- [x] 2.1 Add the `V21__comment_hides.sql` migration and `CommentHides` in `Tables.kt`. The table has `uri`, `origin`, `author_did`, `comment_uri`, `created_at` and `indexed_at`, a primary key of `(uri, origin)`, and an index on `(comment_uri, author_did)`, all in H2-portable SQL. Verify that `MigrationVersionsTest` passes.
- [x] 2.2 In the `Indexer`:
  - upsert `FEED_COMMENT_HIDE` with `placement.origin`, skipping it (`IllegalArgumentException`) when the subject isn't a `feed.comment` URI or `uri.rkey != commentHideRkey(subject)`;
  - delete by `(uri, origin)`;
  - add it to `INTERACTION_COLLECTIONS`;
  - clear it in `dropSpace` (by origin) and in `deleteActor` (by author).

  Verify with `AppViewTest` cases: a Jetstream hide is stored, a wrong-rkey hide is ignored, and a delete from the other origin leaves the row.
- [x] 2.3 In `Views`, add the `hiddenByAthlete` predicate. Exclude hidden comments from `hydrate`'s grouped `commentCount` for everyone. List them in `activity()` only for the activity's author and the commenter, with `hidden = true`. Verify with `AppViewTest`, against the `feed/comments` scenarios:
  - the athlete's, the commenter's, another viewer's and a signed-out viewer's view;
  - a stranger's hide changes nothing;
  - a hide indexed before its comment;
  - a hide in both places, with one place deleted;
  - unhiding brings the comment back;
  - the count matches the list's comments that aren't hidden.
- [x] 2.4 In `SpaceSyncTest`, cover the `spaces/appview-syncer` scenarios:
  - a hide put in the `interactions` space;
  - a hide deleted from the space;
  - a repo delete of the same rkey leaves it;
  - dropping the space drops its hides only;
  - backfill indexes the hides;
  - a hide in a `followers` space is ignored.

  Verify with `./gradlew :server-appview:test`.
- [x] 2.5 Run `EXPLAIN` on the comment count and list queries against the local Postgres (`scripts/dev.sh`), and confirm that the hide lookup uses the `(comment_uri, author_did)` index. Record the outcome in the commit message.
- [x] 2.6 Update `.agents/rules/appview.md`. Describe `comment_hides`, the serve-time author check, and that any future comment query (`paginate-comments`, `comment-replies`) must apply `hiddenByAthlete` for both the count and the list. Verify with `scripts/check-agent-sync.sh`.

## 3. Client data

- [x] 3.1 Add `LocalCommentHides` (comment URI → hidden) to `FeedRepository`. Apply it in `activity()` after `deletes`, setting `hidden` and adjusting `commentCount` only by the difference from the AppView's answer. Verify with a `LocalCommentHidesTest` (jvm):
  - a local hide the AppView hasn't seen lowers the count once;
  - one it has seen doesn't lower it again;
  - an unhide raises the count only while the AppView still marks the comment hidden;
  - a deleted comment isn't counted back in.
- [x] 3.2 Add `ActivityRepository.setCommentHidden(activity, comment, hidden)` as in design §2 (one function for both, which keeps detekt's `TooManyFunctions` limit):
  - they return early unless the activity is the session's and the comment isn't;
  - hiding calls `requireSpacesFor(visibility, FEED_COMMENT_HIDE)` before any change;
  - hiding puts to `interactionPlaceOf` at `commentHideRkey`;
  - unhiding deletes from the repo, and from `interactions` only when `canWriteToSpaces(FEED_COMMENT_HIDE)`;
  - both update the overlay and the cached count.

  Verify with `CommentHidePlacesTest` cases (on `FakeAthletePds`, the fake PDS with spaces split out of `ActivityVisibilityTest`):
  - the repo place for Public, and the space for Followers;
  - refusal without the scope writes nothing and leaves the count;
  - unhide queues both deletes, or repo-only without the scope;
  - a hide repeated twice queues one rkey;
  - someone else's activity writes nothing.
- [x] 3.3 Update `.agents/rules/data-and-auth.md` (the interactions bullet) with the placement of hides, the derived rkey, unhiding from both places, and `LocalCommentHides`. Verify with `scripts/check-agent-sync.sh`.

## 4. Screens, presenter and UI

- [x] 4.1 In `:app-screens`, add `ActivityDetailEvent.HideComment(uri)` and `UnhideComment(uri)`, and `UiError.Kind.HideComment` and `UnhideComment`. Verify with `./gradlew composeStabilityCheck`.
- [x] 4.2 In `CommentActions`, handle hiding and unhiding as in design §6:
  - flip `hidden` optimistically;
  - use `flights.launch(uri)`;
  - on failure, revert and set `commentError`, using `interactionErrorOf` (for `SignInAgain`) or the new kinds.

  Verify with `ActivityDetailPresenterTest` cases:
  - hide marks the comment;
  - a double hide calls the repository once;
  - a scope refusal reverts and reports `SignInAgain`;
  - another failure reverts and reports `HideComment`;
  - unhide clears the mark.
- [x] 4.3 In `composeApp`, make `CommentMenu` take its items:
  - Delete for own comments;
  - Hide or Unhide for others' comments on the viewer's own activity;
  - no menu when there are no items.

  Render hidden comments dimmed, with "Hidden" for the athlete and "Hidden by <athlete>" for the commenter. Count `comments.count { it.hidden != true }` in `ActivityDetailUi`. Add the strings and the error labels in `Labels.kt`. Verify on the desktop app with hot reload, using the MCP semantic tree and a screenshot, on a seeded activity with a hidden comment.
- [x] 4.4 Run `./gradlew check` and fix any formatting, detekt (`UseAdaptiveComponents`, `UseSingleFlight`) or lint findings. Verify that it passes.

## 5. Integration

- [x] 5.1 On the local stack, with two seeded accounts, alice and bob:
  - bob comments on alice's Public activity and on her Followers activity, and alice hides both comments;
  - check that bob sees each comment marked as hidden, that a third account sees neither, and that the counts drop;
  - alice unhides the comments, and the counts and lists return.

  Verify with the `local-stack` skill's smoke steps and the AppView's `getActivity` for each viewer.
- [x] 5.2 Run the `lexicon-auditor` agent on `feed.commentHide`, `feed.defs#commentView` and `space.athlete`, and confirm that it reports no mismatches.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, then sync the `feed/comments` and `spaces/appview-syncer` specs.
- Propose muting or blocking commenters as its own change.
