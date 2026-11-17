# Design

## Context

See proposal.md for why. Today the segment importer works like this:

- **`TrailBuilder`** builds trails for each sport group:
  - named trails: ways grouped by their exact raw name;
  - relation trails: members joined in order, with excluded members, roads among them, removed;
  - parent trails.
- **`CandidateCutter.cut`** cuts each trail. It uses the trail group's own minimum length and takes each piece in every direction that all of the piece's edges allow, so a relation mixing one-way and two-way ways gives a whole-trail candidate in only one direction.
- **`PathOverlap.pathsUnder`** decides path membership, with one rule for every segment. Both the AppView (`PathIndex`) and `publish-paths` use it.

The cases below come from `build/segment-import/us-utah`. Way and relation numbers are OSM ids.

| Case | OSM | Effect today |
|---|---|---|
| Canyon Hollow along a service road | way 988920117, 103 m | trail breaks |
| Canyon Hollow name variant | ways 977484259/61/62, `Canyon Hollow`, 126 m | trail breaks, no candidate |
| Clarks relation onto Ann's | relation 1792170 includes way 394110688 | 2287 m "Clarks", Ann's way inside the Clarks path |
| BST relation over Clarks | relation 1792238 includes the Clarks ways | 5045 m whole candidate is one-way; its 2022 m span names the Clarks group |

## Goals / Non-Goals

**Goals:**

- Fix these cases with general rules.
- Keep candidate ids stable wherever a trail's ways don't change.
- Need no lexicon or database change.

**Non-Goals:**

- Re-cutting or deleting segments already published. The importer never edits or deletes them (`segment-import`).
- Changing parent-trail cutting. Family paths already follow only the directions their ways allow.
- Agency trails. They always go both ways and never cross OSM ways.

## Decisions

### 1. Name variants join by the name without its generic words

`namedTrails` groups ways by `words.shorten(name)` rather than the raw name. The trail's `name` is the raw name of its longest way, so paths and families keep the full name, such as "Canyon Hollow Trail".

- *Alternative:* fuzzy matching. Rejected: it would join unrelated trails.

### 2. Shared tread is bridged on the way itself, from dangling chain ends only

After chaining, a pass over the chain ends looks for a way W through end node `n` where:

- W is not one of the chain's ways;
- no way of the trail's own name continues at `n` (the end is dangling);
- W is open to the group: not `access=no|private`, and not `bicycle=no` for ride or `foot=no` for run and hike.

For each such W, the pass walks along W from `n` in both directions, up to 200 m. If it reaches a dangling end `m` of another chain with the same key, the two chains merge through the partial way `n..m`.

To allow this, `Placed` gains an optional node sub-range. The edges along W carry W's id, so W shows up in the candidate's `osmWays` and in its id, and W never counts as a junction for the trail. The ways crossing W at `n` and `m` still mark junctions.

Relations use a simpler rule: an excluded member is kept when it is a road (not closed), at most 200 m long, and joins the members on both sides of it.

- *Alternative:* allow a gap of up to 200 m between chain ends whatever lies between them. Rejected: the line would cut across terrain the trail doesn't follow.

### 3. Trimming a relation's ends needs an own-name anchor

A relation is trimmed only when at least one of its ways carries the relation's own name or family. Otherwise a relation made entirely of differently named ways would trim itself away. Trimming happens after joining, on each stretch, and removes whole member ways from each end.

### 4. Cut where the direction changes, then at junctions

`cut` splits a NAMED or RELATION trail into maximal runs of edges with equal `(forward, backward)`.

- **One run:** behaviour is unchanged.
- **More than one run:** each run gives a piece with `whole = false` and a part number, plus its stretches at junctions. Names follow the stretch rule.

Because a run has `whole = false`, the existing dedup order (relation, then whole) already keeps the whole "Clarks" relation candidate over BST's Clarks run.

### 5. One stretch minimum for every group on a trail

The cutter computes the minimum used for joining stretches as the maximum `minMeters` over the groups that every one of the trail's own (non-bridged) ways qualifies for (`rules.wayGroups`), including the trail's own group. The final drop of short candidates still uses each candidate's own group minimum.

Run and hike trails over ride-capable ways then merge stretches at 500 m, exactly as ride does, and their lines line up into one segment group.

### 6. A covering relation names the named-way trail

After building, a named trail takes a relation trail's label when the set of its ways equals the relation trail's set of ways. The relation trail may be of any group, and the comparison uses the relation after trimming.

The trail's `name` and `family`, which paths use, stay the same. Only `label`, which candidates use, changes.

### 7. The importer's stricter test lives in `PathOverlap`

`pathsUnder` gains a `share` parameter:

- `PathOverlap.SHARE` with `MIN_METERS` for athletes, as today;
- `IMPORTED_SHARE = 0.9` with no metre cap for the importer's segments.

The two callers pick the rule as follows:

- **`PathIndex`** picks it by comparing the segment row's author with the importer DID it already holds.
- **`publish-paths`** picks it by comparing the segment URI's repo with its own session DID.

That keeps the two in agreement, as the spec requires.

- *Alternative:* store each imported segment's trail on its record. Rejected: it needs a lexicon change and wouldn't cover segments already published.

## Risks / Trade-offs

- **[Ids change for trails that now join or are trimmed]**
  - The new candidates have new ids and get published beside the old segments, which stay. On a fresh stack this is invisible.
  - On a populated AppView, the duplicate check keeps new candidates that duplicate live segments unpublished.
  - Mitigation: accepted. Re-cutting live segments is a separate change.
- **[Bridging joins two different trails that share a name]**
  - The 200 m limit, the dangling-end requirement and the shared way keep this rare.
- **[90% hides an imported segment from a trail it mostly but not wholly follows]**
  - This mainly affects segments that run onto a road at their ends.
  - Bridging and trimming make imported lines follow their trail closely, and the 30 m corridor absorbs small offsets.
- **[A larger stretch minimum gives run and hike fewer, longer stretches]**
  - The user chose this, so that all groups share rows.
