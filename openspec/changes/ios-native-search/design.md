# Design

## Context

`AthleteSearchUi` is one `AppScaffold`: a top bar titled by mode, a segmented control (Athletes, Segments), then either the segment
browse screen through `CircuitContent(SegmentBrowseScreen)` with a navigator that sends `AthleteSearchEvent.GoTo` and `Back`, or a
`SearchField` over a `LazyColumn` of `AccountRow`s through `pagedRows`, with the prompt while the query is blank and "No athletes match"
as the empty slot. The presenter owns the query (`TextFieldState`), the mode and the paged results; a new query keeps the old results
until its first page answers.

`LeftElsewhereUi` is a scrolling column: progress while checking, else the intro, a line per kind of pending work, the choice, an error,
progress while busy, Rejoin and Discard. `SessionView` shows it alone (`SingleScreen`) with no stack, so a native page's navigation
title would not show.

The native pieces exist: `PagedList` (with `keepsTop`), `AppItemRow` with `Avatar` (as `FollowListView` draws accounts),
`ErrorMessage`, `AppButton`/`AppSecondaryButton`, `TextFieldModel`, `pageTitle` with `\.tabRoot`.

## Decisions

1. **`AthleteSearchTexts` in `:feature-search-api`** (`title(mode)`, `mode(mode)`, `prompt(state)` and `empty(state)`, the last two
   null where they don't show) and **`LeftElsewhereTexts` in `:feature-settings-api`** (`intro(state)`, `lines(state)`), plain
   functions returning `AppText`, as `LeaveTexts` is. The count goes through `formatWhole`, so the Compose screen now groups it too.
   The Compose UIs keep their layout and only resolve these.
2. **The search field is the system's** (`searchable`, `.navigationBarDrawer(displayMode: .always)`), bound to a `TextFieldModel` over
   the state's query, followed with `follow(state.query.values)` as `AppTextField` does, so a restored or cleared query shows. It is
   not focused on opening: a tab root that raises the keyboard each time it is chosen isn't iOS's way, and the prompt says what to type.
   It is present only in Athletes mode, since the browse screen has its own name field.
3. **The mode choice is a segmented `Picker` over `SearchMode.allCases`** in a bar under the navigation bar (`safeAreaInset`/
   `safeAreaBar` at the top), so it stays put in both modes and the list still drives the large title's collapse.
4. **Athletes mode is a `PagedList` with `keepsTop`.** Its top rows are the prompt while the query is blank (a status row), so the
   prompt shows before any search, and the first page's progress or failure follows it; the empty slot is `AthleteSearchTexts.empty`.
   Rows are `AppItemRow`s as in Followers, opening the profile with `Open(did)`.
5. **Segments mode embeds the shared browse screen** (`SegmentBrowseEmbed`, a `UIViewControllerRepresentable`). A new iOS entry,
   `SegmentBrowseViewController(record)` in `composeApp`'s iosMain, exported as `IosApp.segmentBrowseViewController(record:)`, composes
   what the Search tab's Compose UI composes for that mode: the theme, Circuit's locals, units and sub-circuits of the session, and
   `CircuitContent(SegmentBrowseScreen)` with a navigator sending the record's latest `AthleteSearchState`'s `GoTo` and `Back`. It takes
   the record (the only type Swift has for it) and reads the sink from the record's state, so no Kotlin function type crosses into
   Swift. The controller is kept per record (the host keeps the record while it is in the stack), so switching modes back and forth
   keeps the browse screen's map and query, as the Compose tab does while the record lives. Outside the host (previews, snapshots) there
   is no record, and the embed draws a plain placeholder.
6. **`LeftElsewhereView`** is an inset grouped list: progress while checking; else a section of the intro and the lines, the choice as
   its footer-like text, the error, and a section with Rejoin (filled) and Discard and sign out, disabled while busy, with progress
   beside them. `SessionView` wraps the left-elsewhere `SingleScreen` in a `NavigationStack` so `pageTitle` shows (a fallback screen
   hides that stack's bar, so the shared UI is unchanged if the registry entry goes).
7. **Registered now.** Both screens show everything the shared UIs show; the only difference is the browse screen embedded rather than
   native.

## Risks

- A Compose view controller inside a native page: its safe area, the keyboard over its field and its map's gestures inside a SwiftUI
  page are new. Walked on the iPhone; if it misbehaves, the fallback is drawing the whole record with the shared UI in Segments mode.
- `searchable` toggled with the mode changes the bar's height; the walk checks it.
- The left-elsewhere screen needs a leave from another device; it is checked on a device only if the local stack can stage it, else
  through its snapshots.
