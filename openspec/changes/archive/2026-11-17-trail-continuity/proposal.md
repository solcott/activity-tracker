# Proposal

## Why

Trail pages in Corner Canyon (us-utah) show where the importer's trail model and real trails part ways.

- **Canyon Hollow** has two gaps in its line. One is a 103 m stretch where the trail runs along a service road. The other is three short ways tagged `Canyon Hollow` instead of `Canyon Hollow Trail`. Its ride stretches and its run and hike stretches are also cut at different places, so each stretch shows up as two rows.
- **Clarks**:
  - its row is named after the Bonneville Shoreline route that shares its tread;
  - its ride segment runs 267 m onto Ann's Trail, because OSM's "Clarks Trail" route relation includes that way;
  - its page lists Ann's stretches and a 3.13 mi Bonneville Shoreline segment;
  - that BST segment is marked uphill-only from end to end, although only its Clarks part is one-way.

## What Changes

- **Name variants join.** Ways whose names match once generic words are dropped (`Canyon Hollow`, `Canyon Hollow Trail`) join into one trail.
- **Short shared tread joins.** A trail continues across up to 200 m of a road or another named way where it shares that tread. A route relation does the same across a short road member.
- **Relation ends are trimmed.** A route relation's end ways that belong to another named trail are dropped, as long as a way of the relation's own name remains.
- **Trails are cut where their one-way status changes.** A trail that mixes one-way and two-way edges gives no whole-trail candidate, only one per uniform run.
- **Sport groups share stretch ends.** Every sport group of a trail cuts stretches at the same points, using the largest minimum length among the groups its ways are open to.
- **A covering relation names the trail.** A named-way trail whose ways are exactly a route relation's trail takes that relation's name.
- **Imported segments stay on their own trail.** The segment importer's segments lie on a path only when at least 90% of their length follows it. Athletes' segments keep the current rule (1 km, or a quarter of their length).

Segments the importer has already published don't change. It never edits or deletes them. The new rules only affect new extracts and how the AppView works out path membership.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/trail-candidates`: how named ways and relations join into trails (name variants, shared tread, trimmed ends), cutting at one-way changes and at shared stretch ends, and naming by a covering relation.
- `segments/segment-paths`: the stricter membership rule for the importer's segments.
- `segments/path-page`: the Clarks scenario reads a single "Clarks" row.

## Impact

- `server/segment-importer`: `TrailBuilder`, `CandidateCutter`, `Extract` and `PublishPaths`; the README.
- `core/location`: `PathOverlap`.
- `server/appview`: `index/PathIndex.kt`, which re-derives path membership. No migration is needed, because membership is recomputed when segments or paths are indexed.
- `app/fixtures`: `PathSamples.kt`.
- No lexicon change.
