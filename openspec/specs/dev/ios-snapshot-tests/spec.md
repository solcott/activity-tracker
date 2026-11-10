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
