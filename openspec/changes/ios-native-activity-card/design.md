# Design

## Context

What exists, from the code (see proposal.md for why):

- **The shared card** (`app/common/ui/.../ui/ActivityCard.kt`, listed through `ActivityItems.kt`'s `activityItems` and `ActivityListCard`).
  From an `ActivityView` and the viewer it draws: `AuthorHeader` (avatar 32 pt, blurred by `ModerationDecision.mediaOf(null,
  author.labels)`, the name, `VisibilityBadge` beside it, and below it `startTime(record.startTimeValue())` and `TogetherWith`),
  `ModerationNotice(decision, isOwn, warn)`, the type icon and title (`record.title ?: activityTypeLabel`, the icon described only when
  there is a title), `DiveSite`, the description (2 lines), `StatsRow` (`diveCardStats` for a dive, else `distanceStat`, Time, and
  `Format.rateStat` while the speed was served), `CategoryLocks(narrowerCategories(activity))` on the owner's own, `MediaRow` (the route
  tile, `RouteMapTile`: a MapLibre snapshot over `RoutePreview`, then each photo, 160 pt high, blurred under `mediaOf(labels,
  author.labels)`, placeholders when taken down), and Like with "N likes · N comments". There is no gear line on the card; gear shows on
  the activity page only.
- **Text built in composables**: `startTime` (the activity's own offset, else the viewer's zone, a date-only start in UTC; `DayText.day`
  beside today on that clock; the time through `timeFormat()`), `togetherWith`, `blurLabel`, `warnLabel`, `ModerationNotice`'s choice,
  `narrowTierLabel`, the badge's description, `CategoryLock`'s "Heart rate · Only me" and its spoken line, `diveCardStats`, `distanceStat`,
  the Time stat, the counts. `AppText.Time` formats a moment in the device's zone only.
- **The feed** (`FeedUi`): its filter bar is `SubCircuitContent(state.filterScreen)`, whose presenter runs only inside Compose's
  `SubCircuitContent` (`:app-host` has no SubCircuit support); its timeline mixes `ActivityListCard` with `PostCard`s, `ImportCard`s, the
  upload and refused rows, the segments and groups prompts, and `ReportingHost` and `QuoteSheetHost`.
- **The batch page** (`ImportBatchUi`, `:feature-feed-ui`): `ListScreen` titled `import_batch_title`, a failed like, `pagedRefreshStatus`,
  a heading per month (`import_batch_month`, `monthName`, grouped in `LocalTimeZone`), `ActivityListCard(authorClickable = false)` per
  activity, `pagedFooter` with `import_batch_empty`. Its state: `name`, `activities: PagedState<ActivityView, Unit>`, `viewerDid`,
  `revealed`, `error`; events `Back`, `LoadMore`, `Retry`, `Activity(ActivityListEvent)`. Samples: `ImportBatchSamples` (two activities a
  month apart, no routes).
- **Native pieces to reuse**: `PagedList` (one section of items), `Avatar` and `RemoteImage` (Nuke, `heldphoto:`), `SportIcon`,
  `AppIconButton(filled:)`, `ErrorMessage`, `@Environment(\.stats)` and `\.clock` (`ShownClock`, whose `CatalogText` formats
  `AppText.Time` in its zone), `Labels`, `elements(as:)`.
- **Another branch** (`no-pair-api`) is replacing `Pair` and `Triple` in non-private signatures, including `RouteMarkers`, `RouteLayers`,
  `CanvasFit` and core-location's `TrackMath`/`RouteCurve`.

## Goals / Non-Goals

**Goals:**

- A native card that says, offers and hides exactly what the shared one does, laid out as an iOS card.
- Every choice the card makes written once in Kotlin, the Compose card asking the same, with no visible change there.
- A first page in the app drawn with it, so it is seen on a device and not only in snapshots.

**Non-Goals:**

