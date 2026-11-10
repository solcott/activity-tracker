# dev/continuous-integration Specification

## Purpose

Runs the repository's checks automatically on GitHub for every push and pull request, so the public repository shows whether its main branch builds and passes its tests.

## Requirements

### Requirement: Checks run on every push and pull request
A CI workflow SHALL run on every push to any branch and on every pull request, and SHALL fail when any of its steps fails.

#### Scenario: Push to main
- **WHEN** a commit is pushed to `main`
- **THEN** the workflow runs and its result is shown on the commit

#### Scenario: Pull request
- **WHEN** a pull request is opened or updated
- **THEN** the workflow runs against its head and its result is shown on the pull request

### Requirement: What the workflow checks
The workflow SHALL run, on a Linux runner: the JVM tests of every client module, the AppView and segment importer tests, the formatting, detekt and dependency-sort checks, Android lint, and the public-safety check. iOS and wasm tests are not run in CI.

#### Scenario: Failing unit test
- **WHEN** a pushed commit breaks a JVM test in any module
- **THEN** the workflow fails and names the failing task

#### Scenario: Unformatted Kotlin
- **WHEN** a pushed commit contains Kotlin that ktfmt would reformat
- **THEN** the workflow fails

#### Scenario: Unsafe file
- **WHEN** a pushed commit adds a file the public-safety check rejects
- **THEN** the workflow fails and names the file

### Requirement: No personal secrets in CI
The workflow SHALL resolve every dependency, including those on GitHub Packages, with the token GitHub issues to the workflow run, and SHALL need no repository secret added by hand.

#### Scenario: Fresh fork
- **WHEN** someone forks the public repository, enables Actions on the fork and pushes to it
- **THEN** the workflow resolves its dependencies without any secret configured on the fork

### Requirement: Status badge
The README SHALL show the workflow's status for `main`, linking to its runs.

#### Scenario: Red main
- **WHEN** the latest run on `main` failed
- **THEN** the README badge shows failing

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
