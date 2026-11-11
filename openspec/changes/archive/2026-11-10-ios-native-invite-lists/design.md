# Design

## Context

What exists, from the code:

- **The contracts** (`feature/invites/api`, `ActivityInvitesScreen.kt`). `ActivityInvitesState` holds `tab` (`Pending`, `Declined`), `content: ContentState<ActivityInvitesContent>` (`pending`, `declined`, both `ImmutableList<InviteView>`), `needsSignIn`, `answering: ImmutableSet<AtUri>` and `error: UiError?`; events `Back`, `SelectTab(tab)`, `Open(invite: AtUri)`, `Decline(invite)`, `Retry`, `SignInAgain`. `AddPeopleState` holds `content: ContentState<AddPeopleContent>` (`invitees: ImmutableList<InviteeView>`, `followers: ImmutableList<ProfileViewBasic>`, `full`), `canInvite`, `busy: ImmutableSet<Did>` and `error`; events `Back`, `Add(did)`, `Remove(invitee: InviteeView)`, `Retry`, `SignInAgain`. Unlike `FollowRequestsState`, neither state has typed getters for its lists or any rule getters yet.
- **Where they open.** `ActivityInvitesScreen` is pushed by `ProfilePresenter` (`OpenActivityInvites`, the own profile's "N activity invites" button while there are any), and `AddPeopleScreen(activity)` by `ActivityDetailPresenter` (`AddPeople`, the "Add people" button of the own activity's Together-with section). Both parents are still the shared UI's. View opens `AcceptInviteScreen`, which stays shared.
- **The shared UIs build text and decisions in composables.** `ActivityInvitesUi`: `ListScreen` titled `activity_invites_title`, then an `AppSegmentedControl` of the two tabs, `RefreshStatus`, `signInAgainItem(error, needsSignIn)`, the declined note above a non-empty Declined list, a row per invite (`InviteSummary`, then Decline when Pending and not `needsSignIn`, and View, both disabled while `uri in answering`), and each tab's empty text. `InviteSummary` builds "Bob added you" (`activity_invites_from` with `shownName`), the title or `activityTypeLabel(ActivityType(sport, subSport))`, and `listOfNotNull(Format.distance, Format.duration).joinToString(" · ")`. `AddPeopleUi`: `RefreshStatus`, the notice `ListNote`, `signInAgainItem(error, !canInvite)`, an "Added" heading over the invitees (each an `AccountActionRow`, which doesn't open, with Pending or Joined and Cancel or Remove, enabled while `canInvite` and not busy), "Your followers", the full note, the no-followers note, and each follower with Add (enabled while `canInvite`, not full, not busy).
- **What `ios-native-follow-lists` left to build on**: `FollowRequestsView`'s shape (`ContentStateView` without a value, the segmented `Picker` over the enum's `allCases` in a status row, `RefreshStatusView`, the notice row over `SignInAgainNotice`, a section whose footer is the list's note), `AppItemRow` with `Avatar`, `AppButton(small:)` beside `AppSecondaryButton(small:)`, `AppPackage.getShownName`, `LabelText.handle`, `ErrorMessage(layout: .row)`, `.listStatusRow()`, `pageTitle`, and `Stats` from the environment.
- **Samples** (`InvitesSamples.kt`): `ActivityInvitesSamples.all` (the content-loading kinds over bob's titled and carol's untitled invite, Pending), `AddPeopleSamples.all` (bob Joined, carol to add). Neither has the Declined tab, a decline or add in flight, a failure, sign in again, a pending invitee, a full activity or no followers.

## Goals / Non-Goals

**Goals:**

- The two pages, native, saying and offering what the shared pages do.
- Every rule they follow written once in Kotlin; Swift reads no `ImmutableSet` and no content's erased value.

**Non-Goals:**

- Accepting an invite natively, and the profile or activity page that open these.
- Swipe actions, pull to refresh, or opening an invitee's or follower's profile from a row: the shared pages have none of them.

## Decisions

### 1. Both pages in one change

They are one feature's two lists, built from the same parts as Follow requests, and need no new design-system piece. Together they are smaller than the follow lists change.

### 2. Probe first

Throwaway Swift tests on a cloned iPhone 17 simulator (iOS 26.0), and the generated Swift read, recorded in the roadmap under "Phase 4 probes (invite lists)":

- Whether `InviteView` and `InviteeView` (`:core-lexicon`) have Swift types today, or only once an exported signature names them, and that the content's lists read through `elements(as:)` once they do (empty ones included).
- `ActivityInvitesTab` as a `CaseIterable` Swift enum, and `Open(invite:)` built from an `InviteView.uri` (`AtUri`).
- `AddPeopleEvent.Remove(invitee:)` built from a Swift-held `InviteeView`.
- The stub report for `:feature-invites-api` once decision 3 lands.

### 3. Text and conditions in Kotlin

In `:feature-invites-api`, beside the contracts:

