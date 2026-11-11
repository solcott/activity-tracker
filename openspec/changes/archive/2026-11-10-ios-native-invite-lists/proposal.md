# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time, cheapest first. After the follow lists, the invites lists are the cheapest left: Activity invites is built like Follow requests (two tabs, `needsSignIn`, an `answering` set, a content of two lists) and Add people is two short lists of people with a button each, so both build on `FollowRequestsView`, `AppItemRow`, `Avatar`, `ContentStateView`'s form without a value and `SignInAgainNotice`, which `ios-native-follow-lists` made for them.

Accepting an invite (`AcceptInviteScreen`) stays the shared UI: it draws a route preview and a visibility form, whose native pieces another change is building.

## What Changes

- **Activity invites is native on iOS.** Titled "Activity invites", with a segmented choice of Invites and Declined; each invite with the activity's title (or its type when it has none), who added the athlete and its distance and time; View on each, and Decline beside it on Invites while the session can decline, both unavailable while that invite's decline is written; the Declined tab's note; each tab's empty message; a failed decline, or that the session must sign in again before declining, with Sign in again; the content-loading states. It is pushed over the athlete's own profile (its Activity invites entry), and View opens the shared accept page.
- **Add people is native on iOS.** Titled "Add people", with the note that people added can see the activity's start time, route and speed; the notice that adding failed or that the session must sign in again, with Sign in again; "Added", each invitee with their picture, name and handle, Pending or Joined, and Cancel or Remove; "Your followers", each with Add; the notes that the activity is full and that only followers can be added; every button unavailable while that account is added or removed, and Add while the activity is full or the session can't invite; the content-loading states. It is pushed over the athlete's own activity (Add people), which stays the shared UI.
- **Kotlin chooses the text and conditions, both UIs ask.** `ActivityInvitesTexts` (each tab's label and empty message, an invite's title, who added the athlete, and its distance and time) and `AddPeopleTexts` (an invitee's status and its button); `ActivityInvitesState`'s typed `pending`, `declined` and the tab's `invites`, `offersDecline`, `canAnswer(invite)`, `showsDeclinedNote` and `notice`; `AddPeopleState`'s typed `invitees` and `followers`, `full`, `canAdd(follower)`, `canRemove(invitee)` and `notice`. Neither UI reads the `answering` or `busy` sets. The shared `InviteSummary` asks the same texts. No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of both pages in every shared sample, plus new variants beside `all` (the Declined tab and its empty form, a decline in flight with a failed one, sign in again, invitees pending and joined, a removal in flight with a failed one, a full activity, no followers, a session that can't invite), so the shared screenshot set and the content-loading conformance keys are unchanged. Registry entries and their test.

Out of scope: accepting an invite (`AcceptInviteScreen`), the profile and the activity page that open these, pull to refresh and swipe actions (the shared pages have neither).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-invites`: the invites list and Add people are drawn natively on iOS, saying and offering what the shared pages do.
- `dev/shared-display-text`: the invites list's and Add people's words and conditions are chosen in shared code.

## Impact

- **`:feature-invites-api`:** `ActivityInvitesTexts`, `AddPeopleTexts`, getters on `ActivityInvitesState` and `AddPeopleState`; tests. **`:feature-invites-ui`:** `ActivityInvitesUi`, `AddPeopleUi` and `InviteSummary` ask them.
- **`:ui-resources`:** one string joining an invite's distance and time (so the join is a shared string, not Kotlin's `" · "`), and the iOS catalog regenerated.
- **`:app-fixtures`:** `ActivityInvitesSamples.variants`, `AddPeopleSamples.variants`.
- **`iosApp`:** `ActivityInvitesView`, `AddPeopleView`, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (invite lists)" and findings section, its open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** the test account may have no invites and no activity with followers to add, so the pages may be reachable on a device only empty, and adding someone or declining writes real records, which the walk doesn't do without the user's go-ahead; iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0.
