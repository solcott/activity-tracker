# Spec Delta

## ADDED Requirements

### Requirement: The left-elsewhere choice is native on iOS
On iOS the screen shown after leaving on another device, with work still on this one, SHALL be drawn natively in place of the app: its
title, what this device still holds, the choice, a failure, and Rejoin and Discard and sign out, both off while either runs, with
progress while it is still reading what the device holds.

#### Scenario: Pending ride on an iPhone
- **WHEN** bob's iPhone detects the leave with 1 recorded ride waiting
- **THEN** the native screen says 1 change is pending and offers Rejoin and Discard and sign out

#### Scenario: Rejoining
- **WHEN** bob taps Rejoin on the native screen
- **THEN** both buttons are off and progress shows until the app comes back, or the failure shows with both buttons on again
