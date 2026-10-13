# Tasks

Review chose option (c) in design.md, so no SubCircuit gets built. These tasks confirm the code already matches (c) and close the change.

## 1. Confirm the code matches option (c)

- [x] 1.1 Confirm `ActivityTypePicker` (`composeApp/.../record/ActivityTypePicker.kt`) is still a plain composable taking `enabled` from its host, and that no `ActivityTypePickerScreen` or `LocalPickerEnabled` exists. Verify with `grep -rn -e ActivityTypePickerScreen -e LocalPickerEnabled --include='*.kt' app composeApp`, which should return nothing.
- [x] 1.2 Confirm `RecordPresenter`, `ManualActivityPresenter`, `ImportActivityPresenter` and `FeedFilterPresenter` all load their quick row through `quickActivityTypes` (`app/presenters/.../record/ActivityTypes.kt`), not their own copy. Verify by grepping for `quickActivityTypes(` and finding all four call sites.
- [x] 1.3 Confirm the quick-row behaviour still has test coverage: run `./gradlew :app-presenters:jvmTest --tests "*.ManualActivityPresenterTest"`, which covers the recent types followed by the common ones.

## Workflow follow-up

- Archive the change with `/opsx:archive activity-type-picker-subcircuit`. It has no delta specs (`skip_specs: true`), so main specs don't change. The archived proposal and design record why the SubCircuit was declined.
