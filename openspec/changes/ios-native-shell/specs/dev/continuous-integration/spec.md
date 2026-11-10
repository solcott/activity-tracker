# Spec Delta

## MODIFIED Requirements

### Requirement: The iOS app builds in CI
The workflow SHALL build the iOS app for the simulator on a macOS runner, including its Swift export build and that build's stub check, whenever the Linux checks run, and SHALL fail when that build fails. It SHALL also run the iOS app's Swift unit tests on a simulator and fail when one fails. It SHALL need no signing team and no repository secret. Kotlin's iOS tests are still not run in CI.

#### Scenario: A contract that no longer exports
- **WHEN** a pushed commit makes the Swift export build fail or adds a stub the stub check rejects
- **THEN** the workflow fails on its iOS job

#### Scenario: Swift that no longer compiles
- **WHEN** a pushed commit breaks a Swift file of `iosApp`
- **THEN** the workflow fails on its iOS job

#### Scenario: A failing Swift test
- **WHEN** a pushed commit makes one of the iOS app's Swift unit tests fail
- **THEN** the workflow fails on its iOS job and its log names the test

#### Scenario: Only docs changed
- **WHEN** a pushed commit changes only Markdown, `openspec/` or agent configuration
- **THEN** the iOS job is skipped, as the Linux checks are
