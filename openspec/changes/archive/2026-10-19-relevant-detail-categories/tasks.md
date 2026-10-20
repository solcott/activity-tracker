# Tasks

## 1. Categories a draft holds

- [x] 1.1 Add `DetailCategory.RUNNING_FORM_CHANNELS` (the Cadence channels minus `cadence`) in `core/lexicon` and cover it in `DetailCategoryTest`; verify with `./gradlew :core-lexicon:jvmTest --tests "*.DetailCategoryTest"`
- [x] 1.2 Add `ActivityDraft.heldCategories()` and `ActivityDraft.RECORDED_CATEGORIES` in `core/data`, per design.md. Add tests: an MTB import draft (no Dive or Physiology), a dive with no route (no Map), a manual draft with and without a distance, a recorded draft within `RECORDED_CATEGORIES`, and agreement with `DetailCategory.currentTiers` for a published draft. Verify with `./gradlew :core-data:jvmTest --tests "*.DetailPlacesTest"` or a new `DraftCategoriesTest`

## 2. Visibility state offers only held categories

- [x] 2.1 Add `runningForm: Boolean = true` to `VisibilityFields` and update its KDoc for the new offering rule; verify `:app-screens` compiles with `./gradlew :app-screens:compileKotlinJvm`
- [x] 2.2 Make `NewActivityVisibility` take the offered set and the running-form flag from its presenter. Keep a tier for every category in `choice`, and ignore `CategoryChanged` for a category that isn't offered. Pass `runningForm` through `fieldsOf`. Verify existing visibility tests still pass with `./gradlew :app-presenters:jvmTest --tests "*Visibility*"`
- [x] 2.3 Set `runningForm` in `EditVisibility.seed` from the streams parts' channels. Extend `EditActivityPresenterTest` with a ride with cadence (false) and a run with stance time (true); verify with `./gradlew :app-presenters:jvmTest --tests "*.EditActivityPresenterTest"`

## 3. Forms pass their categories

- [x] 3.1 Import (one file): offer the placed `importDraft`'s `heldCategories()` and running-form flag. Test an MTB FIT/GPX fixture offering no Dive or Physiology, and a dive with no GPS offering no Map, in `ImportActivityPresenterTest`; verify with `./gradlew :app-presenters:jvmTest --tests "*.ImportActivityPresenterTest"`
- [x] 3.2 Dive batch: offer the union over the selected dives' drafts. Test two dives, one with tank pressures and one with heart rate, offering both, in `ImportLogbookPresenterTest`; verify with `./gradlew :app-presenters:jvmTest --tests "*.ImportLogbookPresenterTest"`
- [x] 3.3 Manual entry: move draft construction out of the Save handler into a function used by both Save and the offer. Test Start time only with no distance, Pace and speed appearing once a distance is entered, a choice surviving clearing and re-entering the distance, and no Only-me details record for an unoffered Dive default, in `ManualActivityPresenterTest`; verify with `./gradlew :app-presenters:jvmTest --tests "*.ManualActivityPresenterTest"`
- [x] 3.4 Record: offer `RECORDED_CATEGORIES`. Test it in `RecordPresenterTest`; verify with `./gradlew :app-presenters:jvmTest --tests "*.RecordPresenterTest"`

## 4. Cadence label

- [x] 4.1 Add a `detail_category_cadence_only` string ("Cadence") and have `DetailVisibilityPicker` use it when `fields.runningForm` is false. Keep the combined label for Settings and `CategoryLock`. Verify with `./gradlew :composeApp:compileKotlinJvm`

## 5. Integration

- [x] 5.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`; both pass
- [ ] 5.2 On the desktop app (`:desktopApp:hotRun`), import an MTB ride file and add a manual entry. Confirm by screenshot that the import shows no Dive or Physiology pickers and labels Cadence "Cadence", and that the manual entry shows only Start time until a distance is typed

## Workflow follow-up

- Archive the change after review and merge the worktree branch into main.
