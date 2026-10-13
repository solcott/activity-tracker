# Proposal

## Why

The feed filter's type sheet lists about 180 activity types across 20 categories, but unlike the record picker it has no search. Finding a type means scrolling and expanding sports by hand.

## What Changes

- The feed filter's type sheet gains a search field above the list. It matches the same way the record picker's search does: a type's own label, its sport's label, or the raw sport and sub-sport tokens.
- While a query is entered:
  - the list shows every matching type, with sports expanded and categories that have no matches hidden;
  - "No matching sports" shows when nothing matches;
  - the recent and common chips are hidden;
  - category headers show no checkbox and can't be tapped, so a tap never selects types that aren't visible.
- Sport rows still select the whole sport, as they do without a search. Selections made during a search stay in the draft when the query is cleared. The query resets each time the sheet opens.
- The matching logic moves out of the record picker into a helper shared by both sheets, so the two searches can't drift apart.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/feed-filter`: adds requirements for searching the type picker and for category headers being unavailable during a search.

## Impact

- `composeApp`:
  - `feedfilter/FeedFilterUi.kt`: the search field and filtered groups in `TypePickerSheet`.
  - `record/ActivityTypePicker.kt`: switches to the shared matching helper.
  - `ui/SportTypeList.kt`, or a new file beside it: the shared helper.
  - The existing `sport_picker_search` and `sport_picker_no_matches` strings are reused, so no new strings are needed.
- Unaffected:
  - `FeedFilterPresenter`, `FeedFilterState` and its events. The query and filtering stay in the UI, because labels are string resources the presenter can't read.
  - The catalog, the lexicon and the AppView.
