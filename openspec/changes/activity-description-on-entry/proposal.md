# Proposal

## Why

Activities already carry a description, and editing or importing one asks for it, but the two ways to create an activity without the details form, manual entry and Record, ask only for a title. An athlete who wants to say how it went has to save first and then edit.

## What Changes

- Manual entry and Record's save step show the details form's description field (its label, placeholder and multi-line size) under the title, on every platform, drawn natively on iOS.
- What the athlete writes there is saved, trimmed, as the activity record's `description`; a blank one saves none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `record/gps-recording`: a finished recording saves the description typed on the screen; the native screen shows it.
- `feed/activity-visibility`: manual entry saves a description; the native form shows it.

## Impact

- `feature/record` contracts (`ManualActivityState`, `RecordState` gain `description`, with `about` for Swift), presenters and UIs.
- `:app-common-ui` `ActivityDescriptionField`, split out of `ActivityDetailsForm`; iOS `ActivityDescriptionSection`, split out of `ActivityDetailsSections`.
- `:app-fixtures` record samples, presenter and UI tests, Android screenshot references and iOS snapshot references.
- No lexicon, AppView or data-layer changes: `ActivityDraft` already carries a description.
