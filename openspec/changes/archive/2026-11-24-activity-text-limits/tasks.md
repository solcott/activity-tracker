# Tasks

## 1. Rules

- [x] 1.1 Add the limits, counting and cutting to `ActivityRecord`, and `ActivityTextLimits` (fits, counters) to `:app-common-api` with the `details_length` string; tests in `:core-lexicon` and `:app-common-api`, the name in `SharedTextNamesTest`

## 2. Records

- [x] 2.1 Trim and cut title and description in `ActivityDraft.toRecord` and `ActivityManager.edit`; `ActivityDraftTest`
- [x] 2.2 Cut them in the AppView's `upsertActivity`; `AppViewTest`

## 3. Forms

- [x] 3.1 `textsFit` on the four states, Save and Finish held back and guarded in the presenters; presenter tests for manual entry, Record and edit
- [x] 3.2 `ActivityTitleField` and the description's counter in Compose; the `tooLong` manual sample and a UI test; Android screenshot references
- [x] 3.3 `ActivityTitleField` and the description's counter on iOS, Save and Finish following the state; iOS snapshot references

## 4. Verify

- [x] 4.1 `./gradlew check`
