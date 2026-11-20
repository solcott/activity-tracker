# Design

## Context

See proposal.md for why. The current state that shapes the approach:

- **OSM data.** `OsmReader` already keeps every tag of each kept way (`OsmWay.tags`) and route relation (`OsmRelation.tags`). `TrailTags` reads only the tags that decide sport groups, exclusion, junctions and direction.
- **Agency data.** `scripts/segment-fetch.sh` fetches agency layers with `outFields=*`. `AgencyReader` turns a feature's properties into an `OsmWay` whose tags are `name`, `agency:groups` and `agency:key`, so attribute fields are dropped there. The field names and values in the specs come from the live layer metadata, read on 2026-11-19:
  - USFS `EDW_TrailNFSPublish_01`: `trail_surface`, `trail_class`, `typical_tread_width`, `typical_trail_grade`, `accessibility_status`, `pack_saddle_*`, and the use season fields.
  - NPS `National_Park_Service_Trails`: `TRLSURFACE`, `TRLCLASS`, `SEASONAL`, `MAINTAINER`.
  - BLM GTLF: `OBSRVE_SRFCE_TYPE`.
- **Trails and candidates.** A `Trail` is a list of edges, each naming its way (`edgeWays`) and its points. A candidate is a range of a trail's edges, so the length along each way is already known when candidates are cut. Paths are built in `Paths.kt` from the same ways.
- **How `climb` flows today.** `climb` is the template for these attributes:
  1. `CandidateEntry` in the candidates file.
  2. `importer.defs#candidate`.
  3. A `segment_candidates.climb` column (V72).
  4. Returned by `checkSegmentCandidates`.
  5. Written by `publish` and kept current by `refine`.
  6. Served from the stored segment record (`Segments.record`) only for the importer's segments (`SegmentViews`).
- **Paths in the AppView.** The `paths` table keeps no record JSON, only columns.

## Goals / Non-Goals

**Goals:**
- One normalized vocabulary shared by the importer, the lexicon, the AppView and the apps, so each layer never re-maps raw tags.
- Attributes that are deterministic for a given extract, so re-running `extract` and `refine` doesn't churn records.
- Attributes on records already published, filled in by the existing `refine` and `publish-paths` re-runs.

**Non-Goals:**
- Filtering or searching segments by attribute, and showing attributes on browse rows or map lines. A later change can build on the served data.
- Attributes on athlete-made segments: no source data, and anything athletes write is not served.
- Showing where along a segment each attribute changes. Only one combined value per attribute is kept.
- OSM `ref`, `incline`, `description` and `mtb:type`.

## Decisions

### One `trailAttributes` object in `feed.defs`, reused everywhere
The same object goes on `feed.segment#trail`, `feed.path#trail`, `importer.defs#candidate.trail`, `segmentView.trail` and `pathView.trail`. Mirror it once in `core/lexicon` as `TrailAttributes`, with open-enum string constants like the existing `SegmentClimb` words.

*Alternative considered:* flat fields on each record. Rejected: five copies of about 20 fields, and the AppView sanitizer would be written twice.

### Normalize per way in the source's rules; combine in one place
Add to `TrailRules` a `facts(tags, group): WayFacts`, holding the normalized value of each attribute for one way.
- `TrailTags` implements it from OSM tags.
- Each `AgencyRules` entry implements it from properties. `AgencyReader` keeps the attribute fields in the way's tags under an `agency:` prefix.
- `group` is passed because USFS open dates depend on the candidate's use.

A single `TrailAttributeCombiner` takes `(WayFacts, lengthMeters)` pairs and applies the combining rules. `CandidateCutter` feeds it the edges of each candidate. `Paths` feeds it every way of a path, using the most open group for USFS open dates.

*Alternatives considered:*
- Putting normalized values back into OSM-style tags: this mixes vocabularies.
- Combining in the AppView: the AppView doesn't have the ways.

### Route relation inheritance is resolved before cutting
When building trails, compute for each way the `dog`, `horse`, `wheelchair`, `operator` and `website` its named route relations agree on, and use them only where the way has none. This is done once per extract, from `OsmData.relations`, in a map from way id to inherited tags.

### Lengths: deterministic, rounded down, small shares dropped
Shares are of the length whose surface is known, not of the whole line: an untagged way is no other surface, and a page reading "74% dirt" for a trail that is all dirt where tagged suggests a quarter of it is something else. The 25% coverage rule still leaves surfaces out when too little is tagged. Shares are rounded down to whole percents, which keeps the stored value stable when a tiny edit moves a node, and only a share that rounds to 0% is dropped. An earlier 5% cutoff hid a trail's short gravel stretch that its segments showed (Canyon Hollow read "97% dirt" while two of its segments read "92% dirt · 7% gravel"), so a trail and its segments disagreed. The "at least 50 m" rule for difficulties ignores a single mis-tagged short way, while still flagging a real hard pitch.

### Storage in the AppView
- **Candidates:** migration `V74__trail_attributes.sql` adds a nullable `trail TEXT` column (AtJson-encoded `TrailAttributes`) to `segment_candidates`.
- **Paths:** the same migration adds a nullable `trail TEXT` column to `paths`.
- **Segments:** no new column. Views decode `trail` from `Segments.record`, as they already do for `oneWay` and `climb`.

The SQL is plain `ALTER TABLE … ADD COLUMN`, which works on both H2 and Postgres.

