# Design

## Context

See proposal.md for why, and the spec for behavior. After `gear-usage`:

- `GearViews.countedFor(viewer, gear)` is the one predicate behind totals, breakdowns and the gear activity list. It checks the reference's tier, the activity, the item, and the combined-member rule.
- `activity_gear` holds what each activity lists directly.
- Items are records in the repo or a tier space, written whole by `GearRepository.save`.

## Goals / Non-Goals

**Goals:**
- Swapping parts changes only the component's own record.
- Mounted counting reuses `countedFor`, so viewer rules, combined activities and the activity list stay consistent.

**Non-Goals:**
- No inferring mounts from sensors (`gear-sensors` reads mounts, it doesn't write them).
- No mounts across athletes, such as a shared tandem.

## Decisions

### Mounts live on the component's record
`gear.item.mounts: [{ parent, from, to? }]`, sorted by `from`. Mounting, unmounting and replacing all rewrite the component. Replacing also writes the new component.
- *Alternative:* a separate `gear.mount` record per mount. Rejected. Mounts belong with the component's visibility, and they are few per item. One record keeps the history and its validation in one place.
- *Alternative:* a list of components on the parent. Rejected. A component then couldn't keep its history after moving to another bike, and a parent's record would need rewriting on every swap.

### Validation in one place
`GearMounts` in `core/lexicon` checks that windows don't overlap, that following parents never comes back to the item, and that a chain is at most three deep below a top-level item. The client checks against its full item list before saving. The AppView indexer runs the same check against indexed rows and drops each invalid mount it finds (not the whole item). It processes items in any order: a mount whose parent isn't indexed yet is kept, and counts once the parent arrives.

### Counting through mounts
- `gear_mounts(component_uri, parent_uri, mounted_from, mounted_to)` is filled from each item's `mounts`.
- `countedFor(viewer, gear)` becomes: the activity lists `gear` directly (as before), **or** it lists an ancestor `A` reached through mounts, with the activity's `started_at` within each mount's window along the path, and for a viewer other than the athlete, every item on the path visible to them (`visibleTo` on each `gear_items` row).
- Depth is three at most, so the ancestor paths unroll as three self-joins of `gear_mounts` OR-ed together. This stays H2-portable, with no recursive CTE.
- `exists` keeps each activity counted once.

### Screens
- `GearScreen` Components section: current mounts with main totals, Previous with dates. Mount opens a picker of the athlete's items that aren't already mounted elsewhere now, plus "New item". Unmount asks for an end date, defaulting to today. Replace is unmount today plus mount from today, saved in that order.
- `EditGearScreen` shows "Mounted on Tarmac since 1 Mar 2026" read-only, with a link to the parent.

## Risks / Trade-offs

- [Mount windows compare instants, and a mount made "today" may miss a ride earlier that day] → Mounts start, and unmounts end, at the start of the local day (`to` is exclusive), so a part swapped on a date counts that whole day's rides for the new part, and "unmounted on 1 June, mounted elsewhere from 1 June" meet without overlapping. An athlete who swaps parts mid-day can edit the times.
- [Three self-joins per counted query] → They're bounded by one item's ancestors, and `gear_mounts(component_uri)` is indexed.
- [A Public component's record shows its parents' URIs and mount dates, whatever their visibility] → Accepted: the URI reveals only an rkey, and the AppView serves mounts and components only for items the viewer may see. An athlete who minds can make the component narrower.
- [Mounts referencing items the viewer can't see reveal nothing] → The path check runs per viewer, and the components list filters by item visibility.

## Migration Plan

Deploy the AppView migration (an empty `gear_mounts`, filled as item records are re-indexed or next written), then ship clients. Items without `mounts` are unaffected.
