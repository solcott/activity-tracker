# Proposal

## Why

Both type pickers (the feed filter's multi-select sheet and the Record/Manual/Import single-select sheet) list every sport with all of its sub-sports. That makes a long list to scroll when the athlete usually wants a whole sport. The two sheets also draw that list separately, with near-identical code.

## What Changes

- Sports in both type pickers can be collapsed and expanded with a chevron on the sport's row, separate from the row's select/toggle action. Each sport starts collapsed whenever a sheet opens.
- A partly selected sport or category in the feed filter shows its checkbox in the mixed (indeterminate) state, as Material's and Apple's guidelines do; this replaces the category header's "Some" text. In the record picker, a collapsed sport whose sub-sport is the chosen type names it under the sport's label. A selection never hides without a trace.
- A sub-sport listed without its own sport (a shortcut such as Indoor Ride under Gym) stays a top-level row and can't be hidden.
- In the record picker, a search shows every match, expanded, while a query is typed.
- The category list and the "Quick" chip row move into one shared composable that both sheets use. This removes the duplicate list code.

## Capabilities

### New Capabilities
- `record/activity-type-picker`: the single-select activity type picker used when recording, entering or importing an activity (its search, quick types and collapsible sports).

### Modified Capabilities
- `feed/feed-filter`: the type picker's sports collapse, collapsed by default, with partial selections shown as mixed checkboxes.

## Impact

- `composeApp`: new `ui/SportTypeList.kt`; `feedfilter/FeedFilterUi.kt` and `record/ActivityTypePicker.kt` use it. A new string for the chevron's content description; `feed_filter_category_some` goes. `ui/adaptive` gains `AppTriStateCheckbox` (Material's `TriStateCheckbox`, or a minus circle on iOS), and detekt's `UseAdaptiveComponents` maps `TriStateCheckbox` to it.
- No change to `:app-screens`, `:app-presenters`, lexicons or the AppView: collapse state is UI-only.
