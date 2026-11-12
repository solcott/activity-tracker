# Proposal

## Why

On iOS every map is still the shared Compose UI's MapLibre view, so every page with a map stays a fallback page: the activity page, the
segment page and its enlarged map, segment browse, the dive site page and the invite's route preview. A native map view is what each of
them waits for. The dive site page is the cheapest first page for it: unlike the segment page it has no chart, and everything else it
shows (activity cards, the Regulars board it opens, the place-name sheet) is native already, while what it says is still chosen inside
composables a native page can't ask.

## What Changes

- **A native route map on iOS** (`RouteMapView`), a SwiftUI MapKit `Map` any native page can place: it draws the line Kotlin serves to
  the Compose map, each piece smoothed, on a white casing, with its breaks' dashed connectors, the stop and resume markers and the start
  and end markers where the viewer's map shows them, framed north-up around the whole route with the shared margin and closest zoom. It
  is static by default (a drag scrolls the page), opens an enlarged map when given one (a tap or its expand button), and can be
  interactive (pan and zoom, north-up, with Recenter) for the enlarged map and segment browse later. MapKit's tiles follow the light and
  dark appearance and carry its own credit.
- **Previews and snapshots draw no tiles**: outside a signed-in session the map draws the route as `RouteSketch`, the same line preview
  the native card draws, so snapshots stay deterministic.
- **Kotlin decides what a route map shows, both UIs ask** (`RouteMap` in `:app-common-api`): the smoothed pieces, where the start, end
  and break markers go (the owner's markers at the ends others see), the route's bounds, and the camera that frames them in a map of a
  size (Web Mercator, a margin, at most zoom 16). The Compose `ActivityMap`, its line preview and the feed's map images ask the same
  marks instead of their own copies, and the feed's map images the same framing. Nothing changes on Android, desktop or web.
- **The dive site page is native on iOS** (`DiveSitePage`), registered in `ScreenRegistry`: an inset grouped list titled with the site's
  current name, its centre and radius on the native map, "originally" its old name, the radius in the viewer's units, who created it,
  OpenStreetMap's credit and the data licence, a status note with Open the original for a duplicate, the top Regular opening the board,
  the conditions in the viewer's units with the month-by-month lines, the names (Propose a name, the open proposals with votes, or Edit
  and Delete on the viewer's own, the history behind a disclosure, a failed vote), then the dives as native activity cards, page by page.
  The place-name sheet and its delete confirmation show over it (`placeNames(_:)`).
- **Kotlin chooses what the page says, both UIs ask** (`DiveSiteTexts` in `:feature-divesite-api`; `PlaceTexts` and `PlaceNameTexts`
  in `:app-common-api`, shared with the segment page): the radius, the creator, the credit and licence, the status label, help and
  whether Open the original shows, the Regulars card, the conditions' lines, the dives' heading and empty message, and the names
  section's lines, totals, vote labels and history entries. The Compose dive site page, segment page and names section ask the same.
- **Samples, previews and snapshots** of the page in every shared sample and new variants (a renamed site with proposals and history, a
  duplicate, the viewer's own proposal, a failed vote), light and dark; the registry entry and its test.

What waits (open items in the roadmap):

- **The segment page** keeps the shared UI until native charts exist (its elevation profile); it can place `RouteMapView` and the names
  section as they are.
- **The enlarged maps** (`SegmentMapScreen`, the activity's route viewer), **segment browse** (many segments and a search box over a
  map), **the activity page** and **the invite's route preview** stay shared; the map's interactive mode and expand action are there
  for them, and the highlighted stretch a new segment shows is left for the create-segment page.

Out of scope: MapLibre on iOS (the roadmap's earlier choice; see design), any change to Android, desktop or web beyond the shared rules
moving, and the segment page itself.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/ios-design-system`: a native route map, static, expandable or interactive, with a deterministic stand-in in previews and
  snapshots.
- `dive-sites/site-records`: the dive site page is native on iOS.
- `dive-sites/site-names`: the site's names and their sheet are native on iOS.
- `dev/shared-display-text`: a route map's pieces, markers and framing, and the dive site page's and names section's text, are chosen in
  shared code.

## Impact

- **`:app-common-api`:** `RouteMap`, `MapLine`, `MapBreak`, `MapBounds`, `MapFrame`; `PlaceTexts`, `PlaceNameTexts`; tests.
- **`:app-common-ui`:** `ActivityMap`, `RoutePreview`, `RouteSnapshots`, `fitCamera`, `RouteMarkers` ask `RouteMap`; `PlaceNames`,
  `PlaceStatus`, `CreatedBy` ask the new texts.
- **`:feature-divesite-api`, `-ui`:** `DiveSiteTexts`, `DiveSiteState.viewerDid` and `revealed`; `DiveSiteUi` asks the texts.
- **`:feature-divesite-presenter`:** fills `viewerDid` and `revealed`.
- **`:app-fixtures`:** `DiveSiteSamples.variants`.
- **`iosApp`:** `RouteMapView` (MapKit, the system's own), `DiveSitePage`, `PlaceNamesSection`, `RouteSketch` taking optional ends,
  `ActivityCardView(likes:)`, `\.liveMaps`, type aliases, samples, previews, snapshot and logic tests, the registry entry.
- **Docs:** the roadmap's stack table (maps), phase 4 entry, findings and open items; `.agents/rules/compose-screens.md` and
  `adaptive-ui.md`.
- **Risk carried:** Apple's tiles look unlike OpenFreeMap's on the other platforms; snapshots compare only on iOS 26.0 and never show
  tiles; the walk can only show sites the local stack has.
