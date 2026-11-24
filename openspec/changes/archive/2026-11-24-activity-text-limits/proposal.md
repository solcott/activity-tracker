# Proposal

## Why

`feed.activity` limits a title to 100 graphemes and a description to 1000, but nothing holds an activity to them. The edit, import, manual-entry and Record forms save whatever is typed, imports carry a file's names and notes whole, the PDS stores app.activitytracker records unchecked, and the AppView indexes and serves the text however long it is. Other fields in the app keep their limits; these should too.

## What Changes

- The four forms that write a title and description show a counter under each field once it nears its limit, red past it, and hold Save (Record's Finish & save) back while either is over. Nothing refuses typing, so text already too long can be shortened. The same rule decides it on the shared and the native forms (`ActivityTextLimits`).
- Text is counted as it saves, trimmed, in characters: each is at least one grapheme and at most four bytes, so what fits passes both the lexicon's `maxGraphemes` and `maxLength`.
- Every record a client publishes or rewrites trims its title and description and cuts them to their limits (`ActivityRecord.publishedTitle`, `publishedDescription`), so a batch import, which has no form, still writes valid records.
- The AppView cuts an indexed activity's title and description to their limits, as it does the record's other free text.

## Capabilities

### New Capabilities

- `feed/activity-text`: how long an activity's title and description may be, and how the forms, the client's records and the AppView keep to it.

### Modified Capabilities

None.

## Impact

- `core/lexicon` `ActivityRecord` (limits, counting and cutting), `core/data` (`ActivityDraft.toRecord`, `ActivityManager.edit`), `:app-common-api` `ActivityTextLimits` and a `details_length` string.
- `textsFit` on the edit, import, manual-entry and Record states, `EditActivityState.canSave`, `RecordState.enabled(action)`; their presenters guard Save and Finish.
- `:app-common-ui` `ActivityTitleField` and the description field's counter; iOS `ActivityTitleField` and `ActivityDescriptionSection`.
- AppView `ActivityIndex.upsertActivity`. No lexicon JSON, migration or table change.
