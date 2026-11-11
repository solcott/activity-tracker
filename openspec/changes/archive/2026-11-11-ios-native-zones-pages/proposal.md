# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time. The roadmap holds Training zones and Privacy zones back for native charts and a map, but neither page draws either: Training zones, the default zones and a sport's zones are lists and number fields, and Privacy zones shows each zone as a label and a radius menu, with no centre. `ios-zones-contracts` (archived) already made their contracts readable from Swift, and `ios-native-gear-editors` (archived) the form rows they need, so they can go native now. Only threshold history draws a chart, and it stays the shared UI's.

## What Changes

- **Training zones is native on iOS.** An inset grouped list: who can see the zones (a menu, with the Followers note, saved as chosen, only once some zone is saved), Threshold history (opening the shared page), then the default zones and each sport with its own, each with its heading, what it applies to, an Edit button, its thresholds (Not set, or marked as the default's for a sport), and each kind of zone it offers with its model and zones; and Add a sport, a menu of the sports that have none.
- **The default zones and a sport's zones are native on iOS.** Grouped forms: the thresholds as number rows (heart rates and power digits only, the threshold pace as `m:ss`), a blank sport threshold showing the default it falls back to, Update from imported activities for each default threshold, each kind's model as a menu with what it is based on and its zones or what is missing to work them out; Save in the bar, a failed save at the top; a saved sport's Remove at the end. A field with a value out of range shows in the error colour and Save waits for it.
- **Privacy zones is native on iOS.** A grouped form: what the zones do, each zone by name with its radius as a menu and Remove, the note on adding from an activity, a zone added at the current location (an optional label, Add, finding the location) on a phone, Hide start and end as a menu, and the note on other apps' copies. While the session must sign in again or a save fails, it says so at the top, and nothing changes while saving.
- **Opened as the shared pages are**: alone, or beside Settings' list on an iPad, where the default zones and a sport's zones are the column's second page, with a back button to Training zones.
- **A digits-only field keeps the digits of an edit.** Today a refused edit is refused whole, so a digit typed right after a letter is lost with it (the roadmap's open item, seen on the gear weight). A digits-only field now takes the digits and drops the rest; a field's maximum length still refuses the whole edit.
- **Kotlin chooses the text and conditions, both UIs ask.** A zone's name (shared with the activity charts' time in zone), range with its unit or paces, model, basis and what it needs; a pace unit; each set of zones' heading, help and threshold lines; the sport page's title, help, pace label, default hint and Remove; the privacy distances (Off, or a distance in the athlete's units) and the add button's words; the field limits; and getters for what each page shows, enables and has chosen. The Compose pages ask the same. No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of the four pages in every shared sample, plus variants beside `all` (sports with their own zones, pace zones, saving, a failed save, a saved sport, a phone adding zones, no zones, a zone without a label, signing in again), so the shared screenshot set and the content-loading conformance keys are unchanged. Registry entries and their test.
- **The roadmap's note that these pages wait for charts and a map is corrected.**

Out of scope: threshold history (it draws a chart), a map of a zone's centre, adding a zone from an activity (the activity page's), and moving the location prompt off the location tracker.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`: Training zones, the default zones, a sport's zones and Privacy zones are drawn natively on iOS, alone and beside Settings' list.
- `dev/shared-display-text`: the zones pages' names, ranges, headings, threshold lines, labels, limits and conditions, and the privacy zones' distances, are chosen in shared code.
- `ui/ios-design-system`: a digits-only field keeps the digits of an edit, and a form row can show a hint while it is blank.

## Impact

- **`:app-common-api`:** a zone name by `ZoneName`, which the activity charts' time in zone asks too; tests.
- **`:feature-settings-api`:** `ZoneTexts` grows (name, range, pace unit, model, basis, missing input), `TrainingZonesTexts`, `SportZonesTexts`, `PrivacyZonesTexts`, `ZoneLimits`, getters on the four states; tests. **`:feature-settings-ui`:** the four Compose pages ask them. **`:app-common-ui`:** `zoneName` resolves the shared choice.
- **`:app-fixtures`:** `variants` for the four samples.
- **`iosApp`:** `TrainingZonesView`, `ZonesView`, `SportZonesView`, `PrivacyZonesView`, the zone rows they share, `AppTextField`'s hint, `TextFieldRules`' digits, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, "Phase 4 probes (zones pages)" and findings, open items (charts-and-map note, refused edits, the zone row's text); `.agents/rules/compose-screens.md`.
- **Risk carried:** iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0; other sessions share the signed-in simulators, so the walk waits until none is walking; a walk changes alice's zones and must put each value back.
