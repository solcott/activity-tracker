# Design

## Context

What exists, from the code:

- **The contracts** (`feature/profile/api`). `FollowListState` holds `kind` (`Followers`, `Following`), `accounts: PagedState<ProfileViewBasic, ProfileViewBasic>` whose meta is the athlete whose list it is (`subject`, null until the first page), `canRemove` (the athlete's own followers while they approve followers), `removing: ImmutableSet<Did>` and `actionError: UiError?`; events `Back`, `LoadMore`, `Retry`, `Open(did)`, `Remove(did)`. `FollowRequestsState` holds `tab` (`Requests`, `Declined`), `content: ContentState<FollowRequestsContent>` (`requests`, `declined`, both `ImmutableList<ProfileViewBasic>`, and `truncated`), `needsSignIn`, `answering` and `unanswerable` (`ImmutableSet<Did>`), `error: UiError?`, and the typed getters `requests` and `declined`; events `Back`, `SelectTab(tab)`, `Approve(did)`, `Decline(did)`, `Open(did)`, `Retry`, `SignInAgain`. Both are plain `Screen`s pushed by `ProfilePresenter` (`OpenFollowers`, `OpenFollowing`, `OpenFollowRequests`) in whichever tab's stack the profile is in.
- **The shared UIs build text and decisions in composables.** `FollowListUi` titles the page by kind, puts `handleLabel(subject.handle)` in the bar's subtitle, shows `actionError` first, then `pagedRows` with the kind's load error and empty text, and an `AccountActionRow` with Remove (disabled while `did in removing`) when `canRemove`, else an `AccountRow`. `FollowRequestsUi` shows, once loaded, an `AppSegmentedControl` of the two tabs, `RefreshStatus`, `signInAgainItem(error, needsSignIn)`, the declined note above a non-empty Declined list, the rows (Decline on Requests only, then Approve, both disabled while answering or unanswerable), the truncated note on Requests, and each tab's empty text. `signInAgainItem` (`:app-common-ui` `ListParts.kt`) decides in the composable whether a notice shows, which error it says (`error`, or `UiError(SignInAgain)` when only `needsSignIn`) and whether it offers Sign in again; Activity invites and Add people use it too.
- **The native pieces this reuses**: `PagedList` (as `BlockListView` uses it), `ContentStateView`'s form without a value and `RefreshStatusView` (as `IgnoredSensorsView`), `AppItemRow` with `Avatar`, `AppPackage.getShownName`, `LabelText.handle(handle:)`, `AppSecondaryButton(small:)`, `ErrorMessage(layout: .row)`, `.listStatusRow()`, `pageTitle`.
- **Samples**: `FollowListSamples.all` (`pagedSamples` of bob and carol under alice, Followers, every paged kind) and `FollowRequestsSamples.all` (the content-loading kinds over bob's and carol's requests). Neither has Following, Remove, the Declined tab, an answer in flight, an unanswerable account, a failed answer, sign in again or the truncated note.

## Goals / Non-Goals

**Goals:**

- The two pages, native, saying and offering exactly what the shared pages do.
- Every rule they follow written once in Kotlin, including the sign-in-again notice the invites pages will need natively.

**Non-Goals:**

- The profile page, Search, Activity invites and Add people natively.
- Swipe actions, pull to refresh, or a progress mark while an answer or removal is written: the shared pages offer buttons and disable them, and the native ones follow them.

## Decisions

### 1. Both pages in one change

Both are lists of people opened from a profile, built from the same row, and neither needs a new data type. Follow requests adds a segmented choice and the sign-in-again notice; Followers adds a subtitle. Together they stay smaller than the settings lists change was.

### 2. Probe first

Throwaway Swift tests on the iPhone 17 simulator (iOS 26.0), and the generated Swift read, recorded in the roadmap under "Phase 4 probes (follow lists)":

- A `PagedState`'s meta through the state's typed `subject` getter, on every sample: alice once answered, nil before.
- `FollowRequestsState.requests` and `declined` through `elements(as: ProfileViewBasic.self)`, empty ones included, and that the page never needs `content.data`.
- `FollowRequestsTab` as a Swift enum: whether Swift sees its cases as `CaseIterable` (so the tab order needn't be written in Swift), and `FollowRequestsEvent.SelectTab(tab:)` built from it.
- `.navigationSubtitle` in an inline bar on iOS 26.0: placed under the title, and what a snapshot of it looks like.
- The stub report for `:feature-profile-api` and `:app-common-api` after decision 3, read once it lands.

### 3. Text and conditions in Kotlin

- **`SignInAgainNotice`** in `:app-common-api` (package `app`, beside `ContentShown`): `@Immutable data class SignInAgainNotice(val error: UiError, val offersSignInAgain: Boolean)` with `companion object { fun of(error: UiError?, needsSignIn: Boolean): SignInAgainNotice? }`: null when neither; else `error ?: UiError(SignInAgain)`, offering Sign in again when `needsSignIn` or that error's kind is `SignInAgain`. `signInAgainItem` asks it, so Activity invites and Add people change nothing visibly. A test for each branch.
- **`FollowListTexts`** in `:feature-profile-api` beside `BlockListTexts`: `title(kind)`, `empty(kind)` and `subtitle(subject: ProfileViewBasic?): AppText?` (`LabelText.handle`, null before the first page). `FollowListState.loadErrorKind` and `isRemoving(account: ProfileViewBasic)`, like `BlockListState`'s. Remove is a fixed string, named in Swift.
- **`FollowRequestsTexts`**: `tab(tab)` and `empty(tab)`. `FollowRequestsState` gains `accounts` (the tab's list, typed `ImmutableList<ProfileViewBasic>`), `offersDecline` (Requests), `canAnswer(account)` (neither answering nor unanswerable), `showsDeclinedNote` (Declined, not empty), `showsTruncatedNote` (Requests and truncated) and `notice` (`SignInAgainNotice.of(error, needsSignIn)`). The title, Approve, Decline and the notes are fixed strings.
- `FollowListUi` and `FollowRequestsUi` ask them; `commonTest`s over the samples and variants cover every branch.

### 4. A subtitle in the design system

`pageTitle(_:subtitle:back:canGoBack:)` and the form with actions take an optional `subtitle: Text?`. In the stack's bar: on iOS 26 `.navigationSubtitle`, the system's own; before it, a `.principal` toolbar item of the title in the bar's headline over the subtitle in caption, secondary, with `.navigationTitle` still set so the back button and the app switcher name the page. In a column header (`PageBar`) the subtitle is a second line under the title, the header at least a bar's height. A gallery entry (`pageBarSubtitle`) and its reference. Nil draws exactly what the page draws today, so every existing reference stays.

### 5. The two views

- **`FollowListView`**: `PagedList(state.accounts, of: ProfileViewBasic.self, errorKind: state.loadErrorKind, id: { $0.did.value })`, the action error as `ErrorMessage(layout: .row)` in a status row at the top, each account an `AppItemRow` with `Avatar`, the shown name, `LabelText.handle` as its line, opening `Open(did)`; with `canRemove` an `AppSecondaryButton(.followListRemove, small: true)` trailing, disabled while `isRemoving`, otherwise the disclosure mark (the design system's rule for a row that opens something and has no control). The empty text as a secondary centred row. `pageTitle(Text(title), subtitle: subtitle.map(Text.init))`.
- **`FollowRequestsView`**: `ContentStateView(state.content, errorKind: .LoadFollowRequests)` in its form without a value; an `.insetGrouped` `List` of: the tabs as a segmented `Picker` bound to `state.tab` (setting it sends `SelectTab`), in a status row; `RefreshStatusView`; the notice as `ErrorMessage(layout: .row)` with `AppSecondaryButton(.actionSignInAgain)` under it when it offers one; then one section of the tab's accounts as `AppItemRow`s with `Avatar`, name and handle, opening `Open(did)`, and trailing Decline (`AppSecondaryButton`, when `offersDecline`) and Approve (`AppButton`, filled), both small and disabled unless `canAnswer`; the section's footer is the declined or truncated note (they never show together: one is Declined's, the other Requests'), as iOS puts a list's explanation; or the tab's empty text as a secondary row. `pageTitle(Text(.followRequestsTitle))`. The footer replaces the shared page's note above the Declined rows: an iOS section explains itself under its rows.
- `AppButton` gains `small:`, as `AppSecondaryButton` has, for a row's filled action; the gallery's buttons entry shows it.
- Both registered in `ScreenRegistry.app`; `KotlinTypes.swift` aliases the two screens, states and events, `FollowListKind`, `FollowRequestsTab`, `FollowListTexts`, `FollowRequestsTexts` and `SignInAgainNotice`.

### 6. Samples, previews, snapshots

- `FollowListSamples.variants`: `following` (bob's Following, alice and carol), `followingEmpty`, `removable` (alice's own followers with `canRemove`, bob's removal in flight and a failed removal, unreachable). `FollowRequestsSamples.variants`: `declined` (the Declined tab with dave), `declinedEmpty`, `answering` (bob being answered, a third account unanswerable, a failed answer), `needsSignIn`, `truncated`. All apart from `all`, whose keys are `ContentLoadingConformanceTest`'s and the Android screenshot set's.
- Previews: `SamplePages` over each page's samples and variants inside a `NavigationStack`.
- `FollowListsSnapshotTests`: every sample and variant of both pages on an iPhone, light and dark, with `everySampleHasASnapshot`.
- `FollowListsTextTests` (Swift Testing): the spec's scenarios on the samples (titles and subtitles, rows' names and handles, Remove's and answers' availability, the tab's accounts, the notes, the notice through `ErrorTexts`, the empty texts).

### 7. Walking the pages

On the signed-in iPhone 17 Pro and iPad Air 11-inch (iOS 26.0): the account's own profile → Followers and Following (it follows bob), an account opened from a list and back, bob's Followers from his profile, back by the button and the swipe, the iPad upright and on its side. Follow requests and Remove show only while the account approves followers. The walk doesn't turn approval on to reach them: that writes an approval for each current follower, which turning it off leaves behind. If the account already approves followers they are walked without answering a request; otherwise they rest on the snapshots and text tests and are listed as not verified. The iOS 18.4 build is launched to sign-in only.

## Risks / Trade-offs

- **[`navigationSubtitle` placement]** → If the probe finds it misplaced in an inline bar, the `.principal` form is used on every version.
- **[A segmented `Picker` in a list row]** → It may take a row's inset and background; it sits in a status row (no background) and is checked in the snapshots.
- **[Follow requests unreachable on the test account]** → Rests on snapshots and text tests; listed as not verified rather than changing the account's follow policy.
- **[iOS 18 not walked signed in]** → Swift tests on 18.4 and a launch; the subtitle's iOS 18 form rests on a snapshot taken there only if references allow, otherwise listed as not verified.

## Migration Plan

None: the shared pages stay on every other platform and as the fallback on iOS for anything not registered.
