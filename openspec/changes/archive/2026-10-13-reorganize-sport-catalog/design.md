# Design

## Context

`SportCatalog` (`core/data`) is the single source for the picker groups. Each `SportInfo` carries one `SportCategory`, and `categories` lists every sport of a category followed by all its sub-sports, plus a hand-kept `shortcuts` map that copies types into a second category. The record picker (`ActivityTypePicker`) and the feed-filter picker both render through `sportTypeList` (`composeApp/.../ui/SportTypeList.kt`). That function already treats a sub-sport whose generic sport isn't listed in the same category as a top-level row, and nests the rest under a collapsible sport.

The feed-filter selection model (`TypeSelection.kt`) and the AppView both read a bare sport as "every sub-sport of it". The model knows a sport's sub-sports through `SportCatalog.subSports`, regardless of category. `SportCategory` is never stored. Its labels are looked up as `sport_category_<name>`.

## Goals / Non-Goals

**Goals:**
- One place, the catalog, decides where each type is listed. The UIs derive nesting from the lists.
- The tests enforce "each type is listed once" with no exceptions.

**Non-Goals:**
- Any lexicon token, importer mapping or AppView change. The provider-coverage tests must pass with their maps untouched.
- A "generic form only" type filter. Selecting a category selects its sports whole (see the feed-filter delta).
- Rate fixes (rowing pace per 500 m) and renaming `indoor` to "no GPS".

## Decisions

**A sub-sport can be filed under its own category.** `SubSportInfo` gets an optional `category: SportCategory? = null`, and a null category means the sport's. `categories` is built per type:
- the sport's generic type goes in the sport's category;
- each sub-sport goes in its own category, or its sport's when it has none;
- within a category, types keep catalog order, and each sub-sport follows its sport when both are listed there.

The `shortcuts` map is deleted.
- *Alternative: keep shortcuts but stop listing the originals.* That needs the same per-type placement plus an exclusion list, so it's two mechanisms for one idea.
- *Alternative: a separate placement table keyed by `ActivityType`.* That splits a sport's facts across two places. Putting the category on `SubSportInfo` keeps each sport declared in one spot.

**Filing in this change.** Indoor Row, Virtual Row and Indoor XC Ski go to Gym & Fitness. Climb keeps bouldering and indoor under Hiking & Climbing. Run and Ride keep all of theirs. Every other sub-sport stays with its sport.

**Categories.** The `SportCategory` entries become, in this order: `Running, Cycling, Gym, MindBody, Combat, Swimming, Multisport, Walking, HikingClimbing, WinterSports, Parasports, WaterSports, Diving, TeamSports, RacketSports, Target, Skating, Adventure, Motorsports, Other`. Enum names stay short. The display text ("Gym & Fitness", "Hiking & Climbing", "Mind & Body") lives in `strings.xml`.

Sports move as follows:

| Sport(s) | From | To |
|---|---|---|
| Walk | Outdoor | Walking |
| Wheelchair | Outdoor | Parasports |
| Hike, Rucking, Mountaineering, Climb | Outdoor | Hiking & Climbing |
| Fish | Outdoor | Water Sports |
| Golf, Disc Golf, Archery | Outdoor | Target |
| Inline Skate, Skateboard, Kick Scooter | Outdoor | Skating |
| Adventure Race, Orienteering, Expedition, Horseback, Hunt | Outdoor | Adventure |
| Parkour | Outdoor | Gym & Fitness |
| Yoga, Pilates, Mobility, Recovery | Gym | Mind & Body |
| Tai Chi | Gym | Mind & Body |
| Boxing, MMA, Wrestling, Fencing | Gym | Combat |
| Meditation, Breathwork | Other | Mind & Body |
| Flying, Skydive | Other | Adventure |
| Bowling | Other | Target |
| Snowmobile | Motorsports | Winter Sports |

Water Fitness stays in Swimming as a sport. Floor Climb and Gymnastics stay in Gym & Fitness.

**No flattening.** Every sport with sub-sports listed under it collapses, in every category, so a row behaves the same wherever it appears. Flattening categories that hold a single sport (Running, Walking) was tried and dropped: it made Run look like a parent missing its chevron next to a collapsible Ride. `sportTypeList` needs no layout change, because it already shows a sub-sport whose generic sport isn't listed in the same category as a top-level row.

**The selection logic is unchanged.** A category toggle already folds `select` over the category's listed types, and a listed bare sport covers its sub-sports filed elsewhere, as the feed-filter delta specifies. `normalized()` already collapses using every sub-sport from `SportCatalog.subSports`, wherever it's filed. A category's mixed state still counts its listed types through `covers`. The only presenter-side work is updating tests that name moved types or shortcuts.

**Labels.** `sub_sport_wheelchair_walkPace` and `sub_sport_wheelchair_runPace` become "Push - Walk Speed" and "Push - Run Speed". `sub_sport_run_indoorTrack` becomes "Indoor Track Run". New `sport_category_*` strings are added for the new entries, and `sport_category_Outdoor` is removed.

## Risks / Trade-offs

- [Selecting Water Sports also covers Indoor Row under Gym & Fitness, which turns Gym mixed] → Accepted, and specified in the feed-filter delta. A generic-only filter value can come later if it matters.
- [20 sticky headers make the unfiltered list longer] → Sports start collapsed, and search plus "Recent and common" remain the fast path.
- [Recent types are unaffected, but a feed filter held in session state could name a category that no longer exists] → It can't: `SportCategory` is never persisted, and the filter stores `ActivityType`s only.
- [Category icons or tests keyed by `SportCategory` names break at compile time] → The enum change makes the compiler find every use. `SportIconsTest` and `SportLabelsTest` cover labels and icons for every entry.

## Migration Plan

None. No stored data or wire format changes. A rollback is a revert.
