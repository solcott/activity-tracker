# Design

## Context

See proposal.md for why. `GearState` holds a `ContentState<GearPage?>` (the item, null until it first loads), a `PagedState` of
`ActivityView`s, the owner's collections, linked devices and profiles, the mount picker, the component being unmounted, whether a mount
change saves, and the last error. `GearPage` names the lexicon's `GearView`, `GearUsage`, `GearComponent` and `GearMountView`, so they
have Swift types in Release too; `GearUsage.bySport` and `byYear` are plain lists of lexicon classes nothing exported names.

The native pieces this builds on: `ContentStateView`'s form without a value, `PagedList` (`keepsTop:`, `sections:`), `AppItemRow`,
`Thumbnail`, `pageTitle(_:actions:)`, `ActivityCardView` over `ActivityCardContent`, `CalendarDay`, `ShownClock` (`\.clock`) and the
environment's units (`\.stats`).

## Goals / Non-Goals

- Goal: the page reads and behaves as the shared one, with Kotlin deciding every word and condition.
- Non-goal: reshaping `ActivityCardView`, its texts or `PagedList` (two other sessions build on them now).
- Non-goal: changing what the Compose page shows.

## Decisions

- **Texts in `GearPageTexts` (`:feature-gear-api`), conditions as getters on `GearState`.** The texts are the page's own, so they stay in
  the feature's contract rather than `:app-common-api`; they build on `GearTexts`, `LabelText`, `StatText` and `LinkedSensorTexts`. The
  totals and breakdowns come out as lists of `GearFigure(label, value)` (both `AppText`), so Swift never reads `GearUsage`'s lexicon
  lists and the "which totals apply" rule is one function. Alternative: a getter per total; rejected, it would spread the rule over ten
  getters each UI must order the same way.
- **The unmount date's bounds are Kotlin's.** `unmountFirstDay(zone)` (the day after the mount began, in the viewer's zone) and
  `unmountInitialDay(now, zone)` (today, or the first day when later) on `GearState`, which Compose turns into picker millis and Swift
  into `CalendarDay`s. Swift passes `clock.now` and `clock.kotlinZone`, so snapshots fix them.
- **Mount dates stay ISO dates.** The shared page writes `LocalDate.toString()`; `GearPageTexts` keeps that so nothing changes for the
  other platforms. Writing them out as `DateText` is a separate, visible change.
- **One `List`, sections as the shared page orders its items.** The page is a `ContentStateView` (no value; it reads the typed
  `state.page`) holding a `PagedList` with `keepsTop: true`, whose `top` rows are the header and every section before the activities, so
  they stay while the first page of activities loads. Each card is a section of its own, as on the batch page; the Activities heading
  and an activity error are a section of their own before them, so the heading shows in every state.
- **Collections are ticked rows.** The shared page draws chips and an Add menu; natively a "Collections" section lists every
  collection with a check on those holding the item, as the native gear form does, each tap sending `ToggleCollection`.
- **Component actions are a `Menu`** on the row's trailing end (an ellipsis labelled by `gear_component_actions`), disabled while a mount
  change saves; tapping the rest of the row opens the component.
- **The mount sheet and the unmount calendar are `.sheet`s with their own `NavigationStack`**, bound to `state.picker` and
  `state.unmounting` (dismissing sends `DismissMount`/`DismissUnmount`). The calendar keeps its pick in `@State` until Unmount sends
  `ConfirmUnmount`, as the shared dialog confirms.
- **The Swift view is `GearItemView`**, not `GearView`, which is the lexicon class's name.

## Risks / Trade-offs

- [Parallel sessions edit the shared Swift files] → additions only, in commented blocks; the registry test's list of shared screens loses
  `"GearScreen"`, the one edit that isn't an addition, recorded as a merge note.
- [Release exports no fixtures] → every class Swift aliases is named by an `-api` signature (`GearFigure` by `GearPageTexts`); build
  Release before archiving.
- [The test account has no components, devices or photos] → the snapshots cover them; what the walk can't reach is listed under
  "Not verified" in tasks.md.
