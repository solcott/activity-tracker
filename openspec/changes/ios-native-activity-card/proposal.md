# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time, and the activity card is
now the blocker: the feed, the profile, the gear item page, a dive site's page, Moderation and an import batch all list activities as
the shared `ActivityCard`, so none of them can go native until the card has a SwiftUI twin that says and decides exactly what the shared
one does. The card also builds much of its text inside composables (the start time on the activity's own clock, whom it was done with,
the moderation notices, the stat lines, the locks), which a native card can't ask.

The feed itself can't be the first page to draw it yet: its filter bar is a SubCircuit sub-screen, whose presenter only runs inside the
Compose UI (the presenter host runs screens, not sub-screens), and its timeline mixes the card with post cards, import cards, upload rows,
two prompts and the report and quote sheets, none of them native. One batch import's page (`ImportBatchScreen`) lists nothing but cards
under month headings, so it is the first page drawn with the native card, and the feed follows in a later change.

## What Changes

- **A native activity card on iOS.** A SwiftUI card drawn from Kotlin's choices: the athlete's avatar (blurred under an account label)
  and name, the visibility badge of a non-public activity, the start time on the activity's own clock, whom it was done with, the
  moderation notices, the sport icon and title (or its type), a dive's site, two lines of description, the stats (distance, time and
  pace or speed; depth, dive time and water temperature for a dive), the owner's locks on categories stored narrower, the route preview
  and photos in one sideways row (blurred until revealed, placeholders when taken down), and Like with the like and comment counts.
  Tapping the card opens the activity, the author line the athlete, a photo the gallery, and Like likes, as the shared card does.
- **The route preview is a line drawn on a `Canvas`**, from points Kotlin places (the same smoothing, breaks, dashed connectors and
  markers as the shared line preview), not a map: the native card shows the line preview until native maps exist.
- **An import batch is native on iOS.** `ImportBatchView` lists the batch's activities as native cards under a heading per month, page
  by page, with a failed like at the top, as the shared page does. It is pushed over the shared feed from an import card.
- **Kotlin chooses the card's text and conditions, both UIs ask.** The start time on the activity's clock (a time of day can now carry
  its UTC offset), the together-with line, the moderation labels and notices, the stat lines, the locks' lines, the counts, whether Like
  is offered and given, what the photos show, and the batch page's month headings. The Compose card and batch page ask the same, with no
  visible change on Android, desktop or web.
- **Samples, previews and snapshots** of the card in each case it draws differently (untitled, a group, photos, blurred and revealed,
  the owner's with locks, a warning, taken down, a dive, a strength workout, a date-only start, a long title at a large text size), and of
  the batch page in every shared sample; a registry entry and its test.
- **The roadmap** records the probes, the findings, and that the feed waits for the filter bar, posts and sheets.

Out of scope: the feed, the profile, the gear item page, the dive site page and Moderation going native; a map under the route line;
the feed's import card and upload rows; comments.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/ios-design-system`: a native activity card that every native page listing activities uses.
- `feed/import-cards`: the batch screen is drawn natively on iOS.
- `feed/feed-route-map`: a native iOS card shows the route as its line preview, without a map.
- `dev/shared-display-text`: the card's start time, together-with line, moderation notices, stats, locks and counts, and the batch page's
  month headings, are chosen in shared code.
- `dev/ios-swift-interop`: a route's shape reaches Swift as placed points, and a time of day as the activity's own clock reads it.

## Impact

- **`:app-common-api`:** `AppText.Time` gains an optional UTC offset; the card's content and texts (`ActivityCardContent`), moderation
  texts, the route's placed shape (`RouteShape`) and `narrowerCategories` move here; tests.
- **`:app-common-ui`:** `ActivityCard`, `startTime`, `ModerationNotice`, `blurLabel`/`warnLabel`, `CategoryLock`, `diveCardStats` and the
  together-with line resolve the shared choices. `RouteMarkers`, `RouteLayers` and `CanvasFit` are left as they are (another branch is
  reshaping them).
- **`:feature-feed-api`:** `ImportBatchTexts` (month headings); **`:feature-feed-ui`:** the batch page asks it.
- **`:app-fixtures`:** `ActivityCardSamples` and the batch page's variants.
- **`iosApp`:** `ActivityCardView`, `RouteSketch`, `MediaStrip`, `ImportBatchView`, `CatalogText.time` with an offset, type aliases,
  samples, previews, snapshot and text tests, the registry entry.
- **Docs:** the roadmap's phase 4 entry, probes, findings and open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** snapshots compare only on iOS 26.0; iOS 18 can't be walked signed in; reaching a batch page on the local stack takes
  a batch import; other sessions share the signed-in simulators.
