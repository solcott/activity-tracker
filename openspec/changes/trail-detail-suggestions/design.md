# Design

## Context

Two things already exist and this change builds on both:

- **Name proposals** (`segments/segment-names`). They already work the way this change needs:
  - a public `feed.segmentNameProposal` in the proposer's repo;
  - a private `feed.segmentNameVote` in their `interactions` space;
  - an hourly adoption pass, `SegmentNaming`, run by `SegmentMatcher`;
  - label handling (`unlabelledProposal()`);
  - `canProposeName` and `canVoteOnNames` on `segmentView`.
- **Trail details** (`segments/trail-attributes`):
  - The importer writes them as `trail` on its `feed.segment` and `feed.path` records.
  - `SegmentViews` serves them only for the importer's repo.
  - The apps show them in `TrailTexts` rows and the grouped cards.

`getPath` returns its segments as full `segmentView`s, so the client already has every segment's served `trail`. The visual design is the canvas at https://claude.ai/artifact/USYmaJAxKzGr4YPpvDJSzu.

## Goals / Non-Goals

**Goals:**
- Reuse the name-proposal model and its code paths wherever the rules match, so the two stay consistent.
- Keep each detail independent: one detail's suggestion, adoption and cooldown never touch another's.

**Non-Goals:**
- Operator pins of trail details. The admin site can come later, like `segment-pins`.
- Suggestions on a whole trail.
- Free-text notes, photos or comments with a suggestion.
- Sharing a suggestion to the feed, as names allow.
- Surface mixes: a rider suggests one surface for a segment, not percents.

## Decisions

### One record per detail, its value as a one-field `trailAttributes`

`feed.trailDetailSuggestion` holds `subject` (segment URI), `detail` (an open enum of the suggestable details) and `value`, a `feed.defs#trailAttributes` with exactly the field(s) that detail uses. Season, for example, uses `openFrom` and `openUntil`, or `seasons`, or `seasonal`.

This reuses the existing vocabulary, validation and texts. Two alternatives were rejected:
- One record holding many details: one bad value would block all the others, and votes would need per-field subjects.
- A string value per detail: it would duplicate the vocabulary.

Season is suggested as one season or as "seasonal". Open dates aren't offered in the picker, since they aren't a list to pick from; the record and the AppView accept them, so a later date picker needs no lexicon change.

Width is suggested as a kind and stored as a representative `widthCm`: singletrack 100, doubletrack 200, road width 400. These land in the ranges the Width row already maps back to words.

### Confirmations and votes share one private record

`feed.trailDetailVote` takes one of two shapes:
- **On a suggestion:** `subject` is a strongRef to the suggestion, as `segmentNameVote` does, with `direction` `up` or `down`.
- **On the value in effect:** `segment`, `detail` and `value` (a `trailAttributes` as above), with `direction` `still` or `changed`.

Naming the value makes a confirmation stop counting when the value changes, with no clean-up job. Both shapes live in the `interactions` space, so one new collection joins `AthleteSpaceDeclaration` and one `GrantFeature`. A separate confirmation record was rejected: it would add a second private collection for no gain.

### Storage and the adoption pass

- **Tables:**
  - `trail_detail_suggestions`: uri, cid, author, segment, detail, normalized value JSON, state, opened_at, closed_at, final totals, record_gone. An adopted suggestion keeps its row, as names do, so there is no separate adopted table.
  - `trail_detail_votes`: uri, voter, either the suggestion's uri and cid or the segment, detail and normalized value, direction, indexed_at.
- **The adoption pass:**
  - `TrailDetailAdoption` runs after the naming pass, from `SegmentMatcher` and `SegmentPasses` (so the admin site's one-segment passes can call it too).
  - It shares the rider count (riders in the last 365 days), the label filter's shape and the vote counting with `SegmentNaming`.
  - Its thresholds live in a `TrailDetailRules` (`TRAIL_DETAIL_*`), so they can differ from `SegmentNameRules`: 3 up votes, not 5, and a 30-day cooldown, not 90.
  - It groups by (segment, detail).
- **Effective values:**
  - `recomputeTrailCommunity` writes each detail's latest adopted value in effect, with the up votes that adopted it, into a `segments.trail_community` JSON column, after the pass, a label change and a handoff.
  - `SegmentViews` lays that over the importer's record `trail` per row (`effectiveTrail`), which is cheap, so a re-import needs no recompute: the community value simply wins.

### Sources

`trailAttributes.sources` is an object whose optional keys mirror the attribute names, each holding `osm`, `usfs`, `nps` or `blm`. The importer already weights by length when it combines values, so it also keeps, per attribute, the length of each source, and writes the longest.

The view's `trailSources` uses the same keys, plus `community` with a rider count. Older records have no sources, so the client shows "Imported".

### Freshness is coarse on purpose

The page shows when details were last confirmed, by month, and only once at least 2 riders have confirmed. A single confirmation dated to the day would reveal when one rider was last on the segment, which efforts served at Only me must never do. The may-have-changed flag needs 3 riders for the same reason.

### Quick check runs on the client

`getSegment` tells the viewer only `recentEffort` (an effort in the last 14 days). The card's "once per segment per season" memory, and "Not now", are a local preference, the same store the client uses for other dismissals. No server state is kept for a dismissal.

### Trail page roll-up on the client

`getPath`'s segment views already carry effective `trail` and `trailSources`. The Trail page compares each segment's community values with the path's own and builds the "Varies along the trail" rows in shared Kotlin (`PathTexts`), so Compose and SwiftUI draw the same rows. No AppView change to `getPath` is needed.

## Risks / Trade-offs

- **[Sockpuppets or a clique flip a detail]** → Suggesting needs efforts on 5 dates, and votes count only from riders with efforts. Adoption needs 7 days and a two-thirds majority. Labels undo a bad adoption, and the value falls back.
- **[Quiet segments never reach 3 votes]** → The suggestion still shows as pending ("Paved?") so riders see it, and it expires after 90 days. The threshold is configurable per deployment.
- **[Re-import overwrites a community fix]** → The adopted value stays in effect over later importer changes. Only a new suggestion replaces it.
- **[Source attribution is approximate]** → "Most of the line" is shown, not a mix, which is good enough for a caption. Records extracted before the bump show "Imported" until regions are re-extracted.
- **[Writes from non-riders]** → The AppView ignores them. The client hides the sheet from them, so they rarely try.

## Migration Plan

1. Ship the AppView first: the migration, indexer, pass and views. With no suggestions, effective details equal today's importer details.
2. Ship the lexicons and the clients.
3. Re-extract regions to fill `sources`. Until then the rows read "Imported".

To roll back, stop the pass and serve the record's `trail` alone. The records stay in repos and are ignored.

## Open Questions

- Whether "Riders report this may have changed" should also dim the value on the Trail page. This can follow real usage.
