# Spec Delta

## Purpose

Runs the repository's checks automatically on GitHub for every push and pull request, so the public repository shows whether its main branch builds and passes its tests.

## ADDED Requirements

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
