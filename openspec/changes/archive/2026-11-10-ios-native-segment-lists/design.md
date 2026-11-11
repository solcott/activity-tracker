# Design

## Context

What exists, from the code:

- **The contracts.** `StarredSegmentsState` (`feature/segment/api`) holds `segments: PagedState<StarredSegment, Unit>` (`StarredSegment(uri, segment: SegmentView?)`, null when gone), `removing: ImmutableSet<AtUri>`, `actionError: UiError?` and `needsSignIn` (the paging is disabled then, so the list never answers); events `Back`, `LoadMore`, `Retry`, `Open(uri)`, `Unstar(uri)`. `SegmentLeaderboardState` and `SegmentMayorsState` hold `name`, `period`, `scope`, `board: String?`, `boards: ImmutableList<String>`, `ageGroup`, `weightClass`, `gender` (each `String?`, null for all), `entries: PagedState<LeaderboardEntry | MayorEntry, BoardMeta<…>>` and `viewerEntry`; events for each choice (`PeriodChanged`, `ScopeChanged`, `BoardChanged`, `AgeGroupChanged(String?)`, `WeightClassChanged(String?)`, `GenderChanged(String?)`), `LoadMore`, `Retry`, and `OpenActivity(uri)` or `OpenAthlete(did)`. `DiveSiteRegularsState` (`feature/divesite/api`) holds `name`, `period`, `scope`, `entries: PagedState<DiveSiteRegularEntry, RegularsMeta>` and `viewerEntry`; events `PeriodChanged`, `ScopeChanged`, `LoadMore`, `Retry`, `OpenAthlete(did)`. Every token is a lexicon string constant (`LeaderboardPeriod`, `MayorPeriod`, `RegularsPeriod`, `LeaderboardScope`, `LeaderboardAgeGroup`, `LeaderboardWeightClass`, `LeaderboardGender`, `SegmentBoard`), each with an `all` list.
- **A new choice restarts the list.** The board presenters key their paging on the filters (`key(filters.key) { rememberRetainedPaging(…) }`), so a choice shows a list that hasn't answered: the shared screen keeps its filters item and shows the first page's spinner under it. Starred segments' paging is disabled while `needsSignIn`, so it never answers and the shared screen still shows its sign-in row.
- **The shared UIs build text in composables.** `StarredSegmentsUi` joins `Format.distance` and `segmentTypeLabel` with `" · "` in Kotlin, and decides the sign-in row (`errorText(UiError(SignInAgain))`), the empty text (none while `needsSignIn`), a row's availability (`segment != null`) and its unstar (`uri in removing`). The boards share `RankBoards.kt` (`:app-common-ui`: `MenuChip`, `ChoiceChip`, `RankRow`, `ScopeControl`, `rankBoardRows`, `ownPlace`) and `BoardParts.kt` (`:feature-segment-ui`: `BoardTypeChip`, `BracketChips`, `ageLabel`, `weightLabel` with the pound conversion, `boardLabel`), and each UI has its own period labels (`when` over tokens), its accent rule (rank ≤ 3 on a leaderboard, rank 1 on the others), its row's day (`effortDateLabel`, which parses `YYYY-MM-DD` and calls `longDate`, "Jan 5, 2026"), "Reached …" and a plural count fed the raw `Int`.
- **The native pieces this reuses:** `PagedList`, `AppItemRow`, `Avatar`, `AppPackage.getShownName`, `ErrorMessage(layout: .row)`, `.listStatusRow()`, `pageTitle`, `AppIconButton`, `@Environment(\.stats)` (`Stats.distance`, `Stats.duration`), and a segmented `Picker` (as Follow requests). `PagedList` draws the first page's progress or failure alone, centred, without the screen's `top` rows.
- **Samples:** `StarredSegmentsSamples.all`, `SegmentLeaderboardSamples.all`, `SegmentMayorsSamples.all` (`pagedSamples` over two or three entries, default filters, no types, no viewer entry) and `DiveSiteRegularsSamples.all` (to be read). None has an unavailable star, an unstar in flight, a failed unstar, sign in again, several types, a chosen bracket, imperial units or the viewer's own place.
- **A length of time:** the leaderboard's time is `StatText.duration` ("3:50"), which `Stats.duration` already reads; no new duration text is needed.

