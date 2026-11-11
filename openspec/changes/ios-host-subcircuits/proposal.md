# Proposal

## Why

A SubCircuit sub-screen's presenter runs only inside Compose's `SubCircuitContent`, so the presenter host runs screens but not the
sub-screens their states embed. A native SwiftUI view of such a screen has no state to draw for the embedded piece. The feed's filter
bar (`FeedState.filterScreen`) is the first blocker of a native Feed, and the shared sheets that use `SubCircuitContent` (report, the
place-name proposal) block every page that shows them. The Feed is the next phase 4 change, so the host learns sub-screens first.

## What Changes

- The presenter host runs sub-screens. A UI asks a shown record for the sub-screen its state embeds (`HostRecord.sub(screen,
  outerEventSink)`), with where the sub-screen's outer events go, and gets a `HostSubRecord` that publishes the sub-screen's state. The
  host finds the sub-screen's presenter in the session graph's `SubPresenterFactory` set, runs it while its parent record runs, keeps
  what it retained and saved for as long as the parent is in its stack (or until the UI closes it), and drops it when the parent leaves.
  A sub-screen that isn't equal to the one before it is a new sub-record, as `SubCircuitContent` remounts on a changed screen.
- Swift reaches the sub-screen's state through that lookup, which takes the screen as an object (Swift has no `SubScreen` conformance,
  since the generic interface isn't exported) and outer events as `SubCircuitOuterEvent`, which is exported. The sub-screen's own events
  go to its state's `eventSink`, as a screen's do.
- The Compose UIs keep `SubCircuitContent` unchanged; a fallback screen still runs its sub-screens inside Compose.
- The feed filter bar's labels move to Kotlin (`FeedFilterTexts`: the types chip and the date chip), which the Compose bar asks.
- A native `FeedFilterBar` view of `FeedFilterState` (the chips, the date menu, Clear, and plain sheets for the type picker and a custom
  range), with previews and snapshots over `FeedFilterSamples`, drawn from a sub-record. It is not placed in any page: the Feed stays the
  shared UI's.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/presenter-host`: the host runs a shown screen's sub-screens, with their state kept per parent record and dropped when it leaves.
- `dev/ios-swift-interop`: Swift follows a sub-screen's state and receives its outer events through the host.
- `dev/shared-display-text`: the feed filter bar's chip labels are chosen in Kotlin.

## Impact

- `:app-host` (`HostRecord`, `HostSubRecord`, `RunRecords`, `SessionHost`, `TabStack`, `AppHost`) and its tests; the session graph's
  `SubPresenterFactory` set reaches `SessionHost`.
- `:feature-feedfilter-api` (`FeedFilterTexts`) and `:feature-feedfilter-ui` (asks it).
- `iosApp`: `Kotlin/KotlinTypes.swift` (additions), `Kotlin/FollowingSub.swift` or similar, `Screens/FeedFilterBar.swift`,
  `Previews/Samples.swift` (additions), a snapshot suite.
- The roadmap `openspec/explorations/ios-native-ui.md` and `.agents/rules/compose-screens.md`.
