# Design

## Context

What exists, from the code (see proposal.md for why):

- **The contracts** (`feature/settings/api`). `TrainingZonesState`: `content: ContentState<TrainingZones?>` (`defaults` and `sports` as `ZoneSummary`s, `addableSports`, `hasZones`, `hasHistory`, `approvesFollowers`), `visibility`, `savingVisibility`, `error`; events `EditDefaults`, `EditSport(sport)`, `History`, `VisibilityChanged`, `Retry`, `Back`. `ZoneSummary`: `sport?`, `kinds` (`ZoneKindOption`s, with `offers(kind)`), `training` (`EffectiveTraining`), `override` (`SportTraining?`), `paceUnit`, and a model and zones (`ImmutableList<Zone>?`) per kind. `ZonesState`: four `TextFieldState`s (threshold, max and resting heart rate, FTP), the heart rate and power models with their options and zones, `autoUpdate: ImmutableSet<String>` of `Threshold` tokens, `content: ContentState<Unit>`, `saving`, `error`, getters `loaded`, `valid`; events `HeartRateModelChanged(token)`, `PowerModelChanged(token)`, `AutoUpdateChanged(threshold, enabled)`, `Save`, `Retry`, `Back`. `SportZonesState`: `sport`, `kinds`, four fields (threshold and max heart rate, FTP, threshold pace), `paceUnit`, `defaults`, a model, options and zones per kind, `saved`, `content`, `saving`, `error`; events adding `SpeedModelChanged` and `Remove`. `PrivacyZonesState`: `content`, `zones: ImmutableList<PrivacyZone>?`, `hideEndsMeters`, `choices` (200 to 1,600 m by 200), `canEdit`, `saving`, `canUseLocation`, `hasLocationPermission`, `locating`, `label`, `needsSignIn`, `error`; events `RadiusChanged(index, meters)`, `Remove(index)`, `HideEndsChanged(meters)`, `AddAtCurrentLocation`, `PermissionResult(granted)`, `Retry`, `Back`. Zones and sport zones pop on a successful save or remove. All four screens are `SettingsPage`s, so the host shows them beside Settings' list when wide, the zones forms as the column's second page.
- **Readable from Swift** (`ios-zones-contracts`): the kinds as `ZoneKindOption`s and `offers(kind)`, `Zone` and `PrivacyZone` as element types (named by `ZoneTexts.label` and `privacyZoneName`), `ZonesContractsTests`.
- **Text built in composables** (`feature/settings/ui`, `:app-common-ui`): `zoneName(ZoneName)` (shared with `ActivityChartsUi`'s time in zone), `numberRange` ("143–150" plus " bpm"), `paceRange` (slower than, a range, or faster, each pace `PaceText.format`ted with the unit's label), `paceUnitLabel`, `zoneModelLabel` (`zone_model_<token>`, else the token), `basisLabel`, `missingInput`; Training zones' headings, help and `Thresholds` rows (which thresholds per kind and per sport, "Not set", "(default)" and the muted colour); the sport form's title, help, pace label, "Default: N" placeholder and Remove label; Privacy zones' distances (`Format.distance`, 0 as Off), the hidden distances `listOf(0) + choices`, and the add button's two words. Validity per field is `ThresholdInput` (`:core-data`), called by field in the UIs.
- **Native pieces to reuse** (`ios-native-gear-editors` is on the branch): `AppTextField(layout: .row)` with `TextFieldModel` and `TextFieldRules`, `AppPickerRow`, `AppListRow`, `pageTitle(_:back:actions:)`, `ContentStateView` (with a value, and the form without one), `RefreshStatusView`, `ErrorMessage(layout: .row)`, `VisibilityTexts.followersNote`, `LabelText.visibility`/`sport`, `@Environment(\.stats)` for the athlete's units.
- **The refused-edit open item**: `TextFieldRules` refuses an edit whole, and on the gear weight a digit typed in the same burst after a refused letter was lost with it.

