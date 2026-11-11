# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time, cheapest infrastructure first. The roadmap's next "profile lists" are the ones a profile opens: Followers, Following and Follow requests. They are lists of people, which the native Blocked and Muted accounts lists (`ios-native-settings-lists`) already draw with `PagedList`, `AppItemRow` and `Avatar`, and a loaded content whose value holds lists, which Ignored devices (`ios-native-gear-pages`) settled. So they need almost nothing new: a subtitle under a page's title, a segmented choice of tab, and the sign-in-again notice.

The other profile screens wait: Moderation and the gear item page need the native activity card, zones and threshold history need charts, Privacy zones needs a map, and Search's tab also shows the segment browse screen, so it is a change of its own. The profile itself stays the shared UI's for now (its header, tabs and activity cards are the feed's work).

## What Changes

- **Followers and Following are native on iOS.** Titled "Followers" or "Following", with the athlete's handle under the title once it is known; each account with its avatar, name and handle, opening its profile; "No followers yet." or "Not following anyone yet." when the list is empty; page by page, with the loading, refresh, next-page and failure states of `ui/content-loading` and what failed in the list's own words. On the athlete's own Followers list while they approve followers, each account has Remove, unavailable while its removal is written, and a failed removal shows above the accounts, which stay.
- **Follow requests is native on iOS.** Titled "Follow requests", with a segmented choice of Requests and Declined; each request with its avatar, name and handle, opening its profile, with Decline and Approve (Approve alone on Declined), both unavailable while the answer is written and for an account that can't be answered; the Declined tab's privacy note; the note that only the first 500 requests show; the empty message of each tab; a failed answer, or that the session must sign in again before declining, with Sign in again; the content-loading states.
- **Both open as they do today**, pushed over the shared profile in its tab's stack, and what they open (a profile) stays the shared UI.
- **Kotlin chooses the text and conditions, both UIs ask.** `FollowListTexts` (title, subtitle and empty message by kind) and `FollowRequestsTexts` (each tab's label and empty message); `FollowListState.loadErrorKind` and `isRemoving(account)`; `FollowRequestsState`'s shown accounts for the tab, `offersDecline`, `canAnswer(account)`, `showsDeclinedNote` and `showsTruncatedNote`; and the sign-in-again notice the shared `signInAgainItem` decides today (`SignInAgainNotice` in `:app-common-api`), which Follow requests, Activity invites and Add people all ask. Neither UI reads the `removing`, `answering` or `unanswerable` sets. No visible change on Android, desktop or web.
- **A page's title can carry a subtitle** in the design system: `pageTitle(_:subtitle:)`, the system's navigation subtitle on iOS 26 and a two-line title before it, and a second line in a column header.
- **Samples, previews and snapshots** of both pages in every shared sample, plus new variants beside `all` (Following, a removable list with a removal in flight and a failed one, the Declined tab, answers in flight, an unanswerable account, a failed answer, sign in again, more than 500 requests), so the shared screenshot set and the content-loading conformance keys are unchanged. Registry entries and their test.

Out of scope: the profile page itself, Search, Activity invites and Add people (which only gain the shared sign-in-again rule, with no visible change), pull to refresh (the shared pages have none), and swipe actions on the rows (the shared pages show buttons).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `graph/follow-requests`: the followers and following lists and the follow requests page are drawn natively on iOS, saying and offering what the shared pages do.
- `dev/shared-display-text`: the follow lists' and follow requests' words and conditions, and the sign-in-again notice, are chosen in shared code.
- `ui/ios-design-system`: a native page's title can carry a subtitle, in the stack's bar and in a column header.

## Impact

- **`:app-common-api`:** `SignInAgainNotice`; tests. **`:app-common-ui`:** `signInAgainItem` asks it.
- **`:feature-profile-api`:** `FollowListTexts`, `FollowRequestsTexts`, getters on `FollowListState` and `FollowRequestsState`; tests. **`:feature-profile-ui`:** `FollowListUi` and `FollowRequestsUi` ask them.
- **`:app-fixtures`:** `FollowListSamples.variants`, `FollowRequestsSamples.variants`.
- **`iosApp`:** `FollowListView`, `FollowRequestsView`, `pageTitle(_:subtitle:)` and `PageBar`'s second line with a gallery entry, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (follow lists)" and findings section, its open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** the test account may not approve followers, so Follow requests and Remove may not be reachable on a device without changing its follow policy, which the walk doesn't do; iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0.
