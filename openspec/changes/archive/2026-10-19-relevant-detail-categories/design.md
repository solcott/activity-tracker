# Design

## Context

`fieldsOf(choice, canChoose, eventSink, approvesFollowers, offered)` already filters the pickers to `offered`. `EditVisibility` passes the keys of `DetailCategory.currentTiers(...)`, which reads an `ActivityView` and its streams. `NewActivityVisibility` passes nothing, so it gets `DetailCategory.all`. All three new-activity forms build an `ActivityDraft` before saving: `fromRecording`, `fromImport` (through `importDraft`, and `diveImport` for a batch), and a manual draft built inline in `ManualActivityPresenter`. These forms have a draft rather than a view, so `currentTiers` doesn't apply to them directly.

The GPX and the source file are stored in the athlete's track space, whatever the Map tier is. So in a draft, the Map category means only the polyline, which `ActivityRepository` builds from `route` at publish time. A dive with no GPS gets its GPX from its entry and exit points, but `route` stays empty, so it has no map.

## Goals / Non-Goals

**Goals:**
- One rule for the categories a draft holds, in `core/data`, used by every new-activity form. It mirrors what `currentTiers` sees once the draft is published.
- An athlete's category choices survive changes to the offered set during the form.

**Non-Goals:**
- A sport-to-category table. Sport type only affects the Cadence label, and even that is decided by data.
- Changing what's published, the defaults record, or Settings.

## Decisions

**`ActivityDraft.heldCategories(): Set<String>` in `core/data`.** It returns Start time, plus each `DetailCategory.SUMMARY_FIELDS` category that is set on `toRecord(startedAt)` (skipping the polyline fields, which are null before publish), plus Map when `route` isn't empty, plus `streams?.heldCategories()`. It reuses the same tables the splitter reads, so a field that moves category moves here too. *Alternative:* build a fake `ActivityView` and call `currentTiers`. Rejected: it needs a URI and a published polyline, and gains nothing.

**Recording offers a fixed set.** `ActivityDraft.RECORDED_CATEGORIES = [START_TIME, MAP, SPEED]` sits next to `fromRecording`. A test asserts that a recorded draft's `heldCategories()` is within it. The picker is on screen before any samples exist, so a live set would start with only Start time and gain Map and Speed partway through the recording. *Alternative:* derive the set from the live `RecordingState`. Rejected because the form would jump during recording.

**`NewActivityVisibility` takes the offered set from its presenter.** It gets `fields(offered: Set<String>, runningForm: Boolean)` (or the equivalent state the presenter updates on each composition), filtering `DetailCategory.all` to `offered` in its order. `choice` still holds a tier for every category, as `EditVisibility` does, so hiding a category and showing it again keeps the athlete's pick. A `CategoryChanged` for a category that isn't offered is ignored, as in `EditVisibility`.

**What each presenter offers:**
- Import (one file): `heldCategories()` of the same draft the page preview builds (`importDraft`, after `adding.placed`), so the preview and the pickers agree.
- Dive batch: the union of `heldCategories()` over the selected dives' drafts.
- Manual entry: draft construction moves out of the Save handler into a function both Save and the offer call. The offer then follows the fields: Pace and speed appears once a distance parses, because `toRecord` derives `avgSpeed` from it.
- Record: `RECORDED_CATEGORIES`.

**Cadence label is decided by data.** `DetailCategory.RUNNING_FORM_CHANNELS` is the Cadence channels minus `cadence` itself. `VisibilityFields` gains `runningForm: Boolean = true`, so Settings keeps the combined label. A new-activity form sets it from the draft's streams channels. Edit sets it from its streams' channels: the parts' `channels` in `GetActivityStreams.Output`. `DetailVisibilityPicker` shows a new `detail_category_cadence_only` string ("Cadence") when the flag is false. `CategoryLock` on the activity page keeps the combined label. *Alternative:* label by sport family. Rejected: running power and pedalling cadence both cross sports, and the data is already at hand.

## Risks / Trade-offs

- [Manual entry pickers appear as the athlete types a distance] → Only Pace and speed changes, and it keeps its value. That's accepted as correct behaviour.
- [Adding a recording to an existing activity: the draft holds only the new file's data, while the merged activity may hold more] → The offer follows the placed draft, as the page preview does. The categories already on the existing activity remain editable from its Edit screen.
- [A category that later gains data has no visible choice] → It publishes at its prefilled default, the same as the activity-wide default the athlete set. Since no new-activity form adds data after the pickers show, except recording, whose set is fixed and covers what it records, this doesn't arise today.
- [The `heldCategories` and `currentTiers` rules drift apart] → A `core/data` test publishes-and-reads a draft (via `toRecord` and the layout) and checks that the two sets agree.
