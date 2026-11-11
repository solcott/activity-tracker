# Design

## Context

`ProfileScreen` (`feature/profile`) is drawn on iOS by the shared Compose UI (`FallbackScreen`) under the native shell. Its presenter (`ProfilePresenter`) already runs headless in `:app-host`; the pages it opens (Followers, Following, Follow requests, Edit profile, Starred segments, Activity invites, Settings) are native and registered. `ActivityCardView` (`ios-native-activity-card`) draws an activity from `ActivityCardContent`; the batch import page feeds it with `ActivityCardContent.Companion.shared.of(activity:viewerDid:)`, the page's `Revealed` and a wrapped `ActivityListEvent`, which this page does the same way (`ProfileEvent.Activity`).

`ProfileUi` chooses a lot itself: the title, the counts, the details' lines (location, age, gender, height in the viewer's units, weight), the follow button's label and style, the own entries and their numbers ("Follow requests (500+)"), which action row shows, the menu's labels and items, the block confirmation's words, and whether the activities wait behind the hidden warning.

Two parts of the page reach Compose-only machinery:
- **Report** (`ReportingHost`): its sheet is `SubCircuitContent(ReportScreen)`, a SubCircuit, whose presenter runs only inside Compose. The presenter host runs screens, not sub-screens; a parallel session is adding SubCircuits to it.
- **Posts** (`postsSection`): each post is the shared `PostCard`, which has no native twin, and its quote sheet (`QuoteSheetHost`) and Report are sheets the host can't show either.

## Goals / Non-Goals

**Goals:** the header, the actions, Mute and Block, and the activities drawn natively, in the shared page's words, with Kotlin deciding every text and condition both UIs show; snapshots of every sample; the native pages it opens pushed through the registry with nothing new.

**Non-goals:** Report and Posts natively; reshaping `ActivityCardView` or its texts; any change to what the profile loads or writes.

## Decisions

1. **Report and Posts are left out of the native page, not drawn by the fallback.** A native page can't host a piece of the Compose page: `IosApp.screenViewController(record:)` draws the whole screen. Swapping the whole page to the fallback while Posts is chosen or the report sheet is open would change the page's look under the athlete's finger and lose its scroll position. So the native menu offers Mute and Block, and the page lists activities with no Activities and Posts choice (one choice is no choice). Both are recorded in the roadmap's open items and in the spec deltas (`moderation/reports`, `feed/posts`); they close when the host runs SubCircuits and a native post card exists. The presenter is unchanged: the Compose page keeps both.
   - *Alternative:* keep the page shared until both exist. Rejected: the task is to make the header, actions and activities native now, and the parent change asked for exactly this split.

2. **Texts in `ProfileTexts` (`:feature-profile-api`), conditions as getters on `ProfileState`.** Following `FollowListTexts` and `EditProfileTexts`:
   - `ProfileTexts`: `title(profile)` (shown name, or "Profile" before it loads), `activities(count)`, `followers(count)`, `following(count)` (counts written by `formatWhole`, a missing count as zero), `location(details, countryName)` (the platform names the country, as edit profile's choice does: `countryName` in Compose, `Locale` in Swift; Kotlin joins the parts), `facts(details, now, units)` (age, gender, height, weight joined by " · "; `height(mm, units)` for cm or feet and inches), `follow(button)`, `ownEntries(state)` (a plain list of `ProfileEntry(label, event)`: Edit profile, Starred segments, Activity invites with its number when there are some, Follow requests with "N" or "N+"), `mute(muting)`, `block(blocking)`, `blockConfirmTitle(name)`, `blockConfirmMessage`, `blockedOnBluesky`, `activitiesEmpty`, `more`, `settings`.
   - `ProfileState`: `actions` (`ProfileActions.Own`, `BlockedOnBluesky`, `Unblock`, `Follow`), `followButton` (`FollowButton.Follow`, `Following`, `Requested`, each with `primary`), `offersBlock` (not a Bluesky-only block), `activitiesHidden` (a hidden athlete not yet revealed), `hiddenKey`, `avatarBlur`, `notices()`, `activityList` (the activities' typed list, since Swift reads a lexicon class only through a typed getter, never the erased `ContentState` value), and `showsActivities` (not blocking).
   - Enums carry what Swift asks (`FollowButton.primary`), as `ZoneThreshold` does; nothing crosses as an enum list. No member is named `description`; lists in fields are `ImmutableList`s; no `Pair`.
   - `ProfileUi` asks all of them, with no visible change.

3. **The native page is one grouped `List`.** Sections, top to bottom:
   - status rows: the failed action (`ErrorMessage` row) and the header's refresh (`RefreshStatusView`, progress only while blocking, since the activities show their own), as the shared header does;
   - the header: the avatar (72 pt, blurred under its label until tapped, revealing by the athlete's DID as the shared avatar does) beside the handle; the notices; the bio; the location and facts lines; the three counts (activities as text, followers and following as plain buttons opening their lists);
   - the actions: the own entries as navigation rows (`AppListRow(opens:)`), which is how iOS lists pages to open, or the follow button full width (primary or secondary, with progress while pending), or Unblock (secondary), or the Bluesky note;
   - the activities: the hidden card (title, message, Show) or the activities' refresh status, then one section per card (`ActivityCardView`, author not clickable), then the empty message once loaded with none.
   The page's loading and failure are `ContentStateView`'s (the `Void` form, reading `state.profile`). The bar has Settings on the own profile, and on anyone else's a `Menu` ("More for this athlete") with Mute or Unmute and Block or Unblock; Block asks with an `.alert` whose Block is destructive. `pageTitle` titles it, so it works in a column too.
   - *Alternative:* a custom scroll view with a large header. Rejected: every native page so far is a system list; the activity card is a list row.

4. **Samples.** `ProfileSamples.all` stays the content-loading set. A new `ProfileSamples.variants` adds: `following`, `requested`, `followPending`, `muted`, `blocked`, `blockedOnBluesky`, `ownApproving` (requests 500 and more, two invites), `details` (location and facts), `imperial` is covered by a text test rather than a sample since units come from the environment, `labeled` (a blurred avatar and a warning), `hidden`, `actionFailed`. The Android screenshot test renders `all` only, so its references don't change.

5. **Tests.** `ProfileTextsTest` (`commonTest`) over every text and getter branch; the presenter's tests unchanged. Swift: `ProfileTextTests` reading each sample's title, counts, lines, entries and button through the catalog; `ProfileSnapshotTests` over `all` and `variants` (iOS 26.0); a registry test line.

## Risks / Trade-offs

- **iOS loses Report and Posts on profiles** until the open items close → recorded in the spec deltas and roadmap; the parent session decides when to merge.
- **The activity card is shared with the gear page's session** → used as it is; any addition is additive.
- **Shared Swift files** (`KotlinTypes.swift`, `Samples.swift`, `ScreenRegistry.swift`, `ScreenRegistryTests.swift`) and the roadmap → edits are appended blocks only.
- **A follow, block or mute on the walk writes to alice.test on the local stack** → each is undone, and only local test accounts are touched.

## Open Questions

None blocking. Whether the profile gets a pull to refresh natively is left as the shared page has it (none).
