# Design

## Context

See proposal.md for why. Requirements are in `specs/feed/activity-trim/spec.md`. The current data path, which the trim has to fit:

- **Drafts.** Recording and import both become an `ActivityDraft` (`core/data/.../activity/ActivityDraft.kt`):
  - from a recording via `ActivityDraft.fromRecording`, with totals from `RecordingState`;
  - from a file via `ActivityDraft.fromImport`, with totals from `DecodedActivityMapper`, which prefers the device's session totals.

  `ActivityRepository.queue(draft, …)` fans each draft out to the record, details per tier, track GPX, segment track, streams with zones, source and devices.
- **Saved activities.** Nothing rewrites a saved activity's streams or track, except `ActivityManager.build`. It is the private pipeline behind combine, `setSources` and `removeMember`:
  1. rebuild;
  2. `privacy.forPublish()`;
  3. `ActivityLayout.split`;
  4. `queueLayout`;
  5. `tracks.put`;
  6. `segmentTracks.put(replacing = true)`;
  7. `streams.put`;
  8. `localStreams` and `rewrites`.

  `drainBeforeMoving` throws `PendingWritesException` while writes for the rkey are queued.
- **Reading a saved activity back.** The owner's device has no local copy. The full route comes from the `feed.activityTrack` GPX (`ActivityRepository.ownRoute`, with a time on each point). The streams come from `ActivityPlaces.read(uri).data`: time-keyed and merged across tiers, but possibly halved by `PdsActivityStreamsStore.fitted()`.
- **AppView.** A changed segment track already re-queues matching (`upsertSegmentTrack` → `jobs.enqueueTrack`), which replaces the activity's efforts and candidate riders. Grouping rechecks overlap from the same track.
- **Recording.** There is no separate save screen. The finish form is part of `RecordScreen`, and `RecordEvent.Finish` publishes. The import preview (`ImportActivityScreen`) already draws `ActivityMap`.

## Goals / Non-Goals

**Goals:**
- One pure trimmer serves drafts and saved activities alike, so recording, import and the page all trim identically.
- A saved trim is a same-rkey rewrite through the existing outbox pipeline, with no new write paths.
- Dragging stays smooth on a two-hour per-second activity.

**Non-Goals:**
- Re-parsing the source file to trim at full resolution.
- Trimming on the AppView.
- Keeping cut data anywhere for undo.

## Decisions

### 1. A pure `ActivityTrimmer` over a kept span in seconds
In `core/data/commonMain/.../activity/trim/`, `ActivityTrimmer.trim(input, span)` returns the trimmed summary, streams, route and laps.
- **Input:** a `TrimInput`, which is the summary fields, `ActivityStreamData`, the full route (`List<LocationSample>`, may be empty), and `startedAt`.
- **Span:** a `KeptSpan(from, to)` in seconds since `startedAt`. The span is time-based whatever the mode: distance mode only maps handle positions to times. A start at distance d maps to the last sample with distance ≤ d, and an end to the first sample with distance ≥ d, which is the spec's "also cuts standing time".
- **Re-basing:**
  - `startedAt` moves by `from`;
  - stream `time` values shift by `-from`;
  - samples outside the span are dropped;
  - laps are clipped at the span, and laps left empty are dropped;
  - lengths outside the span are dropped;
  - route points are filtered by time.
- **Manual entries** (no streams, no route) only move `startedAt` and set elapsed to `to - from`, with moving time clamped to elapsed. Distance is kept.

**Alternative rejected:** re-parsing the source file. Recordings have no source, a source may have been dropped when it was too big, and the file path couldn't serve recordings or GPS-only activities.

### 2. Additive totals subtract the cut; the rest recompute from what's kept
Recomputing distance from the kept streams would make a one-minute trim change a device-measured 45.12 km into 44.70 km, because device totals and stream sums disagree. So the totals are handled in four groups:
- **Additive totals** (distance, moving time, elevation gain and loss) are `original − cut`. The cut parts are measured from the streams, or from the route when there is no distance stream. Gain and loss of each cut part use `ElevationGain.of`, and moving time uses the shared 0.5 m/s rule. `RecordingState` and `DecodedActivityMapper` each have a private copy of that rule today; it moves to one `MovingTime` helper both call.
- **Averages and maxima** (speed, heart rate, power, cadence, temperature) and time in zones are recomputed from the kept streams through `StreamAnalytics` and `PerSecond`, so gaps follow `feed/stream-resampling`.
- **Totals that can't be measured** (calories, training load and the like) are scaled by kept moving time / original moving time. This is the precedent `CombinedSummary` already set.
- **Elapsed time** is `to − from`.

When nothing is cut at one end, nothing is subtracted for that end. A trim that cuts nothing is refused before this point.

### 3. A saved trim reuses `ActivityManager.build`'s tail
- The tail of `build` (steps 2–8 in Context) is extracted into a private `rewrite(built, …)`. Combine and the new `ActivityManager.trim(uri, span)` both call it.
- `trim`:
  1. calls `drainBeforeMoving(uri)`, which surfaces `PendingWritesException` as "still being sent";
  2. reads `StoredActivity` and `ownRoute`;
  3. runs the trimmer;
  4. rewrites the trimmed activity at the same rkey and visibility, with each detail category at its own tier through `ActivityLayout.split`.
- The trim is two calls. `loadForTrim(activity)` reads everything while the editor opens (it needs the network): the stored activity, the track, the zones and the segment track's marks. `trim(source, span)` only queues writes, so confirming works offline.
- The segment track goes through `segmentTracks.put(replacing = true, marks = …)` with the marks read at load. A plain `put` writes a fresh record without them, so the leaderboard exclusion and group-left choice would otherwise be lost.
- The track GPX is rewritten from the trimmed route.
- `feed.rewrites` updates the cached page.
- No AppView code changes: the changed segment-track CID re-queues matching, and pairs recheck overlap.

