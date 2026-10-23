# Proposal

## Why

Testing a screen's UI costs more in setup than in the test itself. There are two Compose UI tests, no previews and no test tags. Every test builds its state by hand: `SegmentCreditUiTest` spends about 25 lines on a single `SegmentView`. The UIs also reach for things a test can't pin:
- the system clock and time zone
- the device's 12/24-hour setting
- MapLibre with remote styles
- network images
- `LocalSubCircuit`

The good part is that every Circuit UI is already a stateless `XUi(state, modifier)`. Adding seams, a harness and fixtures makes UI tests, previews and screenshot tests (`compose-screenshot-tests`) cheap.

## What Changes

- **Deterministic UI inputs.** A screen's output depends only on its state and on values a test or preview can provide:
  - light or dark theme
  - platform look
  - unit system
  - the current time and time zone
  - the 12/24-hour clock
  - maps and images, which draw placeholders when rendered for inspection
- **A preview and test harness.** One composable renders any UI in a chosen platform look, theme, units, clock and window width, with every ambient value a screen needs, and needs no signed-in session, network or local stack.
- **State fixtures**, in a new module shared by UI tests, presenter tests and previews:
  - builders for the lexicon views screens show, with sensible defaults
  - `ContentState`/`PagedState` builders for each ui/content-loading state
  - named sample states for each screen
- **Stable test anchors** on the shared loading, error, retry, refresh and paging pieces and on the top bar's back button.
- **A content-loading conformance test.** It runs the screens through first load, empty, failed, loaded, refreshing and refresh failed using only those anchors, and checks them against `ui/content-loading`.
- **Guard rails:**
  - a detekt rule against reading the system clock or time zone in UI code
  - updated `compose-screens.md` rules and `new-screen` skill, so a new screen ships sample states and a UI test

Out of scope:
- screenshot tests (`compose-screenshot-tests`)
- iOS-look rendering
- device-based or end-to-end UI tests

## Capabilities

### New Capabilities
- `dev/ui-test-harness`: how a screen's UI is rendered in isolation, deterministically, from sample state, for tests and previews; and the anchors and conformance checks UI tests rely on.

### Modified Capabilities
None. The conformance test checks `ui/content-loading` as written.

## Impact

- **composeApp:**
  - `AppTheme` gains a `darkTheme` parameter.
  - New CompositionLocals for the clock and time zone and for the hour-clock style.
  - `ProfileUi`, `EditProfileUi`, `EditGearUi`, `FeedFilterUi`, `DataSourcesUi` and `StartTimeText` read those locals.
  - `ActivityMap` draws a placeholder under inspection.
  - A `TestTags` object goes on `ContentStateLayout`, `RefreshStatus`, `pagedFooter` and `AppTopBar`.
  - The `AppPreview` harness.
  - The existing `SegmentCreditUiTest` and `ChartReadoutUiTest` move to the harness.
- **New module `:app-fixtures`** (`app/fixtures`, KMP, all client targets). It depends on `:app-screens` and `:core-lexicon` only. `composeApp` and `:app-presenters` use it in their tests, and `:androidApp` uses it in screenshot tests.
- **build-logic:** the Compose convention adds `compose-ui-test` and `:app-fixtures` to UI modules' `jvmTest`.
- **detekt-rules:** a new `NoSystemClockInUi` rule.
- **Docs:** `.agents/rules/compose-screens.md`, the `new-screen` skill and `AGENTS.md` (a new module and a command).
- **Dependencies:** none new at runtime. Coil's preview handler is already in `coil-compose`.
