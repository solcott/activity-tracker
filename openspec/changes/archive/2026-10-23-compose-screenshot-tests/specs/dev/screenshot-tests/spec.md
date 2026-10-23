# Spec Delta

## Purpose

Catches visual regressions in the app's screens and shared components by comparing their rendering against reviewed reference screenshots on every check, rendered offline on the developer's machine.

## ADDED Requirements

### Requirement: Every screen has reference screenshots
Each screen SHALL have reference screenshots of:
- its loaded sample, at widths 400, 610 and 900dp, each at heights 400, 500 and 1000dp
- each of its other samples at 400x500dp

The samples are the ones the UI test harness provides.

#### Scenario: Feed sizes
- **WHEN** the screenshot tests run
- **THEN** the feed's loaded sample is compared at all nine sizes, and its loading, empty, failed, refreshing and refresh-failed samples at 400x500dp

#### Scenario: Expanded layout
- **WHEN** a screen that shows a side pane at expanded width is compared at 900dp
- **THEN** its reference shows the side pane

### Requirement: Theme and font scale variants
Each screen's loaded sample SHALL also have 400x500dp references in dark theme and at 1.5x font scale. Each shared component SHALL have references in light and dark theme, each at 1x and 1.5x font scale. The shared components are:
- the adaptive controls
- the first-load, error, refresh and paging pieces
- the charts

#### Scenario: Large text
- **WHEN** the activity page's loaded sample renders at 1.5x font scale
- **THEN** it is compared against its large-text reference

#### Scenario: Dark button
- **WHEN** the shared button renders in dark theme
- **THEN** it is compared against its dark reference

### Requirement: Screenshots render offline on the host
Screenshot tests SHALL render on the developer's machine without a device, emulator, network access, account or local stack. Rendering the same sources twice on the same machine SHALL produce the same images.

#### Scenario: No network
- **WHEN** the screenshot tests run with the network off
- **THEN** they pass, with maps and images drawn as placeholders

#### Scenario: Repeat run
- **WHEN** the screenshot tests run twice with no source change
- **THEN** both runs pass against the same references

### Requirement: Checks fail on a visual difference
`./gradlew check` SHALL compare every screenshot with its reference. It SHALL fail when an image differs beyond the tolerance or has no reference, and produce a report that shows the reference, the new image and their difference.

#### Scenario: Changed colour
- **WHEN** a theme colour changes and `./gradlew check` runs
- **THEN** it fails, and the report shows each affected screenshot with its difference

#### Scenario: New screen without a reference
- **WHEN** a screenshot test is added without recording its reference
- **THEN** `./gradlew check` fails, naming that screenshot

### Requirement: References change only on request
References SHALL be written only by a documented update command, never by `./gradlew check` or a test run. They SHALL be kept in the repository, so changes to them show up in review.

#### Scenario: Updating after an intended change
- **WHEN** a developer changes a screen on purpose and runs the documented update command
- **THEN** the changed references are rewritten in the repository and `./gradlew check` passes

#### Scenario: Check does not write
- **WHEN** `./gradlew check` fails on a difference
- **THEN** no reference file in the repository has changed

### Requirement: New screens ship screenshot previews
A new screen SHALL come with screenshot previews for its samples, and the screen scaffolding SHALL create them.

#### Scenario: Scaffolding a screen
- **WHEN** a developer scaffolds a screen with the project's new-screen workflow
- **THEN** the result includes a screenshot preview file for the screen's samples
