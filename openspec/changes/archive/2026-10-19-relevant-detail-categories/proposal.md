# Proposal

## Why

Recording, manual entry and import show a visibility picker for all nine detail categories, whatever the activity holds. An MTB ride offers "Dive data" and "Cadence and running form", and a manual entry, which has only a start time and maybe a pace, offers nine pickers. Editing already offers only the categories the activity has data in. New-activity forms should follow the same rule.

## What Changes

- Import (one file or a dive batch) offers only the categories the drafted activity holds data in: its summary fields, its stream channels and lap/leg fields, a map when it has a GPS route, and always Start time. A batch offers those held by any selected activity.
- Manual entry offers Start time, plus Pace and speed once a distance is entered, since the record derives an average speed from it.
- Recording offers the categories a GPS recording produces: Start time, Map, and Pace and speed.
- A category that isn't offered keeps its prefilled default in the choice. It has no data, so nothing is published for it. If the offered set changes (for example, a distance is typed or cleared), the athlete's earlier choices are kept.
- On a form for one activity, the Cadence category is labelled "Cadence" unless that activity has running-form data (vertical oscillation, stance time, step length, vertical ratio, stance time balance or leg spring stiffness). Then it keeps the label "Cadence and running form". Default visibility in Settings keeps the combined label.
- Default visibility in Settings is unchanged: there's no activity, so it still offers every category.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/detail-visibility`: "Categories chosen for each activity" changes. New-activity forms now offer only the categories the activity has data in, the rule editing already follows, instead of every category. The Cadence category's label on a form for one activity now depends on whether the activity has running-form data.

## Impact

- `core/data`: a helper on `ActivityDraft` that gives the categories the draft holds data in, reusing `DetailCategory.SUMMARY_FIELDS` and `ActivityStreamData.heldCategories()`.
- `app/presenters`: `NewActivityVisibility` takes the offered categories. `ManualActivityPresenter`, `ImportActivityPresenter` (single and dive batch) and `RecordPresenter` pass them in. `fieldsOf` already accepts `offered`.
- `app/screens`: `VisibilityFields` documents the new rule. It may also carry whether running-form data is present, for the label.
- `composeApp`: `DetailVisibilityPicker` labels Cadence from that flag, with a new "Cadence" string.
- Presenter tests for manual entry, import and record, and a `core/data` test for the helper.
- No lexicon, AppView or storage changes. What gets published for an activity is unchanged.
