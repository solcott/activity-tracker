# Design

## Context

- **Trail detail suggestions as the closest precedent.** `segments/trail-detail-suggestions` already has a public per-segment record with a check when it's indexed, label filtering (`unlabelledSuggestion`, `listedSuggestion` in `segments/TrailDetails.kt`), and a Segment page section in Compose and SwiftUI. Trail reports follow that shape.
  - They need no adoption pass: conditions are summarised when a view is read.
  - They add photos.
- **Activity photos.**
  - The client compresses and re-encodes a photo (`NewPhoto`, `ActivityPhotos.kt`) and uploads it through `UploadQueue`.
  - The record holds the blob.
  - Views serve `/img/media/{did}/{cid}` URLs (`api/BlobRedirects.kt`), which redirect to the PDS. They do so only for a CID the AppView indexed for that author (`ActivityMediaBlobs`, table `activity_media`), and never for a taken-down subject.
- **Recent rides.** `segment_efforts.started_at` (V76) holds each effort's start in epoch ms, at every visibility. It is 0 when the activity row is gone, so such an effort never counts as recent.
- **Labels, blocks and mutes.** These live in `Labels`, `Blocks` and `Mutes`. Comments already apply mutes to lists only.

## Goals / Non-Goals

**Goals:**
- One record type, with no new private storage.
- Conditions computed fresh on every read, so the 14-day window and the summary are never stale.
- Photos served through the same redirect and takedown path as activity photos.

**Non-Goals:**
- Photo locations or a photo map layer: the client strips location.
- Linking a shared photo back to its activity.
- Push notifications for closures.
- Editing a report's tags in place. The client offers delete and remove-photo only, though the AppView accepts any rewrite.

## Decisions

### One record: `feed.trailReport`

The record has these fields:
- `segment`: a strongRef.
- `conditions`: a string array of at most 10, an open enum (`dry`, `tacky`, `wet`, `muddy`, `snow`, `ice`, `downedTree`, `overgrown`, `washedOut`, `closed`).
- `note`: at most 3000 bytes and 300 graphemes.
- `images`: at most 4 items of the existing `feed.activity#image`, which gives alt text and aspect ratio.
- `createdAt`.

Separate photo and condition records were considered and rejected. Riders post both together, the design shows a photo with "its report", and one record keeps one moderation subject per post.

The record lives in the repo, because every report is public. A non-public tier would hide conditions from exactly the people deciding where to ride.

### Tables (one migration, `V78__trail_reports.sql`)

- **`trail_reports`.** Columns:
  - `uri` (primary key), `cid`, `author_did`, `segment_uri`;
  - `note`;
  - `has_conditions`;
  - `indexed_at`: first indexed, kept on rewrite;
  - `day_utc`: for the five-a-day limit.

  Indexes on `(segment_uri, indexed_at)` and `(author_did, segment_uri, indexed_at)`.
- **`trail_report_tags`.** `(report_uri, tag)`. Only known tags are stored.
- **`trail_report_blobs`.** `(report_uri, author_did, cid, position, alt, width, height, mime)`. These rows let `/img/media` serve these blobs.

A path's photos join `trail_report_blobs` to `trail_reports` to the existing segment–path link table.

### Indexing

`index/TrailReportIndex.kt` runs for the repo only, following `TrailDetailIndex.kt`.

On a new URI, it checks in this order:
1. The record decodes.
2. The segment is indexed, `active` and not a lift.
3. At least one known tag or one image is present.
4. An effort by the author on the segment has `started_at >= now - 30d`.
5. Fewer than 5 accepted reports exist for this author, segment and `day_utc`.

On a rewrite, it keeps `indexed_at` and replaces the tags, note and blobs. It does not re-check eligibility, so an accepted report can always be edited. A rewrite that leaves neither a tag nor an image deletes the row.

Deletes and `deleteActor` drop the rows. The Jetstream filter gains `Nsid.FEED_TRAIL_REPORT`.

Checking eligibility once at index time matches suggestions and the existing "never trust record timestamps" rule.

### Summary computed on read

`TrailConditions.summarize(segment, viewer)` runs one query over `trail_reports ⋈ trail_report_tags` for the last 14 days. It:
1. drops reports from blocked authors;
2. drops reports labelled `!hide` or `!takedown`, on the record or its author;
3. keeps each author's newest report that has tags;
4. picks the lead by the spec's rule, in Kotlin.

Mutes are not applied to the summary.

The work is bounded: one segment's reports in 14 days is small. For `getPathConditions`, the same query runs grouped by segment over the path's segment list.

