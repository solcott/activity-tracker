# Proposal

## Why

OSM often maps a well-known trail as a chain of differently named sections, with no route relation over them. Corner Canyon's Rush descent in Draper, UT is one: `Rush Trail - Tom Sawyer Section`, `- Exit Stage Left Section` or `- Through the Trees Trail Section`, `Rush Trail`, `- Mystic Rhythms Section`, `- 2112 Section`, `- Dreamline Section`, `- Limelight Section`. The importer only joins ways that share a name exactly, so it offers each section but never the whole trail that riders know and race. Sections shorter than the group's minimum, like Mystic Rhythms, give no candidate at all.

## What Changes

- OSM ways named `<Parent>` or `<Parent> - <Section>` form a family per sport group. Where the family's ways meet end to end, they also join into **parent trails** named `<Parent>`, alongside the per-name trails, which are unchanged.
- A parent trail is one maximal path through the family's end-to-end joins, from a way nothing leads into to a way that leads nowhere further, in each direction its ways allow. It must cross at least two differently named ways, so it never just repeats a single-name trail.
- Where the family branches (Rush splits after Tom Sawyer and rejoins), each path is its own parent trail named `<Parent> via <section>` after the first section that tells it apart, or `<Parent> from <section>` after its first section when every section on it is on another path too. A family gives at most 4 parent paths; with more, only the longest is kept.
- A way that meets the family mid-way, rather than at an end, is not on any path. Rush's second `Limelight Section` way, a feeder from Coyote Hollow Foot Path, is an example.
- A parent trail gives its whole-trail candidate and, where another named trail or road meets it at a boundary between two of its sections, spans from its start to that junction and from the junction to its end. Locals rarely ride a sectioned trail end to end: Rush is ridden from Tom Sawyer to where Canyon Hollow Trail meets the top of Dreamline, so that span becomes "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail". A span of a single section is left out, since that section gives it. Other stretches are the sections' own candidates. A parent's whole candidate counts as a whole trail in dedup, ranked after a route relation, and loses to one of its sections' whole trails when the two duplicate each other.
- Ids, the 50 km cap, the group minimum, closures and every existing candidate stay as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/trail-candidates`: named ways join into parent trails as well as per-name trails, with naming for branches and a dedup rank for parent trails.

## Impact

- `server/segment-importer`: `TrailBuilder` (family grouping and path search), `CandidateCutter` (cutting parent trails into their whole candidate and junction spans, and naming spans), `CandidateDedup` (rank), the test fixture `trails.osm` with its PBF and expected output, and the README.
- Re-extracting a region adds parent candidates. Staging adds them to that region's candidates, and publishing makes them segments once ridden, with no AppView or lexicon change.
- Agency datasets are out of scope: their trails already join across features at shared ends.
