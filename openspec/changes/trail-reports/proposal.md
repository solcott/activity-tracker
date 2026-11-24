# Proposal

## Why

The Segment and Trail pages say what a trail is: its rating, surface and access, which riders can now keep true (`segments/trail-detail-suggestions`). They don't say what the trail is like right now: muddy after rain, a tree down past the bridge, closed for logging. Riders want that most when choosing where to go today, and the people who rode it yesterday know. Photos would show the trail better than any detail can. Design: https://claude.ai/artifact/BH2rtd8QFX2XzLf9V8vtZs

## What Changes

- **Trail reports.** A rider posts a new public `feed.trailReport` record in their repo. It names an `active` segment that isn't a lift, and holds:
  - condition tags, from Dry, Tacky, Wet, Muddy, Snow, Ice, Downed tree, Overgrown, Washed out and Closed;
  - an optional note of at most 300 characters;
  - at most 4 photos.

  A report needs at least one tag or one photo.
- **Who may post.** A report counts only if its author has an effort on the segment that began in the 30 days before the AppView indexed it, of any visibility. The AppView checks this once, when it indexes the report, and keeps an accepted report after that. It ignores a rider's reports on one segment beyond 5 in one day.
- **Photos two ways.** A rider adds a new photo (taken or chosen), or shares one from their own activity that passed the segment. Either way the client re-encodes the photo without its location and uploads it as a new public blob. A shared photo's activity isn't linked. When that activity isn't public, the client says the copy will be public and asks before every share.
- **Conditions fade, photos stay.**
  - A report's tags count for 14 days after it was indexed. After that, the report is listed under Older.
  - Within the 14 days, each rider's latest tagged report on a segment is the one that counts.
  - The summary leads with Closed when the newest tagged report names it. Otherwise it leads with the surface tag reported by the most riders.
  - Photos stay until their author removes them or deletes the report.
- **Segment page** (Compose and native iOS). A Conditions card sits after the elevation profile and before the boards row. It holds:
  - the summary and its rider count;
  - the other current tags with their counts;
  - the latest note;
  - a strip of recent photos;
  - for riders who may post, Report conditions and Add photos.

  The card opens the full list of reports, newest first, with Older below. A photo opens a viewer with its author, date, alt text and its report.
- **Trail page.** A Conditions card at the head of the list shows:
  - each segment with a current report, Closed ones first;
  - a photo strip from all its segments.

  A closed segment is marked Closed on the map, in words as well as colour.
- **Moderation.**
  - Blocks hide each rider's reports and photos from the other, both ways.
  - Mutes leave a muted rider's reports out of the muter's lists, and their tags still count.
  - `!hide` and `!takedown` on a report or its author leave it out of lists and counts. A takedown stops its photos being served.
  - Media labels blur a report's photos.
  - Clients offer Report on another rider's report and on each of its photos, and Block on its author.
  - The author may delete a report, or remove one photo from it.
- **Out of scope.** Asking for conditions in the after-ride quick check, conditions on dive sites, and replies to reports.

## Capabilities

### New Capabilities
- `segments/trail-reports`: the trail report record and who may post one; tags, the note and photos; the 14-day window and the summary; the AppView's queries and segment and trail views of conditions; blocks, mutes and deletes; and the Segment page card, report sheet, add-photos flow, reports list and photo viewer, on Compose and iOS.

### Modified Capabilities
- `segments/segment-page`: the page's order gains the Conditions card after the elevation profile.
- `segments/path-page`: the Trail page gains the Conditions card at the head of its list, and the Closed marker on its map.
- `moderation/labels`: a label on a trail report applies to its photos, as a label on an activity does.
- `moderation/reports`: Report is offered on trail reports and their photos, and a photo report names the trail report.

## Impact

- **Lexicons.**
  - New `feed/trailReport.json`, plus queries `feed/getTrailReports.json`, `feed/getTrailPhotos.json` and `feed/getPathConditions.json`.
  - `feed/defs.json` gains `trailReportView`, `trailConditions` and `trailPhotoView`. `segmentView` gains `conditions` and `canReportConditions`.
  - All of these are mirrored in `core/lexicon`, and `Nsid.indexedCollections` gains the record.
- **AppView.**
  - A Flyway migration adds `trail_reports`, `trail_report_tags` and `trail_report_blobs`.
  - An indexer branch for the record, plus a line in `deleteActor`.
  - Condition views in `SegmentViews` and a new `api/TrailReportViews.kt`.
  - The `/img/media` redirect serves trail report blobs.
  - Labels, blocks and mutes are applied with the existing helpers.
- **Client data.** In `core/data`, a trail report repository writes through the outbox, uploading photos with `UploadQueue` as activity photos do, and re-encodes a shared photo from the athlete's own activity.
- **Apps.**
  - In `:feature-segment-api`, `-presenter` and `-ui`: the Conditions card, report sheet, add-photos screen, reports list and photo viewer.
  - In the Trail page feature: the Conditions card and the map marker.
  - Strings go in `:ui-resources`, and samples in `:app-fixtures`.
  - The iOS Segment and Trail screens get native versions.
  - Screenshots and snapshots are re-recorded.
- **Moderation.** The report sheet gains the trail report subject.
- **Docs.** The CLAUDE.md data-flow paragraph and `.agents/rules/appview.md` mention the new public record.
