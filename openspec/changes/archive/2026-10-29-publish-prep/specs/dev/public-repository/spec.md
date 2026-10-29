# Spec Delta

## Purpose

Keeps the public GitHub repository presentable and safe to publish: it says what the project is and under what license, and no tracked file reveals where the maintainer lives or details of their machine.

## ADDED Requirements

### Requirement: Open-source license
The repository root SHALL hold a `LICENSE` file with the Apache License 2.0, and the README SHALL name the license. Third-party sample and fixture files keep their own licenses beside them.

#### Scenario: Visitor checks the license
- **WHEN** a visitor opens the repository on GitHub
- **THEN** GitHub shows the project as Apache-2.0 licensed

### Requirement: README introduces the project before setup
The README SHALL open with what the project is and does, screenshots of the app, an architecture overview of how clients, PDSes and the AppView exchange data, and the main technologies used, before any setup instructions.

#### Scenario: First screen of the README
- **WHEN** a visitor opens the repository page
- **THEN** they see the project's description, the CI badge and screenshots before the local development section

#### Scenario: Screenshots are fixtures
- **WHEN** the README shows a screenshot of the app
- **THEN** the image is a committed screenshot-test reference rendered from fixture data, not a capture of a real account or a real map

### Requirement: README states how the project was built
The README SHALL say that the project was developed with AI coding assistants under a spec-driven workflow, and point to where the specs (`openspec/`) and assistant configuration (`.claude/`, `.agents/`) live.

#### Scenario: Visitor finds the specs
- **WHEN** a visitor wonders what the `openspec/` folder is
- **THEN** the README explains it and links to it

### Requirement: README stays accurate
The README's project layout and known gaps SHALL describe the current code: every top-level module directory appears in the layout, and no known gap lists something the app already does.

#### Scenario: Maps
- **WHEN** the app draws route maps with a map library
- **THEN** the known gaps do not call route maps a placeholder

### Requirement: No personal location data in tracked files
Tracked activity files (FIT, GPX, TCX, KML, KMZ, UDDF, Subsurface logs, and their gzip forms) SHALL be third-party or synthetic, each in a directory holding a license or source note for it. Screenshots taken while verifying a change SHALL NOT be committed.

#### Scenario: Activity file with a source note
- **WHEN** a third-party FIT file is committed next to its license file
- **THEN** the public-safety check passes

#### Scenario: Stray personal export
- **WHEN** a GPX file is committed in a directory with no license or source note
- **THEN** the public-safety check fails and names the file

#### Scenario: Verification screenshot
- **WHEN** an image is committed anywhere under `openspec/`
- **THEN** the public-safety check fails and names the file

### Requirement: No local machine details in tracked files
Tracked text files SHALL NOT contain absolute paths into a user's home folder (`/Users/<name>/` or `/home/<name>/`).

#### Scenario: Plan path in a task list
- **WHEN** a committed task list cites `/Users/<name>/.claude/plans/...`
- **THEN** the public-safety check fails and names the file and line

#### Scenario: Placeholder path
- **WHEN** documentation shows an example path such as `/path/to/kmp-dataresult`
- **THEN** the public-safety check passes

### Requirement: Public-safety check is runnable locally
The public-safety check SHALL be one documented command that inspects only tracked files, exits non-zero listing every offending file when any rule fails, and exits zero silently otherwise.

#### Scenario: Clean tree
- **WHEN** a developer runs the check on a tree with no offending files
- **THEN** it exits zero with no output

#### Scenario: Untracked export
- **WHEN** an untracked, gitignored GPX file sits in the working tree
- **THEN** the check ignores it
