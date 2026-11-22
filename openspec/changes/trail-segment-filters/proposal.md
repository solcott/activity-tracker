# Proposal

## Why

A long trail or a resort can list dozens of segment-group rows on its Trail page, and the only way to narrow them is the sport filter browse opened it with, which can only be cleared ("Show all sports"), never changed. Now that every row has a rating level (`segments/trail-ratings`), the page has what it needs to let an athlete find "the blue downhill runs on Ann's" without new data from the AppView. This is the first piece of the Trail & Segment design canvas's Trail page.

## What Changes

- Add a search field above the Trail page's list that narrows rows by name. It matches sections by name too.
- Add filter chips: sport (All plus each sport group the path's segments have), difficulty (the rating levels its rows are drawn in, plus Unrated), direction (Both ways, or one way in a given direction), and length (three bands in the athlete's units). Each chip shows only when its choice would narrow the list.
- Add a sort choice: Longest (today's order and the default), Shortest, Name, Most climbing, Easiest first, Hardest first.
- The map draws only the rows the list shows. A selected row that the filters hide is no longer selected.
- When nothing matches, the page says so and offers to clear the search and filters.
- The search, filters and sort last while the page stays open, including after coming back from a segment page. A new visit starts with them cleared, apart from the sport browse opened it with.
- **The sport chips replace "Show all sports".** The page opens on the sport browse chose, as before, but any sport, or All, can now be chosen.
- On iOS the page searches in the navigation bar and keeps the filters and sort in one toolbar menu, using the system's own controls.

## Capabilities

### New Capabilities
- `segments/path-filters`: how the Trail page's list is searched, filtered and sorted, and how the map and the selection follow it.

### Modified Capabilities
- `segments/path-page`: "The Trail page keeps the browse sport filter" changes. The browse sport is now the starting choice of the sport chips, which can change it, instead of a filter that can only be cleared.

## Impact

- **`feature/segment/api`:** `PathState` and `PathEvent` gain the query (the app's `TextFieldState`), the filter and sort choices, the options each chip offers, and the events that set them. There is a new pure `PathFilter`, which filters and orders `SegmentGroupRow`s and sections, with tests in `commonTest`.
- **`feature/segment/presenter`:** `PathPresenter` keeps the choices retained, filters `PathContent` and the map lines, and clears a hidden selection. `PathPresenterTest` covers it.
- **`feature/segment/ui`:** `PathUi.kt` gets the search field, an `AppFilterChip` row with menus for the multi-choice filters, the sort menu, and the no-matches state. `ShowAllSports` and its button go.
- **iOS:** `PathPage.swift` adds `.searchable`, a toolbar filter menu and the no-matches state. iOS snapshot samples are updated.
- **Resources:** new strings in `strings.xml`, then `generateIosResources`. New `PathSamples` in `:app-fixtures`, with screenshot references updated.
- **No changes to the lexicon, the AppView or migrations.**
