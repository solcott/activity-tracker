# Design

## Context

`FeedFilterUi.TypePickerSheet` (multi-select, checkboxes, category headers with a select-all checkbox and a "Some" label) and `ActivityTypePicker.ActivityTypeSheet` (single-select, check mark, search, plain category headers) each build the same `LazyColumn`: a sticky header per `SportCatalog.categories` group, then one `ListItem` per type, with a sub-sport indented 24dp when its own sport is in the same group. Each sheet also draws its own "Quick" `FlowRow` of `AppFilterChip`s.

In `SportCatalog.categories`, each sport is immediately followed by its sub-sports. A sub-sport can appear without its sport (a shortcut such as Indoor Ride under Gym), and a search can also leave a sub-sport without its sport.

## Goals / Non-Goals

**Goals:**
- One shared list and quick-chip composable for both sheets, with collapsing built in once.

**Non-Goals:**
- No presenter, `State` or `Event` changes. Collapse state isn't part of the filter or the form.
- Turning the record picker into a SubCircuit. That is the separate `activity-type-picker-subcircuit` change.
- Animating expand/collapse. Rows appear and vanish as items in the lazy list, and `animateItem()` can be added later.

## Decisions

**Collapse state is UI-only, in a `rememberSaveable` inside each sheet.** The set of expanded sport tokens starts empty, so each sport opens collapsed, and the sheet's composition holds it. Closing the sheet leaves the composition and drops the set, which is what "collapsed every time it opens" needs. Alternative: hold it in `TypePickerState` with an event. Rejected: the presenter has no use for it, and the record picker has no presenter-side picker state at all.

**Shared `ui/SportTypeList.kt` with slots, not a mode flag.** A `LazyListScope.sportTypeList(...)` extension takes the groups (`ImmutableList` of category to `ImmutableList<ActivityType>`), the expanded set, `onToggleExpanded`, `expandAll`, and slot lambdas for the category header's trailing content and each row's trailing content, row click and the hidden choice. It owns header styling, the visibility rule, indentation and the chevron. A `QuickTypeChips(types, isSelected, onClick)` composable covers the chip row. Each sheet keeps its own logic: the feed filter's `covers`/`ToggleType`/`ToggleCategory`, and the record picker's search, `outdoorOnly` filtering and close-on-choose. Alternative: one `TypePickerSheet(mode = Single|Multi)`. Rejected: the two differ in header, search, apply/cancel and dismissal, so a mode flag would branch everywhere.

**Visibility rule.** Within a group, a row is *nested* when it is a sub-sport and its bare sport is in the same group (the existing `nested` check). A nested row shows only when `expandAll` is true or its sport is in the expanded set. A sport row gets a chevron only when at least one nested row follows it in the group. The set is keyed by sport token, so expanding Running applies wherever Running is listed.

**Chevron is its own `AppIconButton`, placed before the row's trailing checkbox or check mark.** The row's own click keeps selecting or toggling, so behaviour stays the same for anyone used to it. It rotates `AppIcon.Chevron` by 90° when expanded, and sets `stateDescription` from `detail_section_expanded`/`detail_section_collapsed` and a content description naming the sport, matching `DetailSectionsUi.SectionHeader`.

**Partial selection: a mixed checkbox, not a "Some" label.** Material's and Apple's guidelines both mark a partly selected parent with an indeterminate checkbox (a dash), so a text label beside the box reads as out of place. A new `AppTriStateCheckbox(state: ToggleableState, onClick)` wraps Material's `TriStateCheckbox`, and on iOS draws `minusCircleFill` beside the existing `checkmarkCircleFill`/`circle`. The feed filter uses it for:
- The category header: `Selection.All`/`Partial`/`None` map to `On`/`Indeterminate`/`Off`.
- A sport row: `On` when the draft covers it, `Indeterminate` when it covers some of its sub-sports but not the bare sport, otherwise `Off`. This holds expanded too, like a category.
`feed_filter_category_some` is removed. The record picker is single-select with a check mark, which has no mixed state, so `sportTypeList` takes `hiddenChoice: (sport) -> ActivityType?` and a collapsed sport shows that type's label as the row's supporting text ("Run / Trail Run"), like a detail value in an iOS list.

**Search expands everything.** The record picker passes `expandAll = query.isNotBlank()`, so matches never hide behind a collapsed sport. The feed filter has no search and passes `false`.

## Risks / Trade-offs

- [Two click targets in one row on touch] → The chevron is a standard 48dp icon button. The row's `clickable` doesn't wrap it, so the targets don't overlap.
- [Stability] → The slot lambdas and the `ImmutableList` params keep the composable stable, and `composeStabilityCheck` verifies it.
- [Sticky headers plus collapsing change scroll position] → Collapsing removes items below the tapped row only, so the tapped row stays in place.