**Alternative rejected:** extending `ActivityEdit`, which only puts the record and would leave the streams and track stale.

### 4. Drafts are trimmed before `queue`
- `RecordPresenter` and `ImportActivityPresenter` hold a `KeptSpan?` per draft.
- On Finish or Import, `ActivityDraft.trimmed(span)` (a thin wrapper over the trimmer) replaces the draft's summary, `route`, `gpx`, `streams` and laps before `ActivityRepository.queue`.
- The source bytes are untouched, which satisfies the `feed/activity-source-file` delta.

### 5. A `feature/trim` screen that works on a saved activity or a draft
- **`-api`:**
  - `TrimScreen(target: TrimTarget)` and its `TrimState`/`TrimEvent`.
  - `TrimScreen(uri: String? = null, draftId: String? = null)`: a saved activity by its URI, or a draft by its id.
- **Draft data goes both ways through a session-scoped `TrimDrafts` holder** in `core/data` (`SessionScope`), not a Circuit `PopResult`: the presenter host that drives the iOS app passes no pop result. The record and import presenters `offer(id, input)` the draft's `TrimInput` and observe `span(id)`; the trim screen reads the input and, on Save, `apply(id, span)` before popping. Undoing an applied trim is `apply(id, null)`; the presenter that offered a draft `drop`s it when it publishes or leaves. Screens stay small and serialisable.
- **`-presenter`:** `TrimPresenter` builds a `TrimModel` once, with prefix sums of distance and moving time over samples and per-sample altitude. Readouts, the cut summary and the totals for each handle move then cost O(log n) plus one O(kept) gain pass, which is fine for 7,200 samples per hour at 1 Hz.
  - In `Saved` mode, Save shows the confirmation and then calls `ActivityManager.trim`.
  - In `Draft` mode, Save applies the span to `TrimDrafts` and pops, with no confirmation, since nothing is published yet.
  - `TrimState` follows `ui/content-loading` for loading and errors.
- **State contents:**
  - the mode;
  - the kept span;
  - the readouts as `AppText`/`StatText`;
  - the before and after totals;
  - the cut summary;
  - the segment notes;
  - the suggestion;
  - the profile series;
  - the map's route and cut;
  - `canSave`;
  - the dialog.

  It holds no `Pair` or Compose types, as required for Swift export.
- **`-ui` (Compose):**
  - Layout: one column on compact widths, and the map beside a panel on expanded widths, as in mockup A.
  - Profile: the shared `LineChart` (`LineChartUi`) with the existing `AppRangeSlider` under it, aligned to the chart's width. Material's range slider already drags, moves the nearer thumb on a tap, reads as two sliders to screen readers and takes arrow keys; the wrapper gains labels for its two thumbs ("Trim start", "Trim end") and their value text. A new overlay was dropped as duplicating it.
  - Map: `ActivityMap` gains `cut = true`, which draws its route dashed in `outline` and its `highlight` (the kept range) in the route colour, with markers at the highlight's ends.
  - Exact values: tapping a readout opens an adaptive bottom sheet or dialog with an `AppTextField` and steppers.
- **iOS:** a native `TrimActivityView` using Swift Charts and MapKit (`ActivityRouteMapView` gains the same cut styling), driven by the exported presenter through `IosApp`.

### 6. Suggestion: one detector over speed
`TrimSuggestion.of(input)` in `core/data`:
- "Moving" is speed > 0.5 m/s, sustained when 30 of 40 s are moving.
- **Start cut:** the standing time before the first sustained moving stretch, if ≥ 60 s.
- **End cut**, either of:
  - standing time after the last sustained moving stretch, if ≥ 120 s;
  - a stop of ≥ 180 s followed by a tail that is ≤ 15% of distance and ≤ 15 min, with a median speed ≥ max(1.5 × the activity's median moving speed, 25 km/h).

  This catches a drive home without a per-sport speed table.
- Activities without a speed or distance stream get no suggestion.

The copy says "drove" only for the fast-tail case, and "stood" or "stopped" otherwise.

### 7. Gating
- `ActivityDetailPresenter` computes `canTrim` and the `ActivityMenuAction.Trim` item goes in `ownerMenu`. `canTrim` is true for the owner when all of these hold:
  - the activity isn't combined;
  - `combinedInto` is null;
  - it has no `legs`;
  - it has no `dive`;
  - it has no `sets`.
- The same rule is used by the record and import offers.
- iOS adds the case to `ActivityPageMenu.swift`.

## Risks / Trade-offs

- **[Streams were halved to fit the blob]** Trims are then measured on coarser samples. → The additive-subtraction rule keeps totals near the device's, and handle positions round to the nearest stored sample. This is acceptable for an end cut.
- **[Lost cut data]** A destructive trim loses the cut data on the PDS. → The confirmation says so, and an imported activity's original file stays downloadable whole.
- **[Suggestion false positives]** A fast descent after a long café stop could be suggested as a drive. → It is only a suggestion, the copy names what it cuts, and it is undoable before save.
- **[Concurrent edit on another device]** → `rewrite` starts from the last queued put, as edits do. The PDS last write wins, as for every other edit.
- **[Map draws the private full route]** → Only the owner reaches the screen, and only their device reads the `self` track. No new data leaves the device unprotected.

## Migration Plan

No lexicon or AppView change, and no data migration. The feature ships with the client release. Rolling back removes the entry points, and trimmed activities stay valid ordinary activities.

