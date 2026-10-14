# Proposal

## Why

Followers, Mutuals and Only-me activities can't take comments. Clients hide the comment field and count, and the AppView serves nothing for them. Likes on those activities shipped in `like-non-public-activities` by storing each one in the giver's private `interactions` space. Comments can use the same storage, but a comment is text written for a particular audience, so it can't simply follow the activity's visibility when the activity is widened, as a like does. A comment given while an activity was Followers-only would otherwise become public text the commenter never meant to publish.

## What Changes

- **Where a comment is stored.** A comment on a Public activity stays a `feed.comment` record in the commenter's repo. A comment on a non-public activity is the same record in the commenter's own `interactions` space, which only they and the AppView can read. The client chooses the place from the visibility the activity's view shows it, the same way as for likes. A comment never moves after it's written.
- **A comment records its audience.** `feed.comment` gains an optional `audience` field: the visibility of the activity as the commenter saw it when writing (Followers, Mutuals or Only me). An absent field means Public, so existing repo comments need nothing. The field is only ever set in the private `interactions` space, so it reveals nothing.
- **Who sees a comment.** A comment is counted and listed for a viewer only while all three of these hold:
  - the viewer may see the activity at its current tier;
  - the commenter may still see the activity at its current tier;
  - the viewer is in the comment's audience, judged against the activity's author.

  In effect, each comment is shown at the narrower of the activity's current tier and the audience it was written for:
  - **Narrowing:** making the activity narrower hides its comments from viewers who lose access. A Public comment stays in the commenter's public repo, as Public likes do.
  - **Widening:** making the activity wider doesn't widen comments written under a narrower tier.
- **The space declaration** adds `feed.comment` to its `collections`. Athletes must sign in again before they can comment on non-public activities. A session whose `space:` scope doesn't cover `feed.comment` is refused up front with `ScopeMissingError`, shown as "sign in again", as for likes. Comments on Public activities keep working without signing in again.
- **AppView sync.** The AppView indexes `feed.comment` from `interactions` spaces, as well as `feed.like`. The comments table gets an `origin` column and an `audience` column, so a space delete or a dropped space removes only that space's comments.
- **`acceptsComments` is removed** from the lexicon view, the AppView and the clients. The comment field and count show at every visibility. Comments given while an activity was Public, and hidden since it was narrowed, appear again under the rule above.

Out of scope, each a change of its own: deleting comments (`delete-comments`), letting an athlete hide comments on their activities (`hide-comments-on-own-activities`), paging comments (`paginate-comments`), and replies (`comment-replies`). Likes keep following the activity's visibility both ways.

## Capabilities

### New Capabilities

- `feed/comments`: where comments are stored by the activity's visibility, the audience a comment is written for, and who may see each comment.

### Modified Capabilities

- `feed/activity-visibility`: the "No comments on non-public activities" requirement is removed in favour of `feed/comments`.
- `spaces/appview-syncer`: "Only activity data is indexed from spaces" adds `feed.comment` from `interactions` spaces.

## Impact

- **Lexicons:**
  - `feed/comment.json`: the optional `audience` field.
  - `space/athlete.json`: `feed.comment` in `collections`, and its description.
  - `feed/defs.json`: the descriptions of `visibility` and `commentCount`.
- **core/lexicon:** `CommentRecord.audience`, and `ActivityView.acceptsComments` removed.
- **AppView:**
  - a Flyway migration (`V20`) adding `comments.origin` and `comments.audience`;
  - `Indexer` (`INTERACTION_COLLECTIONS`, comment writes and deletes from spaces, and `dropSpace`);
  - `Views` (comment counts and lists filtered by the three-part rule, built on `admits` in `TierAccess.kt`);
  - `acceptsComments` removed;
  - tests.
- **Client data:** `ActivityRepository.comment`, which places a comment, checks `requireSpacesFor`, and sets `audience`. `ActivityManager`'s view projection stops resetting `commentCount`.
- **UI:** `ActivityCard` and `ActivityDetailUi` stop checking `acceptsComments`. The detail screen shows "sign in again" for a refused comment.
- **Docs:** `BACKLOG.md` (remove "Comments"), `.agents/rules/appview.md` and `data-and-auth.md`, and `CLAUDE.md`'s data-flow paragraph.
- **Re-sign-in:** needed once to comment on non-public activities, because the space declaration changes.