## Goals / Non-Goals

**Goals:**

- The four pages native, saying, offering and limiting what the shared pages do, laid out the way an iOS settings page and edit form are.
- Every rule they follow written once in Kotlin, the Compose pages asking the same, with no visible change there.
- The refused-edit question settled for digits-only fields.

**Non-Goals:**

- Threshold history (chart), a map of a privacy zone's centre, adding a zone from an activity.
- A contract change beyond getters and text functions.
- Applying the iOS field limits to the Compose fields: they keep showing an out-of-range value in the error colour, as today.

## Decisions

### 1. Probe first

Throwaway Swift tests on a simulator of this change's own (iOS 26.0), results under "Phase 4 probes (zones pages)" in the roadmap before the views are built on them:

- `Zone.name` reads as Swift's enum, and `ZoneModel` (named by the states' properties) has a Swift type an array of options can be read as.
- A `List` of a contract class returned by a text function (decision 7's threshold lines) reads as a typed array.
- A contract enum passed as a parameter, with its properties (`token`, `maxLength`) read in Swift.
- Burst typing in a digits-only field, through `TextFieldModel` driven as the field drives it (an edit arriving before the refusal's redraw), before and after decision 6.

### 2. Training zones: an inset grouped list

`TrainingZonesView` is a `List` inside `ContentStateView` over `TrainingZones?` (a loaded `nil` draws nothing, as shared), with `RefreshStatusView` and a failed visibility save in a row at the top, titled `pageTitle(Text(.zonesTitle), back:)`.

- **Visibility** (when `hasZones`): a section headed "Who can see your zones", one `AppPickerRow` over Kotlin's visibility options, disabled while saving, footed by the help and the Followers note.
- **History** (when `hasHistory`): an `AppListRow` sending `History`; the shared page opens.
- **Each summary** (defaults, then each sport): a section whose header is the summary's heading with an Edit button at its trailing end (`EditDefaults` or `EditSport`), and whose footer is what it applies to. Its rows: the threshold lines (label leading, value trailing, secondary when unset or the default's), then for each kind it shows, a heading row (the kind's heading leading, the model trailing in secondary, the basis under the heading), then its zone rows, or what the model needs in secondary.
- **Add a sport** (while `addableSports` isn't empty): a row holding a `Menu` of the sports, sending `EditSport`, footed by the help.

The kinds follow the shared order: pace zones when offered, heart rate always, power when offered. Rejected: a section per kind per summary (the "Heart rate zones" header would repeat once per sport with nothing saying whose), and the summary's heading as a navigation row (the shared page's Edit is a word; a row would say only the heading).

### 3. The zones forms: grouped forms with Save in the bar

`ZonesView` and `SportZonesView` are `Form`s inside `ContentStateView`'s form without a value, as the gear form: Save in the bar (`showsSave` once loaded, enabled by `canSave`), a failed save in a row at the top, `pageTitle(..., back:)` so beside the list the column shows a back button to Training zones.

- **Thresholds**: each an `AppTextField(layout: .row)` with its `TextFieldModel` and rules from Kotlin (decision 7), the number pad for heart rates and power and the numbers-and-punctuation keyboard for the pace, in the error colour when Kotlin says the text isn't valid. On the default form each threshold is its own section, its field and its Update from imported activities toggle together, the first headed "Thresholds" and the last footed by the auto-update help, so each toggle sits with the value it updates. On a sport's form the thresholds are one section headed "Thresholds", footed by the sport's help, each blank field showing its default as a hint (decision 5).
- **Each kind**: a section headed by the kind's heading, an `AppPickerRow` "Model" over the options (by token, each named by Kotlin) with the basis under its label, then the zone rows or what the model needs.
- **Remove** (a saved sport): a destructive button in a last section, disabled while saving, sending `Remove`.

Save's state reads the fields' texts through Kotlin (`valid`), which SwiftUI can't observe; the view reads each model's text first, so a keystroke redraws Save, and asks validity with the text as the view holds it (`isValid(field, text)`), as Leave asks `canLeave(typed)`.

### 4. Privacy zones: a grouped form

`PrivacyZonesView`, a `Form` in `ContentStateView`'s form without a value, `pageTitle(Text(.settingsPrivacyZones), back:)`:

- At the top: the sign-in-again notice when `needsSignIn` (`ErrorMessage` of `SignInAgain`, as shared) and a failed change.
- **The zones**, headed by the help: each zone an `AppPickerRow` whose label is its name with "Radius" under it, its radius trailing, the menu of Kotlin's distances; Remove as the row's swipe action (destructive, "Remove zone"), which VoiceOver offers as an action; or "No privacy zones yet." Footed by the note on hiding a start or end from an activity. The radius menus and the swipe are off unless Kotlin's `enabled`.
- **Here** (`canUseLocation`, a phone): the label as an `AppTextField(layout: .row)` and Add a zone at my location, which reads "Finding your location…" with a small progress mark while locating. With no permission yet it sends `PermissionResult(granted: true)`, as the shared iOS UI's request does: `IosLocationTracker` asks the system when updates start.
- **Hide start and end**: an `AppPickerRow` over Kotlin's hidden distances (Off first), footed by its help and then the note that other apps' copies don't change.

A visible Remove button beside each menu was rejected: two trailing controls on one row break the one-control-per-row rule, and a zone per section with its own Remove row doubles the page's length for one zone's action.

### 5. A hint in a blank row

`AppTextField` gains `hint: Text?`, shown as the field's prompt (the placeholder's colour, at the trailing end of a row), for the sport form's "Default: 190". VoiceOver reads a prompt as the field's value while it is blank. A gallery entry and its references.

### 6. Digits-only keeps the digits

`TextFieldRules.digitsOnly` takes an edit's digits and drops the rest; `maxLength` then still refuses the whole edit (a pasted "1700" in a 3-digit field leaves it as it was, since cutting it would make another number). `TextFieldModel.edit` writes what the rules take, and counts a refusal whenever what it took differs from what was typed, so the field redraws over the dropped characters. A burst "9a5" then reads "95": "9a" is taken as "9" (nothing changes but the redraw is asked for), and "9a5", arriving before that redraw, is taken as "95".

- *Why*: on an iPad's number pad, a hardware keyboard or a paste, letters do reach a digits field, and losing a digit typed after one changes the number silently. Taking the digits is what a digits-only field means; a maximum length is different, since dropping the end of a too-long number would also change it silently.
- *Cost*: the old rule that a refusal never rewrites what was typed now holds for length only. The open item is closed with this.
- *Alternatives*: keeping whole refusals and asking the athlete to slow down; refusing and keeping the field's own text until the next keystroke (a SwiftUI `TextField` can't be told which characters it shows without a redraw).

