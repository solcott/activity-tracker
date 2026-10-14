# Design

## Context

See proposal.md for why. This change copies `like-non-public-activities` (archived 2026-10-14) and differs only where comment text needs it. The current state:

- **Likes already use the `interactions` space.**
  - `likePlaceOf` sends a like on a non-public activity to the giver's `interactions` space.
  - `requireSpacesFor(visibility, Nsid.FEED_LIKE)` refuses it up front when the session's `space:` grant doesn't cover `feed.like`.
  - The outbox ensures the space and announces it with `notifySpace`.
  - On the AppView, `Indexer.spaceCollections("interactions")` lets `feed.like` through, `SpaceSyncer.backfill` lists whatever that set allows, and `likes.origin` scopes deletes and `dropSpace`.
  - When serving, `admittedLikes()` joins likes to their activity, and `visibleTo(viewer)` plus `visibleTo(Likes.authorDid)` decide who sees each like.
- **Comments are repo-only and hidden on non-public activities.**
  - The `comments` table has no `origin`.
  - `Indexer.delete` removes a comment by URI alone.
  - `Views.activity` returns up to 500 comments, oldest first, but only when `acceptsComments(visibility)`. `hydrate` counts every comment with `countBySubject` and then zeroes the count for non-public rows.
  - `ActivityView.acceptsComments` hides the field and the count in `ActivityCard` and `ActivityDetailUi`.
  - `ActivityRepository.comment` returns early for non-public activities.
  - `ActivityManager.withVisibility` zeroes `commentCount` when an activity is narrowed.
- **`sendComment`** in `ActivityDetailPresenter` clears the draft before the write and drops any failure.
- **`TierAccess.admits`** treats tiers as nested: Public ⊃ Followers ⊃ Mutuals ⊃ Only me. Requiring two tiers at once therefore gives the narrower of them.

## Goals / Non-Goals

**Goals:**
- One rule for who sees a comment, applied identically to the count and the list.
- That rule needs nothing the AppView can't rebuild from records. A fresh AppView backfilling the spaces must reach the same answers.
- Reuse the like machinery: the place, the scope check, the outbox's space target, `origin`, and `dropSpace`.

**Non-Goals:**
- Deleting, hiding, paging and replies, each its own change (see proposal.md).
- Changing how likes are shown. They keep following the activity both ways.
- Moving or rewriting existing comments.

## Decisions

### 1. The audience is a field the client writes, not something the AppView infers

`feed.comment` gains an optional `audience` string, an open enum with the values of `actor.defs#visibility`. `ActivityRepository.comment` sets it to the view's `visibilityOrPublic` when that isn't Public, and leaves it out otherwise.

Alternatives considered:
- **The AppView records the activity's tier when it indexes the comment.** Rejected for two reasons. Indexing lags the write, so a visibility change in between records the wrong tier. And it can't be rebuilt: a new AppView backfilling would record the tier at backfill time.
- **Infer the audience from where the comment is stored.** The repo means Public, but a space only says "not Public", not which tier.
- **Store the comment in the matching tier space instead.** Rejected for the same reasons as for likes: other members could list it, it would need multi-writer sync, and it would be left behind when the activity moves.

The field leaks nothing. In a repo comment it's absent, and the `interactions` space is readable only by the commenter and the AppView. A repo comment that carries an `audience` from some other client is honoured too. It can only narrow who sees text that's already public.

### 2. Who sees a comment is checked at serve time, three ways

`admittedComments()` joins `comments` to `activities` on the subject, mirroring `admittedLikes()`. Every comment read applies all three checks:

```
visibleTo(viewer)                                             -- activity's current tier, for the viewer
and visibleTo(Comments.authorDid)                             -- current tier, for the commenter (per row)
and visibleTo(viewer, Comments.audience, Activities.authorDid) -- the audience, judged against the athlete
```

The third check uses the existing `visibleTo(viewer: Did?, visibility, author)` overload with the audience column as the visibility. A signed-out viewer passes only when the audience is `public`. The checks are nested, so their conjunction is "the narrower of the current tier and the audience".

`hydrate`'s `commentCount` and `activity()`'s list both go through `admittedComments()`, so they always agree. The count becomes a grouped count over that join, which replaces `countBySubject(Comments…)`. The per-row zeroing goes. `acceptsComments` is deleted from `TierAccess.kt`.

Alternative considered: capping at index time by storing `min(audience, tier)`. Rejected because the tier changes after indexing, and the stored row would then need rewriting on every visibility change.

