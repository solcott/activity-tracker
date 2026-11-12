# Design

## Context

See proposal.md for why. The three forms' presenters already run in the presenter host; their states are plain contracts
(`EditActivityState`, `ManualActivityState`, `CreateDiveSiteState`) with shared sub-states (`ActivityDetailsFields`, `GearField`,
`VisibilityFields`, `ActivityTypeField`), each with its own sink. Native pieces exist for most of it: `AppTextField`/`TextFieldModel`,
`AppPickerRow`, `VisibilityFormSections` (Activity defaults, Accept invite), the gear editors' photo pick (`PhotoPick`, `IosApp.compressPhoto`),
the feed's type sheet with search (`TypePickerSheet`, multi-select), and `RouteMapView` over a `RouteMap`.

## Goals / Non-Goals

- Goals: native `Form`s registered in `ScreenRegistry`; every text rule asked of Kotlin; the Compose forms unchanged to the eye.
- Non-goals: a shared Swift base for every form; changing how the presenters decide anything beyond publishing manual's duration.

## Decisions

- **Edit's back is Cancel.** The stack's back button pops without asking the presenter, but Edit asks before dropping unsaved changes
  (`EditActivityEvent.Back` → `confirmDiscard`). The view hides the system back and its edge swipe (`pageTitle(canGoBack: false)`) and
  leads the bar with Cancel, which sends `Back`; Save trails it. This is iOS's own edit-form shape. Alternative considered: a state
  getter for unsaved changes so back shows only while clean; rejected, since Compose doesn't need it and the form would change shape as
  the athlete types. Manual entry and Create dive site pop with nothing to ask, so they keep the system back.
- **Three alerts from state.** `confirmDiscard`, `confirmNarrowing` and `confirmCoreCharts` are each a SwiftUI `.alert` bound to the
  state, sending the keep/cancel event when dismissed, as the gear form's delete does.
- **Shared Swift form pieces** in `Screens/ActivityFormSections.swift`: `ActivityDetailsSections` (title, dive site, description,
  photos over `ActivityDetailsFields`), `GearFieldRow` with `GearFieldSheet` (over `GearField`), and `ActivityTypeRow` with
  `ActivityTypeSheet` (over `ActivityTypeField`). The visibility is `VisibilityFormSections`, already shared.
- **Photos are picked several at a time** with `PhotosPicker(maxSelectionCount:)` up to the room left, each through `PhotoPick.app`,
  then sent together as one `DetailsEvent.PhotosPicked(photos:)` (the `List` constructor kept for Swift). A tile draws an existing
  photo through `RemoteImage` and a picked one from its bytes, as `GearPhotoView` does.
- **The type sheet is a new single-select sheet, not the filter's.** The filter's sheet drafts a set and applies it; this one chooses
  one type and closes. Its groups for a query are Kotlin's (`SportTypeGroups.matching(query, source)`, over `SportTypeSearch` and the
  catalog), and its sub-sport choices (`SportTypeGroups.subSportChoices(selected)` with `choiceLabel`), which the Compose picker asks too.
  The disclosure rows reuse the pattern of `TypeChoiceList`.
- **Manual's Save follows typing** through a state field, `durationEntered`, which the presenter fills from the duration's text in
  composition (`valid` returns it): a native view redraws only on a new state, and an equal state isn't published.
- **Text rules to Kotlin:** `GearFieldTexts` (`:app-common-api`, beside `GearField`): `summary(field)` (None, or the names joined with
  `AppText.joined`), `line(option)` (kind or type · visibility, · Retired), `collection(option)` and `notAdded(count)`.
  `ManualActivityTexts` (`:feature-record-api`): `distanceLabel(units)`, `temperatureLabel(units)`. `EditActivityTexts`
  (`:feature-edit-api`): `coreChartsBody(visibility)`. `CreateDiveSiteTexts` (`:feature-divesite-api`): `radius(meters, units)`,
  `unavailable(reason)`, `publicNote(namesDive)`; `CreateDiveSiteState.routeMap` as `DiveSiteState.routeMap` is.
- **The radius slider** is SwiftUI's `Slider` over the state's radius, stepping by 5 m, sending `RadiusChanged` as it moves; the
  presenter keeps it in range.

## Risks / Trade-offs

- [The sibling session edits `ScreenRegistry.swift`, `KotlinTypes.swift`, `Samples.swift`] → entries are added as their own blocks.
- [A `GearOption` or `DiveSiteView` list read through the erased collections] → read with `elements(as:)`; typed getters where a
  `ContentState` would erase them.
- [Release exports no fixtures, so a type named only by samples has no Swift type] → build Release before archiving.
- [Picking photos needs the photo library, which an empty simulator lacks] → snapshots draw picked tiles from sample bytes; the walk
  notes what couldn't be picked.
