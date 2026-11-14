# dev/ios-snapshot-tests Specification

## Purpose
Lets a native iOS view be seen and compared without a session: drawn from the same sample states the shared UI's tests use, in previews and against reference images, and says where those comparisons hold.

## Requirements

### Requirement: Native views render from the shared sample states
A native iOS view SHALL be drawable, in an Xcode preview and in a test, from the same sample states the shared UI's tests and reference screenshots use, with no session and no network.

#### Scenario: A preview of each sample
- **WHEN** a developer opens the sign-in view's previews
- **THEN** each of sign-in's sample states is shown: empty, starting, failed, expired, expired while recording, signing in again, and after leaving

### Requirement: Samples stay out of release builds
A release build of the iOS app SHALL NOT contain the sample states or code that reads them.

#### Scenario: A release build
- **WHEN** the iOS app is built for release
- **THEN** the build succeeds and its Kotlin export holds no sample module

### Requirement: Native views have reference snapshots
Every native screen and every piece of the native design system SHALL have reference snapshots, in the light and dark appearances, and the iOS app's tests SHALL fail when a view no longer matches its reference.

#### Scenario: A visual change
- **WHEN** the sign-in view's layout changes and its references aren't rerecorded
- **THEN** the iOS tests fail on the simulator the references were recorded for, and name the view

#### Scenario: No change
- **WHEN** nothing a snapshot covers has changed
- **THEN** a second run of the tests on the same simulator passes

### Requirement: Snapshots compare only where their references are true
The snapshot comparisons SHALL run only on the simulator operating system version the references were recorded on, and SHALL be reported as skipped, not passed or failed, anywhere else.

#### Scenario: Another OS version
- **WHEN** the iOS tests run on a simulator of a different OS version than the references record
- **THEN** the snapshot comparisons are skipped with a message naming the version they need, and the other tests run

### Requirement: References change only on request
A reference snapshot SHALL change only when a developer rerecords it, and a test run SHALL never rewrite one on its own.

#### Scenario: A failing comparison
- **WHEN** a snapshot comparison fails
- **THEN** the reference image on disk is unchanged

### Requirement: A full run compares snapshots as reliably as one suite
A snapshot comparison SHALL give the same result whether its suite runs alone or within a full run of the app's tests on the same simulator, so that a full run with nothing changed passes every time.

#### Scenario: Two full runs
- **WHEN** the app's whole test suite runs twice in a row on the recorded simulator with nothing changed
- **THEN** both runs pass every snapshot comparison

### Requirement: A suite run alone compares the same every run
A suite of snapshot tests run on its own on the recorded simulator, with nothing changed, SHALL pass every run, including its wide (iPad)
samples. A sample that drew differently from run to run SHALL be made to draw the same, not passed by a looser tolerance or dropped.

#### Scenario: Import alone, five times
- **WHEN** the import screen's snapshot suite runs alone five times in a row on the recorded simulator
- **THEN** every run passes, its iPad page included

### Requirement: Snapshots draw with a fixed safe area
Every snapshot SHALL be drawn with a safe area fixed before it draws, the same wherever the snapshot library places the view, so that no
draw depends on when the system passes a changed safe area on. A navigation stack SHALL keep the key window's safe area, so that its bar
sits under the status bar and the content below it is laid out for that, as on a device; a component's own content SHALL take none, so
that it is drawn whole in its frame.

#### Scenario: A list under a bar
- **WHEN** a screen with a navigation bar and a list is snapshotted, alone or in a full run
- **THEN** its bar is drawn under the status bar and its list right below the bar, the same every run

#### Scenario: A component in a short frame
- **WHEN** a component with no navigation stack is snapshotted in a frame little taller than itself
- **THEN** it is drawn whole, placed in the frame as it would be with no status bar above it