- **`ActivityInvitesTexts`**: `tab(tab)` and `empty(tab)`; `title(invite)` (the record's title as written, else `LabelText.activityType`); `from(invite)` (`activity_invites_from` with the inviter's `shownName`); `summary(invite, units)` (`StatText.distance` and `StatText.duration` joined by a new shared string `activity_invites_summary`, "%1$s · %2$s", or the time alone without a distance). Naming `InviteView` in these is also what gives it a Swift type if the probe finds none.
- **`ActivityInvitesState`** gains `pending`, `declined` and `invites` (the tab's), typed `ImmutableList<InviteView>`; `offersDecline` (Pending and not `needsSignIn`); `canAnswer(invite)` (not answering); `showsDeclinedNote` (Declined, not empty); `notice` (`SignInAgainNotice.of(error, needsSignIn)`).
- **`AddPeopleTexts`**: `status(invitee)` (Joined or Pending) and `remove(invitee)` (Remove or Cancel).
- **`AddPeopleState`** gains `invitees` and `followers` (typed), `full`, `canAdd(follower)` (`canInvite`, not full, not busy), `canRemove(invitee)` (`canInvite`, not busy) and `notice` (`SignInAgainNotice.of(error, !canInvite)`).
- The title, View, Decline, the headings, Add and the notes are fixed strings, named in Swift.
- `ActivityInvitesUi`, `InviteSummary` and `AddPeopleUi` ask them; `commonTest`s cover every branch. The new string joins `SharedTextNamesTest` through the closed set it belongs to, and the iOS catalog is regenerated (`checkIosResources`).

### 4. The two views

- **`ActivityInvitesView`**: `FollowRequestsView`'s shape. `ContentStateView(state.content, errorKind: .LoadActivityInvites)` without a value; an `.insetGrouped` `List` of the tabs (`Picker` over `ActivityInvitesTab.allCases`, sending `SelectTab`), `RefreshStatusView`, the notice row, then one section of the tab's invites, its footer the declined note, or the tab's empty text as a secondary row. Each invite is an `AppItemRow` with no picture: the title, its lines "Bob added you" and the summary (from `@Environment(\.stats).units`), and trailing Decline (`AppSecondaryButton`, small, when `offersDecline`) and View (`AppButton`, small), disabled unless `canAnswer`. The row itself opens nothing, as the shared row doesn't. `pageTitle(Text(.activityInvitesTitle))`.
  - No picture, as the shared row has none: the first recording led with the inviter's `Avatar`, and beside Decline and View it left the text too narrow for "32.40 km · 1:05:00", which broke after the dot. The title leads and "added you" follows it, as a row's lines go under its title; the shared summary puts the inviter above.
- **`AddPeopleView`**: `ContentStateView(state.content, errorKind: .LoadInvitees)` without a value; an `.insetGrouped` `List` of `RefreshStatusView`, the notice row (`SignInAgainNotice`), a section headed "Added" when anyone was added, each invitee an `AppItemRow` with `Avatar`, name and handle, trailing its status (secondary) and Cancel or Remove (`AppSecondaryButton`, small, disabled unless `canRemove`); a section headed "Your followers", each follower with Add (`AppButton`, small, disabled unless `canAdd`), or "Only people who follow you can be added." as a secondary row when there are none, and the full note as its footer. The page's note on what people added can see is a secondary text row above the sections, first, as the shared page puts it, so it reads before either list.
  - Neither row opens anything, as the shared `AccountActionRow(onClick = {})` doesn't; no disclosure mark.
- `KotlinTypes.swift` aliases `InvitesPackage`, the two screens, states and events, `ActivityInvitesTab`, `ActivityInvitesTexts`, `AddPeopleTexts`, `InviteView` and `InviteeView` where needed, in one group. Both views registered in `ScreenRegistry.app`, grouped.

### 5. Samples, previews, snapshots

- `ActivityInvitesSamples.variants`: `declined` (the Declined tab listing bob's invite), `declinedEmpty`, `answering` (bob's decline in flight, a failed decline), `needsSignIn`, `noDistance` (an invite whose activity has no distance). `AddPeopleSamples.variants`: `pending` (bob Pending, carol being added, a failed add), `removing` (bob Joined being removed), `full` (20 invitees, a follower who can't be added), `noFollowers`, `needsSignIn` (`canInvite` false). All apart from `all`, whose keys are `ContentLoadingConformanceTest`'s and the Android screenshot set's.
- Previews: `SamplePages` over each page's samples and variants inside a `NavigationStack`.
- `InviteListsSnapshotTests`: every sample and variant of both pages on an iPhone, light and dark, with `everySampleHasASnapshot`.
- `InviteListsTextTests` (Swift Testing): the spec's scenarios on the samples (titles, lines and summaries, tabs' invites, buttons' availability, the notes, the notices through `ErrorTexts`, the empty texts, statuses).

### 6. Walking the pages

On a signed-in iPhone 17 Pro and iPad Air 11-inch (iOS 26.0), only when no other session is walking: the own profile's Activity invites entry if the account has invites, and an own activity's Add people. Nothing that writes (Decline, Add, Cancel, Remove, accepting) is tapped without the user's go-ahead; tabs, View to the shared accept page and back are. What the account can't reach rests on snapshots and text tests and is listed as not verified. The iOS 18.4 build is launched to sign-in only.

## Risks / Trade-offs

- **[`InviteView` with no Swift type]** → The texts name it, which the zones contracts showed is enough; the probe checks before the views are written.
- **[The test account has no invites]** → Activity invites is reachable from the profile only while there are invites; the page then rests on snapshots and text tests, listed as not verified.
- **[A row with two buttons and two lines on a narrow iPhone]** → The title and lines wrap beside the buttons, as Follow requests' rows do; checked in the snapshots.
- **[20 invitees make a long snapshot]** → The full variant is drawn at the page's height like every other; the image is long but compares like the rest.

## Migration Plan

None: the shared pages stay on every other platform and as the fallback on iOS for anything not registered.