Caching the summary was considered and rejected. The window moves every hour, and blocks make the result per viewer anyway.

### Views and queries

- **`segmentView`** gains two fields:
  - `conditions` (`feed.defs#trailConditions`): `lead`, `leadRiders`, `latestAt`, `tags[] {value, riders}`, `latestNote` (a `trailReportView`), `photoCount`, `photos[]` (up to 4 `trailPhotoView`), and `lastReportAt` / `lastReportTags` for the "Last report" empty-state line.
  - `canReportConditions`, filled only by `getSegment`, together with the existing `recentEffort`-style date of the viewer's newest effort. The trail detail suggestions change already serves `recentEffort` within 14 days. Here the field is extended with the full date when it falls within 30 days.

  Feed and browse views carry `conditions` without the photos, to keep lists light.
- **Queries.** `getTrailReports(segment, cursor, limit)`, `getTrailPhotos(segment | path, cursor, limit)` and `getPathConditions(path)` are added to `api/TrailReportViews.kt`. Cursors are `indexed_at` plus `uri`.
- **`trailReportView`**: `uri`, `cid`, `author` (`profileViewBasic`), `conditions`, `note`, `images` (`imageView`), `indexedAt`, `current`, `labels`.

### Media redirect

`mediaRoutes` (in `BlobRedirects.kt`) also accepts a `(did, cid)` that matches a row in `trail_report_blobs` whose report isn't taken down, and whose author isn't taken down either.

This is the only server change needed for photos. Photos stay on the rider's PDS, as activity photos do.

### Client writes

`core/data` gains a `TrailReportRepository`. It enqueues photo uploads with `UploadQueue`, then `putRecord`s through the outbox with a TID rkey. It optimistically adds the report to the cached segment view's conditions and photos.

A shared activity photo is handled in three steps:
1. The client fetches the athlete's own blob: from their PDS for a public activity, or through `SpaceMember.getRecord`'s blob read for a tier.
2. It decodes and re-encodes the photo with the existing photo pipeline, dropping metadata.
3. It uploads the result as a new repo blob.

The new blob's CID differs from the original, so nothing links the copy back to the private activity.

Remove-photo rewrites the record. Removing the last photo of a report with no tags deletes the record.

### Contract and UI

`:feature-segment-api` gains:
- the conditions card state (`TrailConditionsCard`);
- `ReportSheetState`, whose tags live in presenter state and whose note is a `TextFieldState`;
- `AddPhotosState`, with the rider's activities that passed the segment. The presenter lists the viewer's own efforts with `getSegmentEfforts(segment, actor = viewer)`, newest first, and reads each effort's activity and its photos from the client's cache, or from `getActivity`. No new query is needed;
- the reports list and photo viewer screens;
- `TrailReportTexts`: tag names, "3 riders · latest 2 days ago", the empty and closed lines.

The Trail page feature gains a `PathConditionsCard`, with the `getPathConditions` call made in its presenter, and a `closed` flag on its map line model, which draws the label.

The new screens are opened as Circuit screens from the Segment page. iOS gets SwiftUI views over the same states, built with the `iosApp/iosApp/Design` components: a `Section`, a `.sheet` with a `Form`, and `PhotosPicker`. Tag rows are checkmark rows, keeping the controls trailing.

### Tag styling

Tags are neutral tonal chips. Closed alone uses the error container with a closed icon. Trail-sign green, blue and black stay reserved for ratings, per DESIGN.md.

## Risks / Trade-offs

- **[A rider reports before the matcher has made their effort.]** The client offers Report conditions only when `canReportConditions` is true, which needs the effort to exist. The AppView never re-checks a report it ignored, so a racing client loses that report. Matching takes seconds, so this is rare.
- **[Riders with segment matching off have no efforts and can't report.]** This is accepted, and the same holds for suggestions. The sheet's ineligible state explains that matching must be on.
- **[Photo spam or abuse in a public gallery.]** Mitigations: the five-a-day cap, the recent-rider rule, Report on every photo, labels and blocks.
- **[A shared photo reveals a non-public ride's moment.]** The rider confirms every share. The copy carries no link and no location.
- **[One false "Closed" leads the summary.]** Any later tagged report from another rider without Closed clears it, and labels can remove an abusive one.
- **[The summary query per segment view in lists.]** List views carry only the lead and counts from an indexed range scan. If it shows up in metrics, cache per segment for a minute before the block filter.

## Migration Plan

The change is additive: a new migration, a new record and new optional view fields. Older clients ignore the new fields. Rollback means turning off the client entry points; the tables can stay.