### 7. Kotlin rules (no visible change)

- **`:app-common-api`**, `ZoneNameText.of(name: ZoneName)` (`zone_name_<name>`), in `app.text`. `zoneName` in `:app-common-ui` resolves it, and the activity charts and the zones pages ask it.
- **`:feature-settings-api`**:
  - `ZoneTexts` (up to detekt's eleven): `label`, `privacyZoneName` (as now), `name(zone)`, `range(zone, kind, paceUnit)` (bpm and W through the summary value strings, paces through `zones_pace_*`, bounds written as whole numbers as today), `paceUnit(unit)`, `heading(kind)`, `model(model)` (`zone_model_<token>`, the token as fallback), `basis(basis)`, `missing(basis)`.
  - `TrainingZonesTexts`: `heading(summary)`, `help(summary)`, `thresholds(summary): List<ZoneThresholdLine>` (`label`, `value`, `muted`), the rule for which thresholds a summary lists and how.
  - `SportZonesTexts`: `title(sport)`, `help(sport)`, `paceLabel(unit)`, `defaultHint(value)`, `remove(sport)`.
  - `PrivacyZonesTexts`: `distance(meters, units)` (0 as Off), `addHere(locating)`.
  - `ZoneThreshold`, an enum of the five fields with each one's `Threshold` token, whether it is digits only, its maximum length (from `TrainingDetails`' maxima: 3 digits of heart rate, 4 of power; the longest pace `PaceText` writes at the slowest speed in any unit), and `isValid(text, paceUnit)` over `ThresholdInput`. An enum is a parameter and a property Swift reads as its own type, so Swift never needs a `Threshold` or `ThresholdInput` type.
  - Getters: `TrainingZonesState.visibilityOptions`, `canChangeVisibility`; `ZonesState.showsSave`, `canSave`, `autoUpdates(threshold)`, `isValid(field, text)`; `SportZonesState` the same plus `showsRemove`, `canRemove`; `PrivacyZonesState.enabled`, `canAddHere`, `hideEndsChoices`. Swift never reads `autoUpdate`, a set, to answer membership.