- The feed, profile, gear item, dive site and Moderation pages; a map under the route; the import card; comments.
- Reshaping `RouteMarkers`, `RouteLayers` or `CanvasFit` (the other branch's); the Compose line preview keeps its own fit.
- A change to any contract beyond getters and text functions.

## Decisions

### 1. Probe first

Throwaway Swift tests on a simulator made for this change (iOS 26.0), results under "Phase 4 probes (activity card)" in the roadmap
before the views are built on them:

- `ImportBatchSamples`' erased items cast to `ActivityView` (named by `ActivityListEvent`), and its fields read typed: `likeCount`,
  `commentCount`, `viewer?.like`, `author.avatar`, `labels`, `media` as `ActivityMediaView.Image`, `group`.
- `ImportBatchEvent.Activity(event: ActivityListEvent.ToggleLike(activity:))` builds, and `Revealed`'s `contains` and `eventSink` are
  callable.
- A `List` of a contract class holding a `List` of a contract class (the route's pieces) reads as nested typed arrays.
- An `AppText.Time` with an offset through a scratch `TextSource` reads the offset.

### 2. The feed stays shared; the batch page is first

The feed can't be native in this change: its filter bar is a SubCircuit whose presenter the presenter host can't run (it runs screens,
not sub-screens), and its other items (posts, import cards, upload rows, prompts) and its two sheets aren't native. Making the host run
SubCircuits, and those pieces, is the feed's own change. The batch page lists only cards, under month headings, so `ImportBatchView` is
the first page drawn with the native card. It opens from an import card on the shared feed, which a batch import of two files on the
local stack makes (the uploads walk did the same).

Rejected: drawing the card in previews and snapshots only (nothing would show it on a device), and drawing the native card inside the
shared feed (a SwiftUI view inside a Compose `LazyColumn` per item, which phase 5 would delete).

### 3. The card's choices in `:app-common-api`

`ActivityCardContent(activity, isOwn)` (package `app.activitylist`, beside `ActivityListEvent`) holds what one card shows, computed once
from the view: `record` (the effective record), `decision`, `blur` (`mediaOf`), `warn`, `avatarBlur`, `takenDown`, `offersLike`
(`!isOwn`), `liked`, `media` (`mediaItems()`), `polyline`, `gaps`, `locks`. Text functions return `AppText`: `author`, `badge` and
`badgeDescription`, `start(now, zone)`, `together`, `notices` (a list of `CardNotice(text, isError)`), `title`, `typeDescription` (null
when untitled), `diveSite`, `stats(units)` (`LabeledText`s: a dive's depth, dive time and water temperature; else distance when it shows,
Time, and the rate while the speed was served), `lockLines` (each `CardLock(text, description)`), `blurLabel`, `counts`. `of(activity,
viewerDid)` decides `isOwn`. The Compose card builds one in `remember(activity, isOwn)` and asks it; `ActivityListCard` offers Like from
`offersLike`.

The rules move with it: `ModerationTexts` (blur and warn labels, the notices' choice, the own-blurred note), `CardStatsText` (the three
dive figures, which `diveOverviewStats` also asks), `narrowerCategories` (from `:app-common-ui`, same package, public), the badge and lock
lines (`VisibilityTexts.badge`, `badgeDescription`), and `togetherWith`. The composables (`blurLabel`, `warnLabel`, `ModerationNotice`,
`narrowTierLabel`, `CategoryLock`, `diveCardStats`, `togetherWith`) become thin resolvers. Each new closed set of names joins
`SharedTextNamesTest`.

- *Why one class*: the card asks about a dozen things of the same view; computing the effective record and the moderation decision once,
  and giving Swift one type to hold, is simpler than a dozen functions each taking the view and the viewer.
- *Alternative*: getters on each host's state (`ImportBatchState.isLiked(activity)`…): every page that lists activities would repeat them.

### 4. A start time on the activity's clock

`AppText.Time` gains `utcOffsetMinutes: Int? = null`, and `TextSource.time(epochSeconds, utcOffsetMinutes)` formats in that offset when
given, else in the source's zone. `StartText.of(start: FigureValue.StartTime, now, zone)` in `:app-common-api` is the rule `startTime`
had: the activity's offset, else the viewer's zone (UTC for a date-only start), `DayText.day` beside today on that clock, and " at " the
time unless date-only. `startTime` resolves it (the detail page's start line too), `ActivityCardContent.start` asks it with the record's
start value (`startTimeValue` moves to the api beside it). Compose's resolver formats an offset time with `timeFormat()` in that offset;
`CatalogText` formats it in `TimeZone(secondsFromGMT:)`.

- *Alternative*: a new `AppText` case for a wall-clock hour and minute. Rejected: a moment and an offset is what the record holds, and
  one case keeps one resolver path.

### 5. The route as placed points

`RouteShape.of(polyline, gaps, width, height, pad)` in `:app-common-api` (package `app`, beside `RouteEnds`) decodes the polyline, splits
it at the gaps (`SegmentedPolyline.pieces`), smooths each piece (`RouteCurve.smooth`), fits every point into the tile north-up with
longitudes shrunk by the first latitude's cosine (the same math as `canvasFit`), and returns `pieces: List<RoutePiece>` (each
`points: List<ShapePoint>`, `x` and `y` in points), `start`, `end` and `breaks: List<RouteBreak>` (`stop`, `resume`) from the unsmoothed
points, as the Compose preview marks them; null below two points. Swift's `RouteSketch` strokes the pieces with the accent colour 4 pt
wide, draws the dashed connectors and the markers with the radii, rings and colours of `RouteMarkers` (copied into Swift with a comment
naming their source), on a tile of the system's secondary grouped fill, and asks again when its size changes.

- *Why its own fit*: `canvasFit` is in `:app-common-ui`, which Swift can't reach, and the other branch is reshaping it. Once both land,
  `canvasFit` can ask `RouteShape`'s fit, a follow-up noted in the roadmap.
- *Why not MapKit's snapshotter*: a different map and credit from the other platforms, and the task keeps the card off native maps.

### 6. The native card

`ActivityCardView(content:, authorClickable:, revealed:, send:)` in `iosApp/iosApp/Screens/ActivityCardView.swift`, a `VStack` with the
shared card's spacing, drawn as a list row:

- **Author line**: `Avatar` 32 pt (blurred and unlabelled under `avatarBlur`), the name in the subheadline's weight with the badge (a
  small capsule, read as its description), the start and together-with lines in the footnote's secondary. A `Button` sending
  `OpenAuthor` when `authorClickable`.
- **Notices**: capsules, the taken-down one in red.
- **Title row**: `SportIcon` 24 pt and the title in the headline style, the icon hidden from VoiceOver when untitled and labelled with
  the type otherwise; the dive site with a location symbol; the description, 2 lines.
- **Stats**: an `HStack` of label (caption) over value (headline), spaced as `SpaceBetween`; `ViewThatFits` wraps them under each other
  at large text sizes.
- **Locks**: a `FlowRow` of lock symbol and line, each read as its description.
- **Media** (`MediaStrip`): with no photos the route tile alone, full width, 160 pt; with photos a horizontal `ScrollView` of the route at
  80% of the width and each photo at its aspect ratio (clamped as shared), 12 pt corners, 8 pt gaps. A photo is a `RemoteImage`; blurred
  (`.blur` with the label's name and Show over it, a tap sending the reveal) until `revealed`; a placeholder reading the taken-down
  photo's text when `takenDown`. A tap sends `OpenMedia(index)`.
- **Like and counts**: `AppIconButton(.like, filled: liked)` tinted `LikeColor` when given, while `offersLike`, then the counts.

The card is one list row of an inset grouped list, its own section. The row opens the activity with a tap and as its VoiceOver default
action; the author line, Like and the photos are borderless buttons that take their own taps. The views read the content's text through
`AppStrings` and the clock through `\.clock`.

### 7. The batch page

`ImportBatchView` titles itself `import_batch_title` with the name, puts a failed like at the top, and lays the pages out with
`PagedList`, which gains `sections:` (a function from the loaded items to sections, each with an id, an optional header and its items;
one unheaded section by default). `ImportBatchTexts.months(activities, zone)` in `:feature-feed-api` groups the activities by month of
their start in the viewer's zone, newest first, each with its heading (`import_batch_month`); the Compose page asks it too. The view puts
each card in its own section, the month's heading over its first. Events wrap as `ImportBatchEvent.Activity(event:)`; reveals go to
`state.revealed.eventSink`. Registered in `ScreenRegistry`.

### 8. Samples, previews and snapshots

`ActivityCardSamples` in `:app-fixtures` (package `app.activitylist`): a titled ride with a route and counts, an untitled run, a group of
four, photos, photos blurred (and the same revealed), the owner's Followers activity with locks and its Like gone, a warned one, the
owner's taken-down one with photos, a dive at a site, a strength workout, a date-only start, a route with a break. `ImportBatchSamples`
gains routes on its activities and `variants` (a failed like, three months). Swift previews show each through `SamplePages`, and
`ActivityCardSnapshotTests` render each card at an iPhone's width, light and dark, one at the largest accessibility size, and the batch
page's samples. Snapshots over 2,730 points are split.

## Risks / Trade-offs

- **[A list row that holds buttons]** A tap on an inner control can also fire the row's tap. → The controls are borderless buttons, which
  SwiftUI routes alone; the walk taps each one.
- **[Two fits for one route]** The native tile and the Compose preview fit with the same math in two places until the other branch lands.
  → A shared test sample drawn by both; the follow-up is in the roadmap.
- **[`Pair` in core-location]** `RouteShape` calls `TrackMath` and `RouteCurve`, whose `Pair` signatures the other branch may change. →
  Only `RouteShape`'s body changes at the merge; its own signature uses no `Pair`.
- **[Reaching the page on a device]** A batch page needs a batch import. → Two GPX files imported together, as the uploads walk did,
  Only me, deleted afterwards.
- **[Release export]** Every `:core-lexicon` type the views name must be named by an `-api` signature, not only by samples. →
  `ActivityCardContent` and `ActivityListEvent` name `ActivityView` and the moderation types; build Release before archiving.

## Migration Plan

None: no data or contract changes beyond added getters and an optional field with a default. Rollback is removing the registry entry.

## Open Questions

None blocking. The feed's own change decides how the host runs SubCircuits.
