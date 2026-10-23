# Design

## Context

See proposal.md for why, and the spec for behavior. After `activity-gear`:

- `activity_gear(activity_uri, origin, gear_uri, position, visibility)` holds every reference, one row per origin, with the visibility of the record or details row that holds it.
- `gear_items` holds each item with its visibility and origin.
- `activities` has `started_at`, sport and sub-sport as columns. Distance, moving and elapsed time, elevation gain and loss, `utcOffset` and the dive's bottom time are core fields (so they sit at the activity's tier), but only inside the `record` JSON. When Start time is hidden, `started_at` is the local midnight, which still gives the right local date. A combined activity's members are found through `combinedIntoOf`.
- `visibleTo(viewer, visibility, author, record, cid)` works on any table's columns.

## Goals / Non-Goals

**Goals:**
- One predicate defines "an activity counted for this viewer and item", shared by the totals, the breakdowns and the activity list, so the numbers always match the list.
- Totals are read live. No stored aggregates go stale after an edit or a tier move.

**Non-Goals:**
- No rolling windows ("last 30 days") or charts beyond the two breakdowns.
- No caching layer. Revisit only if profiling shows the need.

## Decisions

### The counted-activities predicate
`countedFor(viewer, gear)`, an `exists` over `activity_gear` for the item where:
- `visibleTo(viewer, activity_gear.visibility, …)` admits the reference's tier (for the athlete this is always true),
- `visibleTo(viewer)` admits the activity row,
- the activity isn't a valid member of a combined activity (`not(combinedIntoOf)`),
- and, for the item itself, `visibleTo(viewer, gear_items.visibility, …)` and `served` admit it, or the query answers `NotFound`.

The same reference can exist at two origins mid-move. The `exists` counts each activity once.

### Aggregation
- Summary columns: V36 adds `elapsed_time`, `moving_time`, `distance`, `elevation_gain`, `elevation_loss`, `dive_bottom_time`, `started_on` (the epoch day of `started_at` shifted by `utcOffset`, UTC without one) and `started_year` to `activities`, all computed in Kotlin by the indexer (`ActivitySummary`). SQL can't read the record JSON portably across H2 and Postgres, so V37 is a Kotlin Flyway migration (`ActivitySummaryBackfill`) that fills them for rows indexed before V36.
- Totals: one `SELECT` over `activities` filtered by `countedFor`: `COUNT`, `SUM(COALESCE(moving_time, elapsed_time))`, `SUM(distance)`, `SUM(elevation_gain)`, `SUM(elevation_loss)`, `COUNT(DISTINCT started_on)`.
- Dives: the same query restricted to sport `dive`, summing `dive_bottom_time`.
- Breakdowns: the same aggregate grouped by `(sport, sub_sport)` and by `started_year`.
- Starting usage is added in Kotlin after the query, and only to the totals.

### Queries
- `gear.getGear(uri)` returns `gearView` plus `usage` (`totals`, `starting`, `bySport[]`, `byYear[]`).
- `gear.getActorGear(actor, cursor)` returns the items the viewer may see, each with `totals`, active first, then by name. Totals are computed with one grouped query by `gear_uri`, not one query per item.
- `gear.getGearActivities(uri, cursor)` returns `activityView`s newest first, keyset `(started_at, uri)` like `authorActivitiesByStart`, through the normal `hydrate`.

### Main total per type
`GearType.mainTotal(type)` in `core/lexicon` maps each type to distance, days plus vertical, dives, or time. Unknown types use time. The client formats the total with the athlete's units (`data/units`).

### Screens
- `GearScreen(uri)`: header (photo, name, kind, brand and model, visibility for the owner), a totals card with starting usage shown as "includes 2,000 km before tracking", the two breakdowns, and the activity list through the existing feed item UI with `Outcome<Page>` paging.
- `GearListScreen` reads `getActorGear` for its own DID to get totals, and the items themselves from `GearRepository.items()` as before, so a new item shows at once with "No activities yet".
- `EditGearScreen` gains the starting distance (in the athlete's units) and starting time fields.

## Risks / Trade-offs

- [Aggregating on every read gets slower as an athlete's history grows] → Each query is bounded by one item's references, which `activity_gear(gear_uri)` indexes. Thousands of activities per item is still a small aggregate.
- [The AppView lags behind a fresh edit, so totals lag too] → The gear page refreshes on open. The lag is the same Jetstream and space-sync lag every view already has.
- [Viewers can compare their totals with the athlete's to infer hidden activity] → They never see the athlete's numbers, only their own, so there's nothing to compare against.

## Migration Plan

Deploy the AppView migrations (V36's starting usage and summary columns, V37's backfill of them from existing rows), then ship clients.