## Goals / Non-Goals

**Goals:**

- The four pages, native, saying and offering exactly what the shared pages do.
- Every board choice's options and labels, the date, the segment type and the counts written once in Kotlin, which the Compose pages and the segment page's cards ask too.

**Non-Goals:**

- The segment page, its map, segment browse, creation and the dive site page.
- Pull to refresh, swipe to unstar, or a sheet of filters: the shared pages show neither, and the native ones follow them.

## Decisions

### 1. All four in one change

They share their rows, their filters and the viewer's place; Starred segments shares only the paged list, but it is small and from the same feature. Together they need one design-system piece (the filter menu) and one `PagedList` change, which would otherwise be split across changes.

### 2. Probe first

Throwaway Swift tests on a cloned iPhone 17 simulator (iOS 26.0), and the generated Swift read, recorded in the roadmap under "Phase 4 probes (segment lists)":

- The paged items cast as `StarredSegment`, `LeaderboardEntry`, `MayorEntry` and `DiveSiteRegularEntry`, with `SegmentView?` nil for a gone star, and `entry.effort.elapsed` and `startedOn`, `reachedOn`, `efforts`, `visits`, `rank` read as Swift types; `viewerEntry` nil and set.
- `boards` (empty `persistentListOf()` in every sample) through `elements(as: String.self)`, and whether the views need it at all after decision 3.
- `BoardChoice.options`, an `ImmutableList` of a contract class, through `elements(as: BoardOption.self)`, and `BoardOption.value` as `String?`, once decision 3 lands; that `BoardOption` has a Swift type because an exported function names it.
- An event built from Swift with a nil value (`AgeGroupChanged(ageGroup: nil)`).
- That the samples' first-page states have `hasAnswer` false, so a native board must keep its filters over a first page (decision 5).

### 3. Text and conditions in Kotlin

