# Proposal

## Why

Trail details come only from public data (OSM tags and agency fields), which is often missing, wrong or stale: a trail goes no-dogs, a gravel section is paved, a seasonal closure changes. Athletes' own segments have no details at all. The riders who use a segment every week know better, and there's no way to say so. The app already lets regulars rename segments by vote (`segments/segment-names`). The same model can keep trail details true. Design: https://claude.ai/artifact/USYmaJAxKzGr4YPpvDJSzu

## What Changes

- **Suggestions.** A regular (efforts on a segment on at least 5 distinct dates) may suggest a value for one trail detail of an `active` segment. The suggestion is a new public `feed.trailDetailSuggestion` record in their repo, naming the segment, the detail and the value. One open suggestion per athlete per segment per detail.
- **Votes and confirmations.** Any rider (an effort on the segment) may:
  - agree or disagree with a suggestion;
  - say the value now in effect is "Still right" or "It's changed".

  Both are a new private `feed.trailDetailVote` record in the voter's `interactions` space. Only totals are served, never who voted.
- **Adoption.** A suggestion is adopted as with names: open at least 7 days, up votes reaching the larger of 3 and 10% of the segment's riders in the last year, and at least two-thirds of the votes up. It is rejected at a score of −5 and expires after 90 days. Adopting one closes the detail's other suggestions, and new suggestions on that detail pause for 30 days. The thresholds are configurable.
- **Effective details.**
  - A segment's served `trail` becomes its effective details: each adopted community value over the importer's value for that detail.
  - Community values are served on every active segment, athletes' segments included. An athlete's own `trail` on their record stays ignored.
  - A new `trailSources` on the segment view says where each served detail came from: the importer's source (OpenStreetMap, Forest Service, Park Service, BLM, or just "imported" when unknown) or the community, with its rider count.
- **Import provenance.** The importer records, for each attribute it writes, the source covering the most of the line. This is a new optional `sources` in `feed.defs#trailAttributes`. It is filled when regions are re-extracted, and older records show "Imported".
- **Freshness.**
  - `getSegment` serves, per detail, how many distinct riders confirmed the value in effect in the last 365 days.
  - It serves the month of the latest confirmation, only once at least 2 riders have confirmed.
  - It serves a "may have changed" flag once, in the last 90 days, at least 3 riders said "It's changed" and they outnumber those who said "Still right".
- **Segment page** (Compose and native iOS):
  - Each Trail details row shows its source under the value, and a community value shows a "Community · N riders" badge.
  - A row with open suggestions says so.
  - Tapping a row opens a sheet holding:
    - the value and its source, with the confirmation count;
    - "Still right" and "It's changed";
    - the open suggestions, with agree and disagree;
    - for regulars, "Suggest a different value", which opens a picker of that detail's vocabulary.
  - Regulars also get "Add a missing detail". A segment with no details shows an invitation to regulars.
  - A suggested value on a detail with no value shows as a question until it is adopted.
- **Quick check.**
  - Who sees it: an athlete with an effort on the segment in the last 14 days.
  - What it shows: a card on the Segment page with the folded summary's details, each with Still right and It's changed, plus "All still right" and "Not now".
  - How often: once per segment per season.
- **Trail page roll-up.** A path's details are read from its record as now. When an adopted community value on any of its segments differs from the path's, that row reads "Varies along the trail" and lists the segments and their values, each linking to its segment. Details are changed only on segments.
- **Labels.** A suggestion whose record or author carries `!hide` or `!takedown` is handled as for names: it isn't counted or adopted, an adopted value falls back, and negating the label restores it.
- Riders may change every detail except Maintained by, Website, Trail class and Typical grade. Those are official facts or are measured from elevation.

## Capabilities

### New Capabilities
- `segments/trail-detail-suggestions`: who may suggest a trail detail value, confirm or vote; the suggestion and vote records; adoption, rejection, expiry and cooldown; effective details and their sources; confirmation counts and the may-have-changed flag; labels; and the Segment page's sheet, suggest picker, missing-detail invitation and quick check.

### Modified Capabilities
- `segments/trail-attributes`:
  - `trailAttributes` gains `sources`, and the importer fills it.
  - The segment view serves effective details for every active segment, not only the importer's.
  - Each Trail details row shows its source.
  - The Trail page rolls community values up from its segments.

## Impact

- **Lexicons:**
  - New `feed/trailDetailSuggestion.json` and `feed/trailDetailVote.json`.
  - `feed/defs.json`: `trailAttributes.sources`; on `segmentView`, `trailSources`, `trailSuggestions`, `trailConfirmations`, `canSuggestTrailDetails` and `canVoteOnTrailDetails`; and the suggestion view.
  - All mirrored in `core/lexicon`.
  - `Nsid.indexedCollections` gains both records.
  - The vote joins `AthleteSpaceDeclaration` and gets a `GrantFeature`.
- **AppView:**
  - A Flyway migration adds `trail_detail_suggestions`, `trail_detail_votes` and `segment_trail_adopted`.
  - Indexer branches for both records.
  - An adoption pass beside the naming pass (`SegmentNaming`).
  - `SegmentViews` merges effective details and sources.
  - Label handling joins the name proposals' handling.
- **Importer:** `TrailTags`, `AgencyRules` and the combine step record each attribute's source, and the candidates and paths formats carry `sources`. This is a format version bump, so regions are re-extracted.
- **Apps:**
  - `:feature-segment-api`, `-presenter` and `-ui` gain:
    - the detail sheet;
    - the suggest picker;
    - the missing-detail flow;
    - the quick check;
    - the sources on rows.
  - The Trail page gains the roll-up.
  - The writes go through the outbox.
  - Strings go in `:ui-resources`.
  - The iOS Segment and Trail screens get native versions.
  - `:app-fixtures` gains samples, and screenshots are re-recorded.
