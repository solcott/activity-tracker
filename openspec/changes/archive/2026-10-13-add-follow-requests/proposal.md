# Proposal

## Why

Anyone can follow an athlete. Following is a record in the follower's own repo, and the athlete is never asked. Today that's harmless, because the only tier between public and Only me is Mutuals, and Mutuals already needs the athlete to follow back. But athletes have no way to share data with a wider group they choose: followers they have approved. This change lets an athlete opt in to approving followers, and adds a **Followers** visibility tier that only effective followers can read. A pending or declined follower then can't see Followers-only data.

## What Changes

- **Opt-in follow policy.** `actor.profile` gains `followPolicy` (`open`, the default, or `approve`). With `open`, follows work as they do today.
- **Effective follows.** With `approve`, a follow counts only once the athlete has approved it. An approval is a new public record in the athlete's repo, `app.activitytracker.graph.followApproval`, keyed by the follower's DID. Everything the AppView derives from follows uses effective follows only: timeline, follower and follow counts, follower and follow lists, mutuals, and space access.
- **Follow requests inbox.** A new `graph.getFollowRequests` query lists the follows to the signed-in athlete that are waiting for approval. Their own profile shows a **Follow requests** entry with a count. Each request can be approved or declined.
- **Private declines with a Declined list.** Declining writes an `app.activitytracker.graph.followDecline` record to the athlete's own `self` tier space, so no one else ever learns of it. The client hides declined accounts from the inbox and lists them under **Declined**, where the athlete can still approve them. The declined follower keeps seeing "Requested".
- **Requester state.** `actor.defs#profileView` exposes the athlete's `followPolicy`, and `#viewerState` gains `followRequested`. The follow button then reads **Requested** while a follow is pending, and tapping it cancels the request (deletes the follow record).
- **Turning approval on keeps current followers.** The client writes approvals for everyone who already follows the athlete before it saves `followPolicy: approve`. Turning approval off makes pending follows effective at once. Approvals and declines stay in place, so turning approval back on loses nothing.
- **Remove follower.** With `approve`, the athlete can remove a follower from their own followers list. This deletes the approval, and the follower goes back to pending.
- **Followers visibility tier.** `followers` joins `public`, `mutuals` and `self` as a visibility value and as an `app.activitytracker.space.athlete` key. `checkUserAccess` admits a user to an athlete's `followers` space when the user effectively follows them. Mutuals are effective follows in both directions. Profile details and activity streams can be set to Followers. The picker always offers Followers and notes, when approval is off, that anyone who follows can see it.
- **BREAKING (sessions):** `followDecline` joins the `space.athlete` declaration's `collections`. Every session must sign in again once before it can write a decline. The existing sign-in-again prompt covers this.
- **Out of scope:** visibility for activities themselves. Activities stay public records, so a pending follower can still read them. Making activities non-public is a later, separate change: it needs activities in tier spaces, a timeline that doesn't come from the AppView, and a design for kudos and comments on hidden activities.

## Capabilities

### New Capabilities
- `graph/follow-requests`: The opt-in follow policy, what makes a follow effective, approving, declining and removing followers, the requests inbox and Declined list, and how the requester sees a pending follow.

### Modified Capabilities
- `spaces/managing-app`: "Read access by tier" adds the `followers` tier and decides mutuals by effective follows in both directions.
- `profile/private-details`: Details can also be stored in, and read from, a `followers` tier space. Followers see Followers details.

## Impact

- **Lexicons:**
  - New: `graph/followApproval.json` (a public record), `graph/followDecline.json` (a record that lives only in the `self` space), `graph/getFollowRequests.json` (a query).
  - Changed: `actor/profile.json` (`followPolicy`), `actor/defs.json` (`#profileView.followPolicy`, `#viewerState.followRequested`, `followers` in `#visibility`), `feed/activityStreams.json` (`followers` in `visibility`), `space/athlete.json` (adds the `followDecline` collection).
- **`core/lexicon`:** the new records and query, `Nsid.GRAPH_FOLLOW_APPROVAL` (indexed) and `Nsid.GRAPH_FOLLOW_DECLINE` (not indexed), `Visibility.FOLLOWERS`/`AthleteSpace.FOLLOWERS`, `FollowPolicy`, and an `ApplyWrites` descriptor for batched approvals.
- **AppView:**
  - Indexer: approvals, plus `follow_policy` on actors.
  - New Flyway migration: `follow_approvals` table and `actors.follow_policy`.
  - An effective-follow predicate used by `Views` (timeline, profile counts and viewer state), `GraphViews`, `SpaceAccess` and `ActivityStreamsViews`.
  - New `getFollowRequests` route.
- **`core/data`:** `ProfileRepository` (policy toggle, approve, decline, remove, requests, Declined list, `followers` tier reads), `AthleteSpaces` (`followers` tier, decline records in `self`), `PendingFollows` (requested state).
- **Screens:**
  - New `FollowRequestsScreen` (Requests and Declined).
  - Changed: Profile (requests entry, Requested button), FollowList (Remove follower), EditProfile (Approve followers switch), `VisibilityPicker` and the streams visibility choices (Followers option).
- **Docs:** AGENTS.md architecture paragraph, `.agents/rules/appview.md`, `lexicons.md`, `data-and-auth.md`, BACKLOG (private activities).
- **Tests:** `SpaceAccessTest`, `ActivityStreamsTest`, `AppViewTest` (timeline, counts, lists, requests), `JetstreamEventTest`, `ProfileFollowTest`, `ProfileRepositoryTest`, `ProfilePresenterTest`, `FollowListPresenterTest`, a new `FollowRequestsPresenterTest`, plus an end-to-end check on the local stack.
