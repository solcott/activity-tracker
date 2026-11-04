# Proposal

## Why

Resort skiing and snowboarding are among the most-recorded winter activities, but they can't match any segment today. `SegmentSport.of` maps lift-served `ski` and `snowboard` to no group, and the importer reads only nordic pistes. So a ski day gets no runs, no leaderboards, and no record of the lifts ridden: Garmin's lift splits are unnamed rest laps, and phone recordings have nothing. Segments already cover consent, matching, privacy tiers, statuses and import from OSM, so downhill runs and lifts can reuse all of it.

## What Changes

- Add an `alpine` segment sport group for resort `ski` (any subSport except backcountry and the indoor ones) and `snowboard`. Backcountry and cross-country skiing stay in `ski`. Alpine runs match downhill only, have the 200 m minimum length, get full public leaderboards and Mayors boards like any other group, and have their own speed cap.
- Add a segment **kind**: `run` (the default, every existing segment) or `lift`. A lift is a named ski lift (chairlift, gondola, T-bar …) in the `alpine` group, published only by the segment importer. A lift is matched like a run, but it is never ranked, never on a leaderboard or Mayors board, never renamed, and never marked as a PR.
- The segment importer reads named OSM downhill pistes (`piste:type=downhill` ways and piste route relations) as alpine run candidates. It reads named aerialways as lift candidates, each in its drawn direction only (pistes top to bottom, lifts bottom to top by OSM convention). Candidates carry their kind through staging, the ridden check, the duplicate check and publishing.
- Duplicate checks compare only segments of the same sport group and kind.
- The activity page shows a ski day's **lifts** apart from its runs. Each lift ridden is listed with how many times and the total time on it, at the same visibility as efforts. Lift efforts never show "PR", a rank or an unranked reason.
- Athletes can create alpine run segments from their own ski and snowboard activities, but not lifts.
- **BREAKING (lexicon, additive)**: `feed.segment`, `importer.defs#candidate` and the segment views gain an optional `kind`, and the sport group `knownValues` gain `alpine`. Older clients read a lift as an ordinary segment. Older AppViews don't know `alpine` and drop those records, as the lexicon already specifies.

## Capabilities

### New Capabilities
- `segments/segment-lifts`: ski lifts as a segment kind. Who may publish one, how lift rides are recorded and served, and how an activity shows the lifts it rode.

### Modified Capabilities
- `segments/segment-records`: the sport group list gains `alpine`, each segment has a kind, a lift is indexed only from the importer and only in `alpine`, and duplicates compare within one kind.
- `segments/segment-efforts`: an alpine run matches only top to bottom as drawn. The activity page lists lift efforts in their own section, without PR or rank.
- `segments/segment-leaderboards`: the `alpine` speed cap, and lifts have no leaderboard.
- `segments/segment-mayors`: lifts have no Mayors board.
- `segments/segment-names`: proposals on a lift are ignored.
- `segments/segment-candidates`: candidates carry a kind, which the ridden check and the duplicate check respect.
- `segments/trail-candidates`: `extract` reads named downhill pistes (ways and piste route relations) as `alpine` runs, following their drawn direction only, and named aerialways as whole-line `alpine` lifts. Each candidate holds its kind, and candidates of different kinds never duplicate one another.
- `segments/segment-import`: `publish` checks duplicates within the candidate's kind and writes the kind on the segment.
- `segments/segment-creation`: an athlete may create an alpine run from a ski or snowboard activity, and never a lift.

## Impact

- **Lexicons**: `feed/segment.json`, `importer/defs.json`, `feed/defs.json` (segment views), plus `checkSegment.json`, `searchSegments.json`, and the board `knownValues` in `getSegmentLeaderboard.json` / `getSegmentMayors.json` (add `snowboard`). These are mirrored in `core/lexicon` (`SegmentSport`, `SegmentBoard`, `SegmentSportGroup`, a new `SegmentKind`).
- **AppView**: the segment indexer (kind validation, the importer-only rule for lifts), a Flyway migration adding `kind` to segments and candidates, the matcher, duplicate detection, leaderboard, Mayors and naming queries (excluding lifts), and the views.
- **Segment importer**: `TrailTags` (a piste and aerialway table), the candidates file format version, `publish`.
- **Clients**: the activity page's segments section (`feature/activity`), segment creation, and the segment screen (no leaderboard, Mayor or name proposals for a lift). New strings go in `:ui-resources`, new sample states in `:app-fixtures`, and screenshot references are rerecorded.
- **Out of scope**: piste areas with no centreline, naming the existing FIT lift rest laps, dive sites (a later change), and difficulty grades (`piste:difficulty`).
