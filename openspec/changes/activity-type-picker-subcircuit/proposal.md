# Proposal

> Follow-up to `collapsible-sport-list`; do that change first. This is planning only, and implementing it depends on accepting the trade-offs in design.md.

## Why

`ActivityTypePicker` is a plain composable. Record, Manual and Import each load quick types with `quickActivityTypes(recentTypes, ...)`, carry them in their own `State`, and pass them in. As a SubCircuit, like `FeedFilterScreen`, the picker would own its quick types. The three hosts would then only seed the type and receive changes.

## What Changes

- New `ActivityTypePickerScreen` SubCircuit (contract in `:app-screens`, presenter in `:app-presenters`, UI in `composeApp`) at `SessionScope`, seeded with the initial type and `outdoorOnly`, reporting `Changed(type)` as an outer event.
- Its presenter takes over `quickActivityTypes`/`RecentActivityTypes`. `quickTypes` leaves `RecordState`, `ManualActivityState` and `ImportActivityState`.
- Record, Manual and Import host it through `SubCircuitContent`.
- No user-visible behaviour change.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- None. This is a refactor, so `skip_specs: true`.

## Impact

- `app/screens`, `app/presenters`, `composeApp` record packages; the three host presenters and their tests; a new sub-presenter test with `circuitx-subcircuit-test`.
