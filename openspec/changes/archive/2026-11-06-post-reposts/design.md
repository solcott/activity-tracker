# Design

## Context

See proposal.md for why. What already exists that shapes the approach:

- **Likes on posts are the model.** A `feed.like` is indexed into `likes` (`Indexer.upsert`'s `FEED_LIKE` branch) from repos and `interactions` spaces with its `origin`. It is served through `Subject.POST` (`api/Subject.kt`): `admittedLikes(subject)` joins it to `posts`, and `likesShownTo(viewer, subject)` applies the giver's tier check and `accountShownTo` (blocks and labels). `PostCounts` (`api/PostCounts.kt`) counts a page of posts' likes and looks up the viewer's.
- **Interaction collections are listed in several places**, and a new one joins them all:
  - `Nsid.indexedCollections`;
  - `Indexer`'s `INTERACTION_COLLECTIONS` and `HELD_ROWS`;
  - `space/athlete.json` and its mirror `AthleteSpaceDeclaration`;
  - the client's scope list (`AppCollections`), `GrantStatus`'s `PrivateInteractions` and `RejectedChanges`.
- **The timeline merges sources in memory.** `Views.cardedTimeline` keyset-pages activities and posts on `(sort_at, uri) DESC`, each limited to one page, merges import cards, and sorts. The cursor is `"<position>::<key>"`. A post's key is its URI, and posts come from `postRows(viewer)` limited to the viewer and `effectivelyFollowedBy`, less muted authors.
- **`getTimeline`'s feed is a bare open union** of `activityView`, `importCardView` and `postView`. It has no item wrapper. The client mirrors it as `TimelineItem` with `OpenUnionSerializer`, and caches items in `FeedItemEntity` rows under a per-kind key prefix (`post:` for posts).
- **The client's like toggle** (`ActivityRepository.toggleLike(InteractionSubject)`) places the record with `interactionPlaceOf`, refuses up front without the space scope, updates the cached counts, and deletes from both places on undo. `LocalLikes` lays the viewer's toggles over views until Jetstream catches up. `rememberLikesList` (`:app-common-presenter`) loads the likers sheet the post page shows.

## Goals / Non-Goals

**Goals:**
- Reposts reuse the like rules for posts: the same giver check, the same block and label filters, and the same placement and undo.
- The timeline shows a post once with no state kept per viewer. Its position is computed from indexed rows at read time.
- Older clients keep working. They skip what they don't know.

**Non-Goals:**
- Inserting the viewer's own repost into their timeline at once. The card shows it reposted at once; the timeline item arrives on refresh.
- Paging the timeline on the client beyond the first page, which it doesn't do today.

## Decisions

### 1. Reposts get their own table, indexed like likes
V59 adds `reposts`:
- the columns `uri`, `author_did`, `subject_uri`, `subject_cid`, `created_at`, `sort_at`, `indexed_at` and `origin`;
- an index on `(subject_uri, sort_at DESC)`, for counts, lists and the "latest repost" checks;
- an index on `(author_did, sort_at DESC, uri DESC)`, for the timeline.

`sort_at` is the record's `createdAt`, capped at index time as `FeedPosition` caps a post's. The `FEED_REPOST` branch in `Indexer.upsert` requires the subject's collection to be `feed.post` and its authority not to be the reposter, so it skips anything else. It joins `HELD_ROWS` and `INTERACTION_COLLECTIONS`, so account deletion, space drops and record deletes reach it with no new paths.

The AppView doesn't check at index time whether the reposter may see the post. The repost can arrive before the post, the follow or the approval it depends on. The giver check at read time (Decision 2) covers it, as it does for likes.

*Alternative considered:* a `kind` column on `likes`. Likes and reposts would share the row shape, but every like query would gain a filter it could forget, and the timeline needs `sort_at`, which likes don't have.

### 2. One "shown" rule, plus a timeline rule on top
`api/Reposts.kt` holds two rules:
- `admittedReposts()`: `reposts` joined to `posts` on the subject URI.
- `repostsShownTo(viewer)`, which requires all of:
  - `Subject.POST.visibleTo(Reposts.authorDid)`, the reposter's tier check, which also excludes a block between reposter and author;
  - `accountShownTo(viewer, Reposts.authorDid)`;
  - `postRows(viewer)` on the joined post.

Counts, `getReposts` and the viewer's repost all use `repostsShownTo`, so a count never includes a reposter the list leaves out. It applies no mutes, as the like rule doesn't.

`timelineReposts(viewer)` adds:
- the reposter is the viewer or in `effectivelyFollowedBy`;
- `notMuted(viewer, Reposts.authorDid)`;
- `notMuted(viewer, Posts.authorDid)`.

### 3. The timeline places a post at its latest position with two keyset sources
`cardedTimeline`'s post source becomes two sources, each a keyset query limited to one page:
- **Originals:** today's post rows, plus a `NOT EXISTS` over `timelineReposts` of the same post with a later `sort_at`. A post with a later repost in this timeline is left to that repost.
- **Reposts:** `timelineReposts` rows that are the latest of their post in this timeline, by a `NOT EXISTS` over a later one (ties broken by URI). A further `NOT EXISTS` drops them when the post itself is in the timeline's post rows at the same or a later position, so a tie goes to the original. Their key is the repost's URI.

A repost's URI is in a collection of its own, so it never equals an activity, post or card key, and the order stays total. The cursor format doesn't change. Every page computes each post's single position the same way, so a post lands on one page.

For the repost entries on a page, one grouped query counts the other distinct reposters in `timelineReposts`, which gives "and N others".

*Alternative considered:* one grouped subquery, a `UNION ALL` of post positions and repost positions with `MAX` per post, keyset-paged on the result. It reads more simply. But H2 can't use `reposts`' or `posts`' indexes through a `UNION` in a subquery (the same reason `post-interactions` rejected it for counts), and the grouped keyset would scan every followed repost on each page.

### 4. Lexicon shapes
- `feed/repost.json`: a record with a `subject` strongRef and `createdAt`, key `tid`. Its description says the subject is a `feed.post`, and where each audience stores it.
- `feed/defs.json` gains these:

  | Def | Holds |
  |---|---|
  | `#repostedPostView` | `post` (a `#postView`) and `reason` (a `#reasonRepost`) |
  | `#reasonRepost` | `by` (a `profileViewBasic`), `others` (an integer, default 0) and `createdAt` (the repost's position) |
  | `postView` | `repostCount` (default 0) |
  | `#postViewerState` | `repost` (an at-uri) |

- `getTimeline`'s union gains `#repostedPostView`.
- `feed/getReposts.json` is shaped like `getLikes`: `uri`, `limit` and `cursor` in, `{ uri, cursor?, reposts: profileViewBasic[] }` out, with `NotFound`.

A separate union member rather than a `reason` on `postView`, because the reason belongs to the timeline item, not the post: `getPost` and `getAuthorPosts` would always leave it empty.

### 5. Post views count reposts in the same pass
`PostCounts` gains a grouped count of distinct reposters per post over `admittedReposts()` and `repostsShownTo`, plus a lookup of the viewer's reposts. That's five queries per page instead of three. `Views.reposts` serves `getReposts` with `requireVisiblePost` first, so the refusal is the same as `getLikes`'.

### 6. Client: a repost toggle beside the like toggle
- **`core/lexicon`:** `RepostRecord`, `Nsid.FEED_REPOST` and `FEED_GET_REPOSTS`, `GetReposts`, `PostView.repostCount`, `PostViewerState.repost`, and `TimelineItem.Repost(RepostedPostView)` with its serializer member.
- **`ActivityRepository.toggleRepost(post: PostView)`** mirrors `toggleLike`:
  - it does nothing on the viewer's own post;
  - `requireSpacesFor(visibility, FEED_REPOST)` refuses it before anything changes;
  - it places the record with `interactionPlaceOf` and updates the cached post through `updateCachedPost`;
  - undo deletes the rkey from both places.
- **`LocalReposts`** lays the viewer's toggles over posts, like `LocalLikes`. The two share the delta helper (`likeDelta`, renamed `toggleDelta`).
- **Feed cache:** repost items are cached under a `repost:` key prefix, keyed by the post URI with the reason in the row's JSON. `updateCachedPost` rewrites rows under both prefixes, so a like or repost on a reposted item updates it too.
- **`FeedRepository.reposts(uri)`** reads `getReposts`. `rememberLikesList` is generalized over its loader, and the post page uses it for both lists.
- **UI:** `PostEvent` gains `Repost` and `OpenReposter`, handled in `rememberPostEventSink`. `PostCard` takes an optional reason, shown as a "Reposted by" header above the author, plus the repost button and count. The post page adds `PostPageEvent.ToggleRepostsList` and the reposters sheet.
- **Scope lists:** `AppCollections`, `AthleteSpaceDeclaration` and `GrantStatus` (`PrivateInteractions`) gain `FEED_REPOST`, and `RejectedChanges` gains a `Repost` kind. A session from before then is asked to reconsent, as for likes.

*Alternative considered:* folding reposts into `toggleLike` with a kind flag. The two write different collections and update different counts, so the flag would branch on every line. Keeping them as two short functions over the shared placement helpers is clearer.

## Risks / Trade-offs

- **[Risk] Correlated `NOT EXISTS` checks on every timeline page.** → Both indexes in V59 cover the lookups. The end-to-end check runs `EXPLAIN` for a timeline page on the local Postgres and confirms both are used.
- **[Risk] A post could appear twice while someone pages.** If a repost is undone between pages, the post moves down, below the cursor, and can show again on a later page. A new repost moves it up, above the cursor, so it can't duplicate. → Accepted: the client loads only the first page, and refresh rebuilds it.
- **[Trade-off] Older clients lose a reposted post they would have seen as an original.** They skip `#repostedPostView` as unknown, and dedupe has removed the original. They see it again on the author's profile and on the post page.
- **[Trade-off] The viewer's own new repost doesn't appear in their timeline until refresh.** The card's state and count change at once, which matches likes.
- **[Risk] A repost could leak the existence of a post to a non-follower.** → `repostsShownTo` requires `postRows(viewer)`, and the `feed/reposts` scenarios test a follower of the reposter outside the post's audience.

## Migration Plan

- V59 adds `reposts` and its two indexes. There's no backfill.
- The space declaration (`space/athlete.json`) gains `feed.repost` and is republished (`docker/scripts/publish-lexicons.sh` locally). Interactions spaces accept it once their declaration is current, as for earlier additions.
- Deploy the AppView first. Older clients ignore `repostCount`, the viewer repost and `#repostedPostView`.
- Rollback: an older AppView doesn't index `feed.repost` and serves no repost items. Repost records stay in repos and spaces, harmlessly.
