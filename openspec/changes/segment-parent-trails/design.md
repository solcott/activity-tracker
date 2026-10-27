# Design

## Context

`TrailBuilder.namedTrails` groups OSM ways by (sport group, exact name), chains them end to end, and stops a chain where more than two same-named ways meet. `relationTrails` joins a route relation's members in order. `CandidateCutter.cut` gives each `Trail` a whole candidate plus stretches between junctions, in each direction all of its edges allow, and names a two-way whole trail with its heading. `CandidateDedup` ranks by agency, then relation, then whole, then id. Candidate ids hash the group, the ways in travel order, the direction relative to the first way, the end nodes and the end-visit count (`CandidateCutter.idOf`).

Draper's extract shows both shapes the new rule must handle (see proposal.md - Why). Rush's ways form a one-way diamond: Tom Sawyer leads into both Exit Stage Left and Through the Trees, which both lead into the bare `Rush Trail` way. Limelight has a feeder (way 1140172760) and a two-node stub (way 1420268007) that end at nodes in the middle of the main Limelight way (696249490).

## Goals / Non-Goals

**Goals:**
- Parent trails come from the same OSM data as today, are as stable as other candidates across re-extracts, and leave every existing candidate's id and name unchanged.
- Bounded work per family, so one heavily sectioned network can't blow up extraction.

**Non-Goals:**
- Joining ways whose names share no `<Parent> - ` prefix, such as "Upper Ghost" or other local names. That calls for curated input, a separate change.
- Agency datasets, whose features already join at shared ends whatever their names.
- Splitting ways at mid-way nodes to join feeders.

## Decisions

**Family key: the name up to the first `" - "`.** A way named `P - S` belongs to family `P`, and a way named exactly `P` belongs to it too. A family exists only when at least one of its ways has a section suffix, so plain trails never get a parent pass. Alternatives considered: matching on a trailing `Section` word was too narrow (`Rush Trail - Limelight` without it would be missed), and longest-common-prefix grouping joined unrelated trails that only share words like "Bonneville".

**Directed path search over way ends.** For each family and group, every way gives a directed edge from its start node to its end node for each direction `rules.directions(tags, group)` allows. A path starts at an edge whose start node no other way's edge leads into, follows outgoing edges of ways not yet used, and ends where none continue. Joining only at way ends is what keeps the Limelight feeder off every path, since it ends mid-way and never shares an end node with the main way. Alternative considered: undirected search between degree-1 nodes, then filtering by direction. It produced paths no one can ride on one-way families, and those paths counted against the cap.

**Pairs, cap and a search budget.** A path and the path along the same ways reversed are one parent with two directions. They are built as one `Trail` with `forward`/`backward` set from the ways' rules, so the cutter emits both directions and names a two-way parent with its heading as today. Distinct way-sets are kept up to 4, and with more, only the longest. The depth-first search stops after 1,000 extended steps per family and group. Past that, it keeps the longest paths found so far and logs the family, so a pathological network degrades to fewer parents instead of hanging.

**Naming.** One path: `<Parent>`. Several: `<Parent> via <S>`, where `S` is the full section text after `" - "` (for example "Exit Stage Left Section") of the first way on the path that no other kept path in the family uses. A bare `<Parent>` way can't be the distinguishing section, since it names no section. When every section on a path is on another kept path too, as for an entry that joins partway down through a bare `<Parent>` way, it's named `<Parent> from <S>` after its first section, so it doesn't read as the whole trail. Every parent path crosses a section, since it crosses at least two names. Names go through `truncate` like any other.

**Trail kind instead of a flag pair.** `Trail.fromRelation` becomes `kind: NAMED | RELATION | PARENT`, and `Candidate` carries the kind. The cutter gives a `PARENT` trail its whole piece and its junction spans (below), never its other stretches. Dedup sorts by agency, relation, named whole, parent, stretch, then id, which puts a single-name whole trail ahead of a parent when the two duplicate (one long section plus a 30 m stub). Alternative considered: a second boolean on each. Three states fit an enum better, and every `fromRelation` reader is local to the importer.

**Ids unchanged in derivation.** A parent candidate's id uses `idOf` over its ways, like any OSM candidate. It can't collide with a section's, because it follows at least two differently named ways. It equals a relation candidate's id only when the relation follows the same ways between the same ends. Those two lines then duplicate each other and dedup keeps the relation, so ids stay unique per file. A test covers that case.

**Junction spans: from the ends to each junction, not between junctions.** Riders treat a sectioned trail's named crossings as where a run starts or stops, so a parent trail is cut where a named way outside its family meets it at a section boundary: the junctions the cutter already finds (`others`), restricted to nodes between differently named sections and to ways whose family isn't the trail's. It gives a span from its start to each junction and from each junction to its end, which is linear in junctions. Spans between two junctions are left out: they're quadratic, and in Draper the only one (Canyon Hollow to the BST) is Dreamline alone. Spans are `whole = false` with kind `PARENT`, so dedup ranks them with stretches, and a span that only follows one name is dropped since the section gives it. Alternative considered: cutting at every named crossing like a single-name trail. That repeats the sections' own stretches, and a road crossing mid-section isn't where a run ends.

**Naming spans in the cutter, with what the builder knows.** A `PARENT` trail carries its family name and the ways on no other kept path of its family (`ownWays`), set by `TrailBuilder`. A span is named in its direction of travel: `<trail> to <J>` when it ends at the junction and `<trail> from <J>` when it starts there. `<trail>` is the trail's own name (`Rush Trail via Exit Stage Left Section`) when the span follows one of its `ownWays`, else the family name, so the suffix that every branch shares reads `Rush Trail from Canyon Hollow Trail` and dedups to one id. `<J>` is the family name of the first other named way at the node, by name, so `Bonneville Shoreline Trail - Draper Section` reads `Bonneville Shoreline Trail`.

## Risks / Trade-offs

- [Unrelated trails sharing a prefix join, such as two "Main Street - …" paths] → Joins happen only at shared way ends, families are per sport group, and a path must cross two differently named ways. A wrong parent becomes a segment only once riders ride it end to end, since publishing waits for riders.
- [Long agency-style families such as "Bonneville Shoreline Trail - Draper Section" yield very long parents] → The 50 km cap and group bounds drop them, and a shorter family path is still a fair segment.
- [More candidates per region raise staging volume] → One whole candidate per kept path, at most 4 per family, plus two spans per junction at a section boundary. Draper's Rush has two such junctions.
- [A span ends a few metres past where riders stop, so a ride that turns off at the junction just misses the 25 m end radius] → Spans end at the junction node, which riders pass through to turn off. Both Draper rides pass Tom Sawyer to Canyon Hollow with at most 25 m of drift.
- [Branch names read oddly when sections are named generically ("Upper Section")] → Acceptable: they're still the map's names, and the via-suffix is stable across re-extracts as long as the ways are.

## Migration Plan

No data migration. After release, re-extract and re-stage each region. Parent candidates stage as new ids with no riders, and riders are counted against existing usable tracks when staged (segments/segment-candidates), so ridden parents publish on the next `publish` run. To roll back, re-extract with the previous importer: parents leave staging with that run, but segments already published from them stay.