### 3. The audience is normalised when indexed

The `comments.audience` column is `NOT NULL DEFAULT 'public'`. The Indexer stores `Visibility.known(record.audience)`, so an absent value becomes `public` and an unknown one becomes `self`. Serving then never meets a value `admits` doesn't handle, and an unknown value fails closed: only the athlete sees it, because `admits` always lets the author through.

### 4. `comments.origin`, with deletes scoped like likes

Migration `V20__comment_origin_audience.sql` adds `origin` (`DEFAULT 'repo'`) and `audience` (`DEFAULT 'public'`). It uses two `ALTER TABLE … ADD COLUMN` statements so it runs on H2 too.

- **Upserts** write `placement.origin` and the normalised audience.
- **Deletes** become `uri = ? AND origin = ?`. Today a delete matches the URI alone, so a repo delete of an rkey that lives in a space would remove the space copy.
- **`dropSpace`** gains `Comments.deleteWhere { origin eq placement.origin }`.

No `RecordShadows` are needed: a comment is never moved, so one URI never lives in two places. The Indexer gains `Nsid.FEED_COMMENT` in `INTERACTION_COLLECTIONS`. `SpaceSyncer.backfill` already iterates `spaceCollections(skey)`, so it needs no change.

### 5. Placement and refusal on the client

`likePlaceOf` is renamed `interactionPlaceOf`, and likes and comments both use it. `comment()`:

1. calls `requireSpacesFor(view visibility, Nsid.FEED_COMMENT)` before anything changes;
2. bumps `commentCount` optimistically;
3. queues `writer.put(FEED_COMMENT, rkey, …, space = interactionPlaceOf(visibility))`.

`sendComment` still clears the draft at once, so a double tap can't send twice. On failure it puts the text back if the field is still empty, and sets a `commentError`. That error is mapped like a like error, so `ScopeMissingError` becomes `UiError.Kind.SignInAgain`; the like-error mapping is generalised for both. `ActivityDetailScreen.State.Loaded` gains `commentError: UiError?`, and `ActivityDetailUi` shows it under the comment field.

### 6. The declaration and the view lexicon

- `space/athlete.json` adds `app.activitytracker.feed.comment` to `collections`, and its description says the AppView indexes `feed.like` and `feed.comment` from `interactions`.
- `feed/defs.json` drops "Unless it is public, commentCount is 0", and describes `commentCount` as the comments the viewer may see.
- `ActivityView.acceptsComments` is removed. `ActivityManager.withVisibility` stops zeroing `commentCount`; the AppView recounts on the next read, as it does for likes.

## Risks / Trade-offs

- **[Widened threads show gaps]** After a widening, new viewers don't see comments written for the narrower tier, so later comments may answer something they can't see. → It's inherent to option B. There are no replies yet. `comment-replies` defines how replies to hidden comments behave.
- **[Count queries cost more]** Each comment on a non-public activity now runs up to three correlated follow lookups. → This is bounded the same way as likes: a page of activities, and the follow indexes `visibleTo` already uses. Public activities with Public comments short-circuit on `visibility = 'public'`. Check with `EXPLAIN` on the local Postgres alongside the likes query.
- **[A stale view writes the wrong audience]** If the activity changed tier just before the comment, the audience reflects the old view. → Both directions are safe. A narrow audience on a now-Public activity just stays narrow. A Public comment on a now-Followers activity was written as public text anyway, and is shown only to the current tier.
- **[The AppView is the only reader]** Non-public comments are readable only through the AppView. → The same accepted trade-off as for likes. A new AppView admitted to `interactions` spaces rebuilds them, audience included, from the records.
- **[Signing in again]** Comments on non-public activities need the new declaration in the session's grant. → The refusal is up front, keeps the draft, and asks to sign in again. Public comments keep working.

## Migration Plan

1. Deploy the AppView. `V20` runs, and the existing comments become `origin = 'repo'` and `audience = 'public'`, which is correct, since every one so far was written on a Public activity. Comments indexed while their activity was Public, then hidden by narrowing, are shown again by the new rule to the current tier.
2. Publish the updated space declaration: `docker/scripts/publish-lexicons.sh` locally, through `scripts/dev.sh`.
3. Ship the clients. Athletes sign in again before commenting on non-public activities.

Rollback:
1. Revert the clients, which hides the field on non-public activities again.
2. Revert the AppView's serving to `acceptsComments`.
3. Leave `V20`'s columns in place, since migrations are append-only.

Comments already in `interactions` spaces stay there, unread.
