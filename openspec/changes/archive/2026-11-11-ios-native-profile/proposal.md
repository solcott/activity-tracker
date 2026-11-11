# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time. The profile (`ProfileScreen`) was waiting on a native activity card; `ios-native-activity-card` (archived) made `ActivityCardView`, so the profile's activity list can be native now. The pages it opens (Followers, Following, Follow requests, Edit profile, Starred segments, Activity invites, Settings) are already native, so the profile is the last shared page between the You tab and them.

## What Changes

- **The profile is native on iOS**, in the system's grouped list and navigation bar, drawn from `ProfilePresenter`'s state:
  - the header: the avatar (blurred under its label until revealed), the handle, the labels' notices, the bio, the details the viewer may see (a location line, then age, gender, height and weight in the viewer's units), and the activities, followers and following counts, the last two opening their lists;
  - the actions: on the athlete's own profile Edit profile, Starred segments, Activity invites (when they have some) and Follow requests (when they approve followers), and Settings in the bar; on anyone else's Follow, Following or Requested (a tap cancels it; busy while written), Unblock on a profile the viewer blocks, the Bluesky note where only Bluesky can remove the block, and a bar menu with Mute or Unmute and Block (after the shared confirmation) or Unblock;
  - a failed action, a refresh's status, and the activities loaded on their own below the header, each a native `ActivityCardView`, with the empty message; behind the hidden warning for a hidden athlete reached by link.
- **Two parts wait, and the native page leaves them out** (each an open item in the roadmap):
  - **Report** on someone else's profile. Its sheet is a SubCircuit (`ReportScreen`), which the presenter host can't run yet (a parallel change adds SubCircuits to the host). The native menu offers Mute and Block only.
  - **The Posts tab.** Posts need a native post card, which doesn't exist; its card also opens the quote sheet and Report. With one list left, the native page has no Activities and Posts choice and lists activities.
- **Kotlin chooses the text and conditions, both UIs ask.** The page's title, the counts, the details' lines, the follow button's label and style, the own profile's entries, the menu's items, the block confirmation, and which action row shows move from `ProfileUi` into `ProfileTexts` and getters on `ProfileState` in `:feature-profile-api`. The Compose page asks the same. No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of the page in every shared sample, plus variants beside `all` (following, requested, a follow in flight, muted, blocked, blocked on Bluesky, the own profile approving followers with requests and invites, details in both unit systems, a labelled avatar, a hidden athlete, a failed action), so the content-loading conformance keys and the Android references are unchanged. A registry entry and its test.

Out of scope: Report and Posts natively (above), the activity card itself (used as it is; only additions if the page needs one), and any change to what the profile loads or writes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `graph/follow-requests`: the profile's follow button and its own Follow requests entry are drawn natively on iOS.
- `graph/blocks`: Block and Unblock on a native profile on iOS, with the same confirmation.
- `graph/mutes`: Mute and Unmute on a native profile on iOS.
- `moderation/reports`: a profile on iOS doesn't offer Report until the report sheet can be shown natively.
- `feed/posts`: a profile on iOS lists activities only until posts can be drawn natively.
- `dev/shared-display-text`: the profile's words and conditions are chosen in shared code.

## Impact

- **`:feature-profile-api`:** `ProfileTexts`, getters on `ProfileState` (`actions`, `followButton`, the menu, the hidden check); tests. **`:feature-profile-ui`:** `ProfileUi` asks them.
- **`:app-fixtures`:** `ProfileSamples.variants`.
- **`iosApp`:** `ProfileView` (and a small header view), type aliases, samples, previews, snapshot and text tests, a registry entry. `ActivityCardView` is used unchanged.
- **Docs:** the roadmap's phase 4 entry, "Phase 4 probes (profile)" and findings, open items; `.agents/rules/compose-screens.md` if a rule changes.
- **Risk carried:** iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0; the signed-in simulators are shared with other sessions, so the walk waits for theirs; follow, block and mute on the local stack are undone after the walk. Report and Posts on a profile are unavailable on iOS until their open items close.