### Sanitize on the way in and on the way out
Untrusted values are cleaned by one function, `TrailAttributesSanitizer`, at two points:
- **Staging:** `putSegmentCandidates` sanitizes, though the importer is trusted.
- **Serving:** segment and path views sanitize, because records can be written by anyone (the views check that the author is the importer, but the record can still hold anything).

The sanitizer checks vocabularies and ranges, pairs `openFrom` and `openUntil`, limits operator graphemes, and parses `website` as an absolute http(s) URL with `java.net.URI`, requiring an http(s) scheme and a host (Ktor's `Url` accepts `https:///x`). Clients still check the scheme again before opening a link.

### The importer writes only what the AppView would keep
The importer drops a website that isn't an absolute `http(s)://` URL of at most 512 characters and an operator over 100 graphemes before writing, as the AppView's sanitizer would. Otherwise `refine` would see its record differ from the cleaned copy on every run and rewrite it each time.

### Smaller mapping choices
- A `;` list in `surface` uses its first value, and `piste:grooming=classic;skating` counts as `classic_skating`.
- An open-ended month range ends on the month's last day (February 28). A whole-year range (`Jan-Dec`, USFS `01/01-12/31`) gives no open dates.
- NPS mixtures such as `Earth/Gravel` are `dirt`, and unlisted surfaces such as `Boardwalk` are `other`. Trail class reads `3`, `TC3` and `Class 4: …`.
- On a path, a way shared by several sport groups uses the USFS dates of the group open longest, with no dates counting as open longest. Road paths read their roads' tags.
- An agency path fills an OSM path's season (seasons, `seasonal` and open dates) as one attribute.
- Route relation tags are inherited only by OSM ways, once per extract. Lift candidates carry no attributes.

### Refine and publish-paths compare decoded values
Both decode the existing record's `trail` and compare it with the new value as `TrailAttributes` objects. Comparing JSON text would see a difference whenever field order changes.

### Apps: one presenter-built list of rows
`SegmentState` and `PathState` gain `trailDetails(units)`, as `chart(units)` is built, since the rows need the athlete's units, which presenters don't have.
- **Building the rows:** `TrailTexts.rows` builds them from the served attributes, filtering difficulty scales by sport group on the Segment page. Each row is a label and value as `AppText`, so Compose and SwiftUI resolve the same strings. A `TrailTexts` object in `:feature-segment-api` turns each value into `AppText`.
- **Website row:** carries the URL for the UI to open. Compose uses `LocalUriHandler`, as `DiveSiteUi` does, and iOS uses `openURL`.
- **Widths:** shown only as the kind of trail the width makes (singletrack under 1.5 m, doubletrack under 3 m, else road width), with no number: riders and runners think in those terms, and a mapped width is rough anyway. Ski and alpine segments, where those words mean nothing, show no width.
- **MTB scales:** shown as a difficulty word and what it means on the ground ("Intermediate: roots, rocks and small steps"), never the S code, which most riders don't know.
- **Open dates:** formatted with kotlinx-datetime month names through the shared date formatting already in `AppText`.
- **Placement:** after the Leaderboard and Mayor on the Segment page, and in the Trail page's header under its credit, so the list's scroll offset when a section folds is unchanged.
- **Wording:** IMBA reads White to Double black as OSM numbers it (0–4), visibility Excellent to No visible trail, smoothness Very smooth to Impassable, trail class "3 (Developed)". Surface words are lowercase in a mix ("70% paved · 30% gravel") and capitalised alone ("Gravel").
- **iOS:** draws the section natively in `iosApp/iosApp/Screens` from the same rows.

### File format bumps
- The candidates file goes from `IMPORTER_VERSION` 5 to 6, with an optional `trail` on `CandidateEntry`.
- The paths file goes from `PATHS_VERSION` 2 to 3, with an optional `trail` on `PathEntry`.

Old files are refused, as on earlier bumps. Region runs re-extract every time anyway.

## Risks / Trade-offs

- **[Sparse or wrong OSM tags give misleading values]** → Attributes need 25% coverage (strict ones excepted), unknown is never shown as a default, and a data credit already names OSM or the agency.
- **[Strict `dog: no` from one stretch hides a mostly dog-friendly trail]** → Intended: being turned away mid-run is worse than missing a trail. The row says only what the data says.
- **[Record size grows]** → The object is small: at most about 25 fields, with surfaces capped at 8 entries. That is well under the record limits next to a polyline.
- **[Website links can be abused]** → Only http(s) URLs are served. Clients re-check the scheme and open the URL in the system browser, never in a web view inside the app.
- **[Refine rewrites many segments on first run]** → Expected one-off backfill. It goes through the existing `applyWrites` rate limits.
- **[Agency value lists drift]** → Unknown values give no attribute rather than failing. Tests pin the values seen today.

## Migration Plan

1. Deploy the AppView first. The migration adds nullable columns, and old importer runs keep working because `trail` is optional.
2. Release the apps. Old apps ignore the new view field.
3. For each region, run `scripts/segment-import.sh <region> …`. It re-extracts at version 6, stages, publishes, refines (backfilling `trail` on published segments) and runs `publish-paths` (backfilling paths).

**Rollback:** older AppViews and apps ignore `trail`, and the columns can stay.

## Open Questions

- Exact wording of a few rarely seen values, such as `very_horrible` smoothness and `difficult_alpine_hiking`. These can be settled when the strings are written, without changing the vocabulary.
