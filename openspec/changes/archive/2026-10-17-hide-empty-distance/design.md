# Design

## Context

`ActivityCard`, `overviewStats` (activity page) and the import preview each build their stat list inline and always add `Stat(stat_distance, Format.distance(...))`, which prints a dash for null and "0.00 mi" for 0. `Format.rateStat` already drops the pace/speed stat for `RateKind.None` sports. Dives have their own stat lists and are unaffected.

## Goals / Non-Goals

**Goals:** one rule, shared by the three places, that drops a meaningless distance or elevation.

**Non-Goals:** changing what the importer stores (a 0 m session distance stays 0 m in the record); changing dive stats; hiding zero distance for sports with a rate (a 0 km run is odd data worth seeing).

## Decisions

- **Key on `RateKind.None`, not a new sport flag.** The catalog already documents it as "distance means little for this sport", and it stays in step with the pace/speed stat that `rateStat` already drops. Alternative: a `hasDistance` flag per sport, more to maintain for the same answer.
- **Fix it in display, not import.** Older records already hold 0 m; dropping zero at import would leave them wrong, and a display rule fixes old and new alike. Alternative: also null it at import, which a later change can do if the AppView ever filters or totals by distance.
- **A small helper beside `Format.rateStat`** (for example `Format.distanceStat(sport, meters)` returning `Stat?`, and the same for elevation), used with `listOfNotNull` the way `rateStat` is, so the three call sites stay one line each.

## Risks / Trade-offs

- [A sport marked `RateKind.None` that does sometimes cover ground with 0 m legitimately] → only zero and absent are hidden; any positive distance shows.
