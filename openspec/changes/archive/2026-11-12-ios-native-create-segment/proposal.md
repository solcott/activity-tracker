# Proposal

## Why

Create segment is one of the last two screens the native activity page opens that iOS still draws with the shared Compose UI inside
the native shell (the other, import, moves in a sibling change). Its pieces are mostly native already: the route map, the form rows,
the duplicate rows and the bar's Publish from Create dive site. What it lacks is a highlighted stretch on the native map and a control
for choosing a stretch, a range with two ends.

## What Changes

- **Create segment is native on iOS** (`CreateSegmentView`), a grouped `Form`: the route as the athlete's privacy zones leave it on the
  native route map with the chosen stretch highlighted, a two-thumb range slider choosing the stretch, its length and sport group or why
  it can't be a segment, the name, the public note (and that it links to the activity when it will), any duplicates to open, the
  allowance reached, a failed check or publish, and Publish in the bar with progress. An activity no segment can be made from says
  why instead. Loading follows `ui/content-loading`.
- **The native route map draws a highlighted stretch** over the route, as the shared map does, on MapKit and in the line preview
  snapshots draw: `RouteMap.of` takes the highlight, and `RouteMap.highlight` / `RouteShape.highlight` place it. Changing it never
  reframes the map.
- **A native range slider** (`RangeSlider`, `iosApp/iosApp/Design`): two thumbs over one track, the span between them in the accent
  colour, each thumb its own adjustable element for VoiceOver.
- **Kotlin chooses what the form says** (dev/shared-display-text): the selection's line (length and sport group, too short, too long,
  crossing a gap) and whether it's a failure, the reasons no segment can be made, the public note, and a duplicate's line
  (`CreateSegmentTexts`, `:feature-segment-api`), and its map (`CreateSegmentState.routeMap`). The Compose form resolves the same texts.
- **The samples choose a real stretch**: the loaded sample's start and end were fractions of a meter of a 2.4 km route (the comment
  meant 0.2 to 0.6 of it); they become 480 m and 1,440 m, with a highlight, and variants cover the remaining states.

Out of scope: Android, desktop and web beyond the shared rules moving and the corrected sample; import (the sibling change); changing
how the presenter cuts or checks a stretch.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-creation`: creating a segment is native on iOS.
- `ui/ios-design-system`: the native route map highlights a stretch; a native range slider.
- `dev/shared-display-text`: the create-segment form's texts are chosen in shared code.

## Impact

- **`:app-common-api`:** `RouteMap.of(highlight:)`, `RouteMap.highlight`, `RouteShape.highlight`; tests.
- **`:feature-segment-api`, `-ui`:** `CreateSegmentTexts`, `CreateSegmentState.routeMap`; the Compose form asks them.
- **`:app-fixtures`:** the corrected loaded sample and `CreateSegmentSamples.variants`; Android screenshot references re-recorded.
- **`iosApp`:** `CreateSegmentView`, `RangeSlider`, the map's highlight, aliases, samples, previews, snapshots, the registry entry
  and its tests.
- **Docs:** the roadmap's phases, findings and open items; `.agents/rules/compose-screens.md` if a rule changes.
- **Risk carried:** the import session edits the same shared Swift files; entries are added as separate blocks.
