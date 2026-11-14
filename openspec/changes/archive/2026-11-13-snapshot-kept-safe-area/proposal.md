# Proposal

## Why

`import-leftovers` found why Import's wide iPad snapshot drew its page 52 points higher now and then: swift-snapshot-testing lays a
key-window snapshot out under the status bar and then moves it off screen, which takes the safe area away, and a stack's lists get their
new inset only a run-loop turn later, which a slow draw sometimes comes after. It fixed only that snapshot (`KeptSafeArea`,
`assertAppSnapshot(keepsSafeArea: true)`). Every other snapshot with a list or a bar under the status bar has the same latent exposure,
and its references hold the stale layout (`openspec/explorations/ios-native-ui.md`, open item "every other suite has the same exposure").

## What Changes

- **Every snapshot's safe area is fixed.** `assertAppSnapshot` always draws through `KeptSafeArea`, so a navigation stack keeps the
  window's safe area, and gives the hosting controller no safe-area regions, so a component's own content lays out in its whole frame.
  The `keepsSafeArea` parameter goes, with the one call that passed it.
- **The references are rerecorded** on the iOS 26.0 simulator wherever the kept safe area changes the drawing, and each changed one is
  looked at: the only difference allowed is the bar under the status bar and what's under it laid out for that, as on a device.
  A component with no stack keeps its reference.

No app code or shown text changes.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `dev/ios-snapshot-tests`: every snapshot is drawn with a fixed safe area, whatever its suite: the window's for a navigation stack, none
  for a component's own content.

## Impact

- `iosApp/iosAppTests/Snapshots.swift`, `ImportSnapshotTests.swift`, and the iOS 26.0 references under `iosApp/iosAppTests/__Snapshots__/`.
