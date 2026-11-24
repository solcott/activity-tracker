# Tasks

## 1. Lexicon

- [x] 1.1 Add `feed/trailReport.json`:
  - `segment` (a strongRef);
  - `conditions`, an open enum of the 10 tags, at most 10 items;
  - `note`, at most 3000 bytes and 300 graphemes;
  - `images`, at most 4 `feed.activity#image`;
  - `createdAt`.

  Add `feed/getTrailReports.json`, `feed/getTrailPhotos.json` (`segment` or `path`) and `feed/getPathConditions.json`.

  In `feed/defs.json`, add `trailReportView`, `trailPhotoView` and `trailConditions`, and add `conditions` and `canReportConditions` to `segmentView`. Extend the viewer's recent effort date to 30 days.

  Verify: the JSON parses and the lexicon tests pass.
- [x] 1.2 Mirror the record, views and queries in `core/lexicon`. Add `Nsid.FEED_TRAIL_REPORT` and the query NSIDs, and add the record to `indexedCollections`. Verify: `RecordSerializationTest` round-trips the record with and without images, and `./gradlew :core-lexicon:jvmTest` passes.
- [x] 1.3 Run the `lexicon-auditor` agent on the new NSIDs. Verify: it reports no mismatches, apart from the layers still pending.

## 2. AppView: indexing and media

- [x] 2.1 Add migration `V78__trail_reports.sql` with `trail_reports`, `trail_report_tags` and `trail_report_blobs` and their indexes, and the matching `Tables.kt` objects. Verify: `./gradlew :server-appview:test` migrates on H2.
- [x] 2.2 Write `index/TrailReportIndex.kt`, from repos only:
  - the decode, active non-lift segment, tag-or-photo, 30-day effort and five-a-day checks;
  - known tags only;
  - `indexed_at` kept on rewrite, and a rewrite to empty deleted;
  - deletes and `deleteActor`.

  Wire it into `Indexer`. Verify: `TrailReportIndexTest` covers each scenario in the spec's record, tag, recent-rider, per-day and indexing-time requirements.
- [x] 2.3 Let `mediaRoutes` redirect a `(did, cid)` found in `trail_report_blobs`, refusing it when the report or its author carries `!takedown`. Verify: `BlobRedirectsTest` cases for a served trail photo, a taken-down report's photo, and an unknown CID.

## 3. AppView: conditions and views

- [x] 3.1 Write `segments/TrailConditions.kt`. It applies the 14-day window and keeps each author's latest tagged report, with the block and label filters but not the mute filter. It picks the lead (Closed by the newest counted report, otherwise the surface tag with the most riders, the newest breaking a tie) and builds the summary with the last-report fallback. Verify: `TrailConditionsTest` covers the Muddy-wins, Closed, reopened, only-trail-tags, old-report, changed-mind and photo-only-after-tagged scenarios.
- [x] 3.2 Serve `conditions` on every segment view, without photos in lists, and on `getSegment` with its 4 photos, `canReportConditions` and the viewer's newest effort date. Verify: `SegmentsTest` cases for a recent rider, a signed-out viewer and a blocked author.
- [x] 3.3 Add `api/TrailReportViews.kt` with `getTrailReports` (newest first, `current` flag, mutes omitted, labels attached), `getTrailPhotos` for a segment or a path, and `getPathConditions` (Closed first, then newest; photo count and 5 photos). Register the routes. Verify: `TrailReportViewsTest` covers paging, a muted author, a hidden report, the path roll-up order and path photos.
- [x] 3.4 Update the CLAUDE.md data-flow paragraph and `.agents/rules/appview.md` for the new public record and its photos. Verify: `scripts/check-agent-sync.sh` passes.

## 4. Shared client logic

- [x] 4.1 Add `TrailReportRepository` to `core/data`. It:
  - uploads photos through `UploadQueue` and writes the record through the outbox with a TID rkey;
  - re-encodes a shared activity photo from the athlete's own blob (public from the PDS, a tier through `SpaceMember`), without metadata;
  - supports delete and remove-photo, deleting the record when the last photo of a tagless report goes;
  - updates the cached segment view optimistically.

  Verify: jvmTest with `MockEngine` and an in-memory database covers posting, sharing a tier photo, removing the last photo and deleting.
- [x] 4.2 Extend the `:feature-segment-api` contracts:
  - the conditions card state;
  - the report sheet state (tag toggles, the note `TextFieldState`, photos, posting, and the non-public confirm);
  - the add-photos state (activities with their visibility and photos, and the 4-photo cap);
  - the reports list and photo viewer screens.

  Add `TrailReportTexts`: tag names, "3 riders · latest 2 days ago", "No reports in the last 14 days", "Last report: Dry, 5 weeks ago", "You rode it 3 days ago. How was it?", and "Reports on 3 of 8 segments, last 14 days". Add the strings to `:ui-resources` and run `generateIosResources`. Verify: `TrailReportTextsTest` covers the spec's card wording.
- [x] 4.3 Add the presenters in `-presenter`:
  - the card's actions, shown only when `canReportConditions`;
  - the sheet: Post report disabled with no tag and no photo, and the confirm before sharing a non-public photo every time;
  - add photos: the viewer's efforts via `getSegmentEfforts`, activities newest first, 4 at most;
  - the reports list: paging, and a menu of Report and Block for others and Delete for the viewer's own;
  - the photo viewer: paging, and Remove from trail for the viewer's own.

  Verify: presenter tests for each flow.
- [x] 4.4 Add the Trail page presenter's `getPathConditions` call, and the `closed` flag on its map line model. Verify: a presenter test for Kingdom Loop's roll-up order and the closed line.

## 5. Compose UI

- [x] 5.1 Build, from `ui/adaptive` wrappers:
  - the Segment page's Conditions card, placed after the elevation profile;
  - the report bottom sheet;
  - the add-photos screen;
  - the reports list;
  - the photo viewer, with media-label blur.

  Add the sample states to `:app-fixtures`. Verify: `SegmentUiTest` cases for the card states (Muddy, Closed, empty with a ride, none) and re-recorded screenshots reviewed against the design canvas.
- [x] 5.2 Build the Trail page's Conditions card at the head of its list, the "Closed · 1 day ago" row line, and the map's Closed label. Verify: a `PathUiTest` case and re-recorded Trail page screenshots.
- [x] 5.3 Offer Report on trail reports and their photos with the trail report as the subject, and photo position in the note. Verify: report sheet tests for both subjects.

## 6. iOS

- [x] 6.1 Build native SwiftUI versions on the Segment screen:
  - the Conditions section;
  - the form sheet with checkmark tag rows, Cancel and Post;
  - `PhotosPicker` plus the viewer's ride photos;
  - the reports list and the photo viewer.

  Use `iosApp/iosApp/Design`, keeping controls trailing. Verify: snapshot tests on the iOS 26 simulator, and a run on iPhone 17 Pro.
- [x] 6.2 Build the Trail screen's Conditions section and the map's Closed annotation. Verify: a snapshot test, and `:iosExport:checkSwiftExportStubs` passes in the iOS build.

## 7. Integration

- [x] 7.1 Run `./gradlew check`. On the local stack, seed a rider's recent effort, post a report with a tag and a shared photo, and confirm on Android and iOS that:
  - the Segment and Trail pages show the conditions and the photo;
  - a second rider's Closed report leads;
  - a `!takedown` label removes the report and stops its photo.

  Verify: `check` passes, and screenshots show each step.
