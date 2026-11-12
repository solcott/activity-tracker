# Design

## Context

See proposal.md for why. `CreateSegmentPresenter` already runs in the presenter host and publishes `CreateSegmentState`: the drawn route
(`route`, `routeGaps`), the route's length, the chosen `start`/`end` in meters, a `SelectionCheck`, the stretch as a precision-5
`highlight`, the name's `TextFieldState`, `namesActivity`, `duplicates`, `limitReached`, `publishing`, `actionError`. Create dive site
(`CreateDiveSiteView`, ios-native-forms) is the same shape with a radius instead of a stretch, and its view is the template.

## Goals / Non-Goals

- Goals: a native `Form` registered in `ScreenRegistry`; every text rule asked of Kotlin; the Compose form unchanged to the eye (but for
  the corrected sample).
- Non-goals: a shared Swift base for the two place forms; changing the presenter's rules.

## Decisions

- **The highlight is part of `RouteMap`.** `RouteMap.of(polyline, gaps, ends, highlight)` smooths the highlight as the shared map does
  (`RouteCurve.smooth`) into `highlight: MapLine?`, and `sketch` places it with the route's own fit into `RouteShape.highlight`. It's in
  `equals`, so a moved stretch is a new state, but not in `key`, which the native map reframes on: dragging the slider moves the stretch
  and leaves the camera where it is. Alternative: a separate highlight parameter on `RouteMapView`, decoded in Swift; rejected, since
  decoding and smoothing are Kotlin's everywhere else.
- **The highlight's look is the shared map's**: wider than the route in the colour the shared map highlights with. Natively that's the
  catalog's `LikeColor`, which the segments map's selected run uses too, on a white casing on MapKit.
- **A custom two-thumb slider.** SwiftUI has no range slider. `RangeSlider` draws a track, the chosen span in the accent colour and two
  round thumbs; a drag moves the nearer thumb, kept on its side of the other. Each thumb is an accessibility element with
  `accessibilityAdjustableAction` stepping by 1 % of the range, named Start or End of the stretch and read as its position along the route (`CreateSegmentTexts.startThumb`, `endThumb`, `along`). It reports every change as it
  moves, as the Compose slider does, and the presenter clears duplicates for the new stretch.
- **Text rules to Kotlin:** `CreateSegmentTexts` (`:feature-segment-api`): `selection(state, units)` (the length and sport group, too
  short, too long, crossing a gap), `selectionFails(state)`, the slider's `range`, `startThumb`, `endThumb` and `along(meters, units)`, `unavailable(reason)`, `publicNote(namesActivity)` (sentences read in
  order, as the dive site's) and `duplicate(segment, units)` (name · length). `CreateSegmentState.routeMap`, as the dive site's.
- **The form's order follows Create dive site's**: a failure, the allowance and duplicates at the top where Publish's answer lands,
  then the map, the stretch section (its header the slider's title, the slider, the selection line red when it fails), and the name
  with the public note as its footer. Publish is in the bar.

## Risks / Trade-offs

- [The import session edits `ScreenRegistry.swift`, `KotlinTypes.swift`, `Samples.swift`, the registry tests] → separate blocks.
- [A custom slider's gesture fighting the form's scroll] → a horizontal-dominant `DragGesture` with a minimum distance; the walk checks
  it.
- [Re-recorded Android screenshots for the corrected sample] → looked at before committing.
- [Release exports no fixtures] → build Release before archiving.