- The four Compose pages and `ActivityChartsUi` ask all of it; their screenshots must compare equal. Tests in `commonTest` of each module over each branch and every sample; each new name set joins `SharedTextNamesTest`.

### 8. Samples

Variants beside `all`, so the shared screenshot set and `ContentLoadingConformanceTest` keep their keys: `TrainingZonesSamples.variants` (`sports`: a Swim override with its own threshold heart rate and pace zones per 100 m; `runDefaults`: Run with nothing of its own, no sport left to add; `noZones`: nothing saved, no history; `saveFailed`). One sport per variant keeps each whole page under the 8,192 pixels an image can be at three pixels a point (a 3,500-point page recorded blank), `ZonesSamples.variants` (`saving`, `saveFailed`, `autoUpdate`, `missing`: no FTP), `SportZonesSamples.variants` (`saved`, `swim`, `saving`, `saveFailed`), `PrivacyZonesSamples.variants` (`phone`: two zones, one unnamed, with the label field; `locating`; `empty`; `signInAgain`; `saveFailed`). The privacy samples' `choices` become the presenter's kind (200 m up): they held 0, so the radius menu offered Off and Hide start and end offered it twice.

### 9. Registry, aliases, previews, snapshots, tests

- The four screens, their states and events, the new text objects and `ZoneThreshold`, `ZoneThresholdLine`, `ZoneModel`, `ZoneName`, `PaceUnit`, `EffectiveTraining`: one block in `KotlinTypes.swift` replacing the zones contracts' block; the four registrations in one commented block at the end of `ScreenRegistry.app`; the samples' variants in `Samples.swift` beside the zones samples already there. Append-only beside other sessions' blocks.
- `#Preview`s with `SamplePages` over each sample and variant, one beside Settings' list; `ZonesPagesSnapshotTests` over every sample and variant, light and dark, tall enough for the whole page, plus one of Run's zones beside the list at an iPad's size; the hint in `DesignSnapshotTests`. `ZonesPagesTextTests` hold the words, limits and conditions; `TextFieldModelTests` the digits rule; `ScreenRegistryTests` the four names.

## Risks / Trade-offs

- [Taking an edit's digits rewrites what was typed] → only for digits-only fields; the maximum length still refuses whole; the gear weight's behaviour improves the same way; tested in `TextFieldModelTests` and seen in the walk.
- [Remove as a swipe is less visible than the shared page's button] → it is the system's way to remove a row and VoiceOver offers it as an action; the walk checks it.
- [Save's enabling reads Kotlin state SwiftUI can't observe] → the view reads each model's text, which it observes; a text test drives a model and asks `canSave`.
- [A location prompt on a device] → the walk adds a zone only if location is already allowed for the app; otherwise it is recorded under "Not verified", since allowing it is the user's choice.
- [The walk changes alice's zones and visibility] → each value changed is noted first and put back; a zone added is removed.
- [Other sessions share the signed-in simulators] → tests on simulators made for this change; the walk only when none is walking, or with the user's word.
