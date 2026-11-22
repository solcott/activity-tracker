# Design

## Context

`PathPresenter` loads `GetPath.Output` once and reworks it with `contentOf(output, sport)` into `PathContent`: the groups (`SegmentGroupRow`, longest first) and `sectioned` (`PathListItem.Group` / `.Section`). It builds `mapLines` from the path's lines, which are muted, and each group's shown segment. Changing the sport only regroups what already loaded. Each `SegmentGroupRow` already carries what the filters need:
- `rating`, from `TrailRating.of(shown)`
- `ways` (`SportWay`, with its direction and whether it goes both ways)
- `shown.distanceMeters`, `shown.elevationGain` and `shown.currentName`

So nothing in this change needs the AppView. The Compose UI and the native SwiftUI page both draw `PathState`, so any rule that should hold on both platforms belongs in Kotlin.

## Goals / Non-Goals

**Goals:**
- One pure, shared function decides which rows show, in what order, and which options each chip offers. Both UIs only draw it.
- Choosing or typing does not refetch.

**Non-Goals:**
- No search on the server and no filters on Trails & roads browse itself.
- Choices are not saved across visits or app restarts.
- No filtering by trail attributes beyond the rating level, such as surface or width.

## Decisions

**A pure `PathFilter` in `feature/segment/api`.**
- *What it is:* `data class PathFilter(query, levels: Set<TrailRating?>, ways: Set<WayChoice>, lengths: Set<LengthBand>, sort: PathSort)`.
- *What it provides:*
  - `apply(content: PathContent, units): PathContent` returns the filtered `groups` and `sectioned`. A section keeps the rows that match, or all of them when its name matches. A section left with one row becomes a plain `Group`, matching the existing fold rule. A section left with none drops out.
  - `options(content, units)` returns the levels, ways and bands that are present, so each chip shows only when it narrows the list.
  - `pruned(options)` drops choices that no longer apply.
- *Why here:* the api module is exported to Swift, and the rules are tested in `commonTest`, as `SegmentsMap` and `TrailRating` are. The alternative was filtering in each UI, which would put the rules in two places and drift.

**Units come from `UnitsRepository` in the presenter.**
- The length bands depend on the unit system. The presenter collects `units.units` the way `ActivityDetailPresenter` does and passes it to `apply`, so `PathState` holds rows that are already filtered.
- The alternative was letting the UI pass its `LocalUnitSystem` back as an event. That is a round trip, and the iOS page would need it too.

**Search with the app's `TextFieldState` in `PathState`.**
- This follows browse (`MapBrowseState.query`).
- Matching folds case and strips accents with the normalizer the name search already uses, if one exists in `:core` (check `SegmentBrowse`'s local filter first). Otherwise a small `lowercase` plus Unicode-decomposition helper in api.
- Typing filters on every change. There is no debounce, since it all runs in memory.

**The filtered content drives everything below it.** `PathPresenter` computes `shown = remember(content, filter, units) { filter.apply(content.data, units) }`.
- `mapLines` is built from `shown.groups`, plus the path's muted lines.
- `items`, `sections`, `groups` and `empty` read the unfiltered `content` only to decide "no segments here yet". A new `noMatches` (filtered empty, unfiltered not) drives the no-matches state.
- A `SideEffect` clears `selection.selected` when it's no longer among `shown.groups`.
- `reveal` and `TapRow` look rows up in `shown`.

**Events.**
- `QueryChanged` is not needed: the field state is observed through `snapshotFlow`, as browse does.
- New events: `SportChanged(sport?)`, which replaces `ShowAllSports`; `ToggleLevel(TrailRating?)`; `ToggleWay(WayChoice)`; `ToggleLength(LengthBand)`; `SortChanged(PathSort)`; and `ClearFilters`.
- The choices are held in one `rememberRetained` `PathFilter`. That is retained, not saved, matching `detailsExpanded`, so a new visit starts clear.
- Changing the sport runs `pruned`.

**Ordering.** `PathSort` compares `SegmentGroupRow`s:
- Longest uses distance descending, then name.
- Shortest uses distance ascending.
- Name uses the shown display name, compared case-insensitively.
- Most climbing uses `elevationGain` descending, with nulls last.
- Easiest and Hardest use the level's ordinal, with unrated last.

Ties break by longest, then name. Sections take their first row's position under the same comparator. This generalises the current "longest row" placement in `itemsOf`, which is refactored to take a comparator.

**Direction choices.** `WayChoice` is either `BothWays` or `OneWay(SegmentDirection)`. A row matches when, for the chosen sport (or any sport under All), one of its `ways` has `bothWays`, or has a one-way `direction` equal to the choice. The options list the choices present across the listed rows, and the filter shows only when there are two or more.

**Shared UI layout (`PathUi.kt`).**
- The header gets the search field (an `AppTextField` with a search icon and clear button), then one horizontally scrolling chip row:
  - the sport chips (`AppFilterChip`, as `SegmentBrowseUi` has them)
  - a divider
  - Difficulty, Direction and Length chips. Each opens a menu of checkable items. A chip names what it is set to: its filter's name while nothing is chosen, the choice itself when one is ("1–3 mi"), else the first and how many more ("Intermediate +1"); assistive technology reads the filter's name before it ("Length: 1–3 mi").
  - a Sort chip with a single-choice menu: the sort icon (`AppIcon.Sort`, Material's swap-vert, SF `arrow.up.arrow.down`) and the choice ("⇅ Longest"), read aloud as "Sort: Longest", so it doesn't pass for a filter
- The header stays above the list on both the phone and the wide layout.
- The no-matches state replaces the list body with a message and an `AppTextButton` "Clear filters".

**iOS (`PathPage.swift`).**
- `.searchable(text:)` is bound to a `TextFieldModel(state.query)`.
- A `ToolbarItem` holds a `Menu` with a sport `Picker` (inline), `Toggle`s for levels (with `RatingSwatch`), ways and bands in `Section`s, and a sort `Picker`.
- Its icon is `line.3.horizontal.decrease.circle`, switching to `.fill` while any filter is on.
- The no-matches state uses `ContentUnavailableView.search` with a Clear button.

## Risks / Trade-offs

- [The chip row grows long on a phone with every chip showing] → Chips show only when they narrow the list. The multi-choice filters sit behind menu chips rather than one chip per level.
- [Removing `ShowAllSports` touches the links and samples that use it] → Grep for `ShowAllSports` and `offersAllSports` and replace them with the sport chips. The `PathScreen.sport` deep-link parameter keeps its meaning.
- [Filtering rewrites section placement] → `itemsOf` gets one comparator and the existing section tests run unchanged under Longest.
- [Accent folding differs between platforms] → It is done in Kotlin common code, so Swift never matches on its own.
