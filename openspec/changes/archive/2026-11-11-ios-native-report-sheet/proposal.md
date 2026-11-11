# Proposal

## Why

The native profile on iOS leaves Report out (`ios-native-profile`): the report sheet is the `ReportScreen` SubCircuit, which only
Compose's `SubCircuitContent` could run. Since `ios-host-subcircuits` the presenter host runs sub-screens, so a native page can show the
sheet natively, and the profile can offer Report as the shared page does. The place-name sheet (`ProposePlaceNameScreen`) is a sheet
sub-screen of the same kind, which the segment and dive site pages will need when they go native.

## What Changes

- A native report sheet (`ReportSheet`) drawn from the report sub-screen's state through the host (`SubScreenView`): the reasons, the
  note (required for Other, with its limit), what moderators may see, Cancel and Send, the failure, then the thanks with Block offered
  and Done. It is SwiftUI's own sheet, closed in the host when dismissed.
- A reusable way in for any native page that carries `Reporting`: a view modifier (`reporting(_:)`) showing the sheet while it is open
  and the usual Block confirmation once its offer is taken. A page opens the sheet with `ReportingEvent.Open`, which Kotlin builds.
  The native post card (another change) reuses it.
- The native profile offers Report in its menu on someone else's profile, kept on one the viewer blocks, as the shared page does.
- Native pages reach their own record from the environment (`\.hostRecord`), so a sheet deep in a page can ask the host for its
  sub-screen.
- The report sheet's words and conditions move to Kotlin (`ReportTexts`, `ReportState` getters, `ProfileState.reportEvent`), which the
  Compose sheet and profile ask too.
- A native place-name sheet (`ProposePlaceNameSheet`) and its modifier (`placeNames(_:)`, the sheet and the delete confirmation), with
  its words in Kotlin (`ProposePlaceNameTexts`). No native page places it yet: the segment and dive site pages are still the shared UI's.
- The activity card offers no Report in either UI (the shared card has no menu), so the native card gains none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `moderation/reports`: the native profile on iOS offers Report.
- `dev/presenter-host`: a sheet's sub-screen is closed when the sheet is dismissed, and starts afresh when reopened.
- `dev/ios-swift-interop`: a native page reaches its record to ask for a sheet's sub-screen.
- `dev/shared-display-text`: the report and place-name sheets' words and conditions are chosen in Kotlin.

## Impact

- `:feature-report-api` (`ReportTexts`, `ReportReasonChoice`, `ReportState` getters), `:feature-report-ui` and `:app-common-ui`
  (`ReportingHost`) ask them; `:feature-profile-api` (`ProfileState.reportEvent`) and `:feature-profile-ui`.
- `:app-common-api` (`ProposePlaceNameTexts`, `ProposePlaceNameState` getters) and `:app-common-ui` (`ProposePlaceNameUi`).
- `:app-fixtures` (report and name sheet variants outside the shared screenshots' `all`).
- `composeApp` JVM test running the real report presenter through the host.
- `iosApp`: `Shell/ScreenView.swift` (the record in the environment), `Screens/ReportSheet.swift`, `Screens/ProposePlaceNameSheet.swift`,
  `Screens/ProfilePage.swift` (Report in the menu), additions to `KotlinTypes.swift` and `Samples.swift`, snapshot and text tests.
- The roadmap `openspec/explorations/ios-native-ui.md` and `.agents/rules/compose-screens.md`.