- **`:app-common-api`, package `app.boards`.** `@Immutable data class BoardOption(val value: String?, val label: AppText)`; `@Immutable data class BoardChoice(val options: ImmutableList<BoardOption>, val chosen: String?, val narrows: Boolean)` with `label: AppText` (the chosen option's label, else the token written as itself, as `ChoiceChip` showed an unlisted value), `offersMenu: Boolean` (two or more options) and `isChosen(option)`, which also gives `BoardOption` its Swift type. `RankBoardTexts`: `scope(chosen)` (Everyone, Following), `rank(rank)` (written, never grouped: it's a place, not a quantity), `reached(day: AppText)` ("Reached …").
- **`DateText`** (`app.text`): `date(year, month, day)` (`zones_history_date` with `month_short_N`, day and year written) and `isoDate(text)` (`YYYY-MM-DD`, or the text itself). `longDate(HistoryDate)` and `effortDateLabel` resolve them, so the threshold history and every effort list keep their words.
- **`SegmentTexts`** (`:app-common-api`, package `app.segment`): `sportGroup(sport)` and `type(sport, kind)` (ski lift, ski run, or the group), which `segmentSportLabel` and `segmentTypeLabel` resolve.
- **`SegmentBoardTexts`** (`:feature-segment-api`): `leaderboardPeriod(chosen)` (All time, This year, This month), `mayorPeriod(chosen)` (the eight windows, in `MayorPeriod.all`'s order), `type(board, boards)` (null without a board; one option when the segment lists fewer than two, so `offersMenu` is false and the UIs show the label; a type's label is its sport's, or E-bike), and an entry's `effortDay(entry)`, `reached(entry)` and `efforts(entry)` (the plural fed `formatWhole`). `StarredSegmentsTexts.summary(segment, units)` names a new `starred_segments_summary` ("%1$s · %2$s") for the length and type, as the invites list's summary did.
- **`LeaderboardBracketTexts`** (`:feature-segment-api`, apart from `SegmentBoardTexts` to keep each object small, and with the bounds a private data class: the Swift-export source check refuses a `Pair` even in a private function): `ageGroup(chosen)`, `weightClass(chosen, units)` (the kilogram classes, or pounds converted and rounded, a class ending a pound before the next), `gender(chosen)`, each with its "All …" option first and `narrows` while one is chosen.
- **`DiveSiteRegularsTexts`** (`:feature-divesite-api`): `period(chosen)`, `reached(entry)`, `visits(entry)`.
- **Getters on the states.** `SegmentLeaderboardState` and `SegmentMayorsState`: `periodChoice`, `scopeChoice`, `typeChoice`, `ageGroupChoice`, `genderChoice`, `weightClassChoice(units)`, `accents(entry)`. `DiveSiteRegularsState`: `periodChoice`, `scopeChoice`, `accents(entry)`. `StarredSegmentsState`: `canOpen(starred)`, `isRemoving(starred)`, `signInError: UiError?` and `showsEmpty`.
- `AppIconButton` gains `filled:` (the icon's selected form), for the starred segment's filled star.
- **The Compose UIs ask them.** `RankRow` takes its rank as text, and `RankBoards.kt` gains `BoardChoiceChip(choice, onSelect)` (a `MenuChip`, selected while the choice `narrows`; a plain label when it doesn't `offersMenu`) and `BoardSegments(choice, onSelect)` (an `AppSegmentedControl` of the options), replacing `ChoiceChip` and `ScopeControl`; `BoardParts.kt` keeps only the segment filters' layout. Each UI's own period `when` goes. The Mayor and Regulars cards on the segment and site pages ask `efforts` and `visits`. `commonTest`s cover every branch; `SharedTextNamesTest` (common names) and a segment `-ui` names test check every name chosen.

### 4. The filter menu in the design system

`AppMenuChip(label: Text, filled: Bool, selection: Binding<Value>, options: [Value], optionLabel: (Value) -> Text)` in `Design/`: a SwiftUI `Menu` holding an inline `Picker` (the menu checks the chosen one, as `AppPickerRow`'s does), its label the choice and a chevron in a capsule, the bordered-prominent style while `filled` and bordered otherwise, so it reads as the shared filter chip does. No app types. A gallery entry (`menuChips`, both looks) and its reference. Rank-board screens wrap it in a `BoardMenu(choice:send:)` beside the views, which reads a `BoardChoice`; one that doesn't `offersMenu` is its label as text.

### 5. `PagedList` keeps a screen's rows where it must

Two additions, each defaulting to today's layout so every existing page and reference is unchanged:

- `after:` — rows after the items, before the empty message and the next page's status: the viewer's own place, as `rankBoardRows` orders it.
- `keepsTop: Bool` — until the first answer, the list is still drawn with `top`, and the first page's progress or failure follows as a stacked status row, as the shared board keeps its filters over a new board's first page. Without it the first page has the screen to itself, centred, as now.
- Both are shown in the gallery (`pagedWithRows`: `after`, `keepsTop`) with references, which is what checks them: a SwiftUI list's rows can't be read back from its hosting view in a unit test.

### 6. The four views

- **`StarredSegmentsView`**: while `signInError` is set, a list of that notice alone (the paging is disabled then, and the shared page shows the notice with no empty message, first page or next page); otherwise `PagedList(state.segments, of: StarredSegment.self, errorKind: .LoadStarredSegments, id: { $0.uri.value })` with the failed unstar as `ErrorMessage(layout: .row)` in a status row on top. A row: `AppItemRow` with the segment's current name and `summary(segment, units)` as its line, opening `Open(uri)` when `canOpen`, or the unavailable text as its title, opening nothing; trailing an `AppIconButton(.star, filled, label: .segmentUnstar)` disabled while `isRemoving`. The empty text only when `showsEmpty`. `pageTitle(Text(.starredSegmentsTitle))`.
- **`SegmentLeaderboardView`, `SegmentMayorsView`, `DiveSiteRegularsView`**: `PagedList(…, keepsTop: true)`; top: a status row of the segmented choices (period and scope on the leaderboard, scope on the others), then a status row of the menus that wraps onto further lines (`FlowRow`, a small `Layout` in `Design/`: a list cell clips a row scrolled sideways, so the first recording's edge-to-edge scroll stopped 16 points short of the screen's edge) (type and brackets; window first on Mayors; window alone on Regulars); rows `RankRow` (below); after: a "Your place" section with the viewer's `RankRow` when `viewerEntry` is set; the empty text. Titles: the segment's name, `.mayorsTitle(name)`, `.diveSiteRegularsTitle(name)`. Weight labels take the environment's `stats.units`.
- **`RankRow`** (`Screens/RankBoards.swift`, shared by the three, beside `BoardFilters`, `BoardSegments`, `BoardMenu` and `YourPlace`): an `AppItemRow` whose leading is the rank (headline, monospaced digits, a fixed width, the theme's tertiary amber (`LikeColor`) when accented, as the shared row's tertiary) beside the `Avatar`, a 36-point `Avatar` (the shared row's size), the athlete's shown name as its title, the day line under it in the footnote's size (so "Reached" and a date keep to one line beside a usual count), and the value (time or count) as its summary in the primary colour, opening the activity or profile with the disclosure mark. The time is `Stats.duration(seconds:)`.
- Registered in `ScreenRegistry.app` in one commented block after the upload queue's; `KotlinTypes.swift` gains one block at its end (the four screens, states and events, `StarredSegment`, the three entry types, `SegmentView`, `BoardChoice`, `BoardOption`, the text objects) and the imports `FeatureSegmentApi` and `FeatureDivesiteApi`.

### 7. Samples, previews, snapshots

- `variants` beside `all`, whose keys are `ContentLoadingConformanceTest`'s and the Android screenshot set's: `StarredSegmentsSamples.variants` (`unavailable`: a gone star and one unstarring, `unstarFailed`, `needsSignIn`); `SegmentLeaderboardSamples.variants` (`filtered`: ride and e-bike types, an age group and a weight class chosen, a fourth entry, the viewer's place at 140; `firstPage`: filters chosen and the first page loading); `SegmentMayorsSamples.variants` (`filtered`: this week, a type menu, the viewer's place); `DiveSiteRegularsSamples.variants` (`lastYear` with the viewer's place).
- Previews: `SamplePages` over each page's samples and variants inside a `NavigationStack`; the leaderboard's in imperial units too.
- `SegmentListsSnapshotTests`: every sample and variant of the four pages on an iPhone, light and dark (the leaderboard's `filtered` also imperial), with `everySampleHasASnapshot`.
- `SegmentListsTextTests` (Swift Testing): the spec's scenarios on the samples (rows' titles and lines, unstar availability, the choices' labels and `narrows`, the type label versus menu, accents, the days and counts, pounds).

### 8. Walking the pages

On the signed-in iPhone 17 Pro and iPad Air 11-inch (iOS 26.0), only when no other session is walking: alice's own profile → Starred segments; a segment page → its leaderboard and Mayors, each filter tried once; a dive site → Regulars; back by the button and the swipe; the iPad upright and on its side. The walk writes nothing: if alice has no stars, efforts or reachable segments, the pages are seen as they are (empty or unreachable) and what couldn't be seen is listed under "Not verified", rather than starring segments or recording efforts without the user's agreement. The iOS 18.4 build is launched to sign-in only.

## Risks / Trade-offs

- **[Menus in a horizontal scroll inside a list row]** → A `Menu` in a scrolling row may lose its taps to the row or the scroll; checked on the simulator, and the row is a status row with no button of its own.
- **[`keepsTop` changing what a first page looks like]** → Only the four new pages set it; the default keeps every existing reference.
- **[Nothing to see on the test account]** → Rests on snapshots and text tests; listed as not verified.
- **[A thousand efforts now grouped]** → A visible change on every platform ("1,204 efforts"), consistent with ui/number-format; noted in the proposal.

## Migration Plan

None: the shared pages stay on every other platform and as the fallback on iOS for anything not registered.
