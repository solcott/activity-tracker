# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) leaves Training zones, threshold history and Privacy zones for last, since they need native charts and a map. Their contracts also hold three things Swift export can't read, which the roadmap's open items list: two sets of a Kotlin enum, two element types Swift has no name for, and a getter that hands Swift an immutable list as a `List`. Each one stops the app or gives Swift an opaque object the moment a native view reads it. Clearing them is Kotlin work that touches no screen anyone sees, doesn't overlap the native follow lists, and leaves those pages only their drawing when charts and maps arrive.

## What Changes

- **The zone kinds a set of zones offers reach Swift as objects.** `ZoneSummary.kinds` and `SportZonesState.kinds`, today sets of `ZoneKind`, become an immutable list of a contract class holding one kind each, in the order the shared page shows them (heart rate, power, speed), as Segments & groups' grouping choices did (`GroupOption`). A getter asks whether a kind is offered, so the shared UIs keep asking one question rather than searching the list.
- **A computed zone and a privacy zone have Swift types.** `Zone` (`:core-data`) and `PrivacyZone` (`:core-lexicon`) appear in the contracts only as an immutable list's element, so Swift gets an opaque object for each. Two text functions a native page will need anyway name them: a zone's label ("Zone 2") and a privacy zone's name (its label, or "Zone 1" by its place). The shared pages ask the same functions, with the same words.
- **Threshold history's shown rows are a copy.** `ThresholdHistorySection.shownRows` returns its immutable `rows` itself when every row is shown. The probe found that only an empty persistent list stops the app there, and a threshold always has a row, but it returns a copy either way, so the getter holds whatever the list is.
- **Probes first, kept as Swift tests.** Each blocker is read from Swift over the screens' `:app-fixtures` samples before the fix, and the same reads, made to pass, stay as Swift tests.

No visible change on any platform: the zones and privacy zones pages stay the shared UI everywhere, including iOS, and say and show exactly what they do today.

Out of scope: drawing any of these pages natively (they need native charts and a map), the rest of a zone row's text (its name and range, which move when the native page needs them), and the zone models' own lists (`ZoneModel.zones`, never read by a page).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/ios-swift-interop`: the zone kinds reach Swift as objects; a zone and a privacy zone reach Swift as their own types; a shown subset of a list reaches Swift as a typed array.
- `dev/shared-display-text`: a zone's label and a privacy zone's name are chosen in shared code.

## Impact

- **`:feature-settings-api`:** `ZoneSummary.kinds` and `SportZonesState.kinds` change type (a list of `ZoneKindOption`, with `offers(kind)`); `ThresholdHistorySection.shownRows`; `ZoneTexts`; tests.
- **`:feature-settings-presenter`:** the two presenters build the new list; their tests compare it.
- **`:feature-settings-ui`:** `TrainingZonesUi`, `SportZonesUi`, `ZonesUi` and `PrivacyZonesUi` ask the new getter and functions.
- **`:app-fixtures`:** the zones samples hold the new list, with the same kinds; `ThresholdHistorySamples.variants` holds a history with every row shown, apart from `all`, for the Swift test (Swift can't build one).
- **`iosApp`:** Swift tests over the samples (`ZonesContractsTests`), and type aliases for what they read.
- **Docs:** the roadmap's probes and findings for this change and its open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** none on a device: nothing native draws these contracts yet, so only the tests read them from Swift.
