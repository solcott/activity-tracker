# Design

## Context

Extraction runs `TrailBuilder` → `CandidateCutter` → `CandidateDedup` over one network of OSM and agency ways (`candidatesOf` in `Extract.kt`). `AgencyReader` keeps only features that qualify as trails: a key, a line, a name and at least one group. A Forest Service wilderness feature with hikers managed and bikes restricted becomes a hike-only way, and the restriction is lost. A feature closed to every group, or one without a name, is dropped entirely. OSM and agency ways share no nodes, so nothing in the network relates an OSM way to the agency line it runs along. Only dedup compares their geometry, whole line against whole line, per group.

In the Vermont sample (`segment-agency-import`'s task 1.1), every Forest Service `BICYCLE_RESTRICTED` value was a full-year `01/01-12/31`, on features with no bicycle season managed or accepted. BLM's allowed-mode codes and access restrictions are described in `segment-blm-import`'s design.

## Goals / Non-Goals

**Goals:**
- OSM candidates that would invite a sport where an agency says it is closed are left out, using only datasets the operator already gives `extract`.
- The rule is geometric and local: only the candidates that actually follow a closed line are affected.

**Non-Goals:**
- Park Service closures. Its `TRLUSE` is free text and "Unknown" on a third of features, so a missing use is not a closure. Its decommissioned or temporarily closed status is left for later.
- Seasonal closures. A use restricted part of the year stays open, as qualifying ignores seasons.
- Retiring segments already published from OSM candidates. The importer never deletes a segment.
- Overriding a mis-coded agency feature, such as BLM's motorcycle-only Slickrock Trail. See Risks.

## Decisions

**Each dataset's rules say what a feature is closed to.** `AgencyRules` gains `closedTo(properties): Set<String>` beside `groupsOf`:

- **USFS:** a group closes when its `_RESTRICTED` field is `01/01-12/31` and none of its `_MANAGED`, `_ACCPT` or `_ACCPT_DISC` fields holds a season. The groups follow the same field-to-group table as `groupsOf`.
- **BLM:** for a known allowed-mode code that isn't a snow code, every one of `ride`, `run` and `hike` that the code's row leaves out closes. Snow codes describe winter travel and close nothing. Restricted access (`All`, `Admin Only`, `Authorized/Permitted User Only`) closes all five groups. `UNK`, empty and unknown codes close nothing: the planned-mode fallback can open a trail but never close one.
- **NPS:** returns nothing.

*Alternative:* treating any group not in `groupsOf` as closed. Rejected: absence is not closure in any of the three datasets (Park Service "Unknown", BLM `UNK`, Forest Service fields left blank).

**The reader keeps every keyed line with its open and closed groups.** Beside the ways it builds, `AgencyReader` returns `AgencyLine(open: Set<String>, closed: Set<String>, points)` for each keyed LineString or part. These lines are raw and unsnapped, with or without a name, and the reader keeps only lines that close something or are open to a group. Names don't matter for closures: an unnamed restricted feature still closes the ground it covers.

**A closure filter runs between cutting and dedup.** `ClosureFilter(lines).keep(candidate)` checks each OSM candidate (`osmWays` not empty) of group G:

- It samples the candidate's line every 5 m.
- A sample counts as closed when it lies within 15 m of a segment of a line closed to G and not within 15 m of a line open to G.
- The candidate is dropped when its closed samples add up to at least 100 m.

Lines are indexed by `SegmentCells` cell (0.01°) with a 15 m margin, so a candidate is compared only with lines in its own cells. Distances use the same local flat-earth projection as `CandidateCutter`'s tests. The filter runs before dedup, so the report counts the drops, and an OSM candidate that dedup would have dropped for an agency one is counted too.

*Alternatives:*
- Dedup's whole-line duplicate test. Rejected: a closed stretch is usually part of a longer OSM trail, so the lines are not duplicates.
- Cutting OSM trails at closure boundaries. Rejected: new cut points would change OSM candidate ids for trails that have nothing to do with closures.

**15 m and 100 m.** OSM and agency digitising of one trail typically differ by a few metres, and well under the matcher's 30 m corridor. 15 m catches the same trail without catching a separate trail at 30–40 m. 100 m ignores crossings: a perpendicular crossing shares about 30 m within the corridor, and a short shared approach to a junction adds little. Task 1.1 measures the offsets on the Vermont data before these numbers are coded. If they fail, the spec and this decision change first.

**The run reports drops per group.** `extract` prints, after its candidate counts, `Left out N <group> candidates along agency closures` for each group with drops.

## Risks / Trade-offs

- **[A mis-coded agency feature drops OSM candidates on a legal, popular trail]** The literal BLM reading closes Slickrock Trail (coded motorcycle-only) to `ride`, so OSM's Slickrock ride candidates would be left out. → The report shows the counts for review. A later change can add an operator override list if this proves common.
- **[The closure covers only the datasets the operator passes]** A region extracted without its Forest Service data gets no closures. → The README says to pass every agency dataset covering the region.
- **[A trail that leaves and rejoins a closed line]** Its closed samples add up across separate overlaps, so several short overlaps can reach 100 m together. → Accepted: each overlap is still riding on closed ground.
- **[Cost]** Sampling every candidate every 5 m against nearby segments is O(length × nearby segments). → Lines are indexed by cell, and the network of a state fits in memory today.

## Migration Plan

Re-extract each region with its agency datasets and stage it. Candidates the filter drops leave staging when the run finishes, as any candidate missing from a run does. Published segments stay.
