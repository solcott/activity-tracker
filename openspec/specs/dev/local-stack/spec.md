# dev/local-stack Specification

## Purpose

Defines what the local development stack (PLC, PDS, relay, Jetstream, Postgres and the AppView) guarantees to developers, including atproto Spaces support on the local PDS.

## Requirements

### Requirement: Spaces-capable local PDS
The local PDS SHALL run an image that serves the `com.atproto.space.*`, `com.atproto.memberspace.*` and `com.atproto.managedspace.*` XRPC methods of the 2026-10-29 Spaces alpha. The image SHALL be pinned to an exact digest, so a rebuild never picks up an unreviewed alpha.

#### Scenario: PDS serves simplespace
- **WHEN** a signed-in local account calls `com.atproto.space.createSpace` on the local PDS with a `com.atproto.managedspace.policy`
- **THEN** the PDS creates the space and returns its `at://{did}/space/{type}/{skey}` URI

#### Scenario: Rebuild is reproducible
- **WHEN** the PDS image is rebuilt
- **THEN** it uses the same pinned digest, not whatever the moving `pds-spaces-alpha` tag points to now

### Requirement: Runs on Apple Silicon
The local PDS SHALL start on arm64 macOS hosts even though the Spaces alpha image is published for amd64 only.

#### Scenario: Start on an arm64 Mac
- **WHEN** a developer brings the stack up on an Apple Silicon Mac
- **THEN** the PDS container starts under amd64 emulation and becomes healthy

### Requirement: OAuth sign-in on every client
Signing in against the local PDS SHALL keep working on Android, iOS, desktop and web, including in WebKit, which drops `Secure` cookies on `http://localhost`. The image patch that enables this SHALL fail the build if its target has drifted.

#### Scenario: iOS sign-in against the local PDS
- **WHEN** the iOS app signs in to a local account
- **THEN** sign-in completes without a "Missing CSRF header" error

#### Scenario: Patch target drifted
- **WHEN** a new image no longer has the patched file or line
- **THEN** the PDS image build fails instead of starting unpatched

### Requirement: Spaces smoke check
The repository SHALL provide a script that, given a local account, creates a space, writes a record into it, reads it back as the owner and deletes it. It SHALL exit non-zero on any failure.

#### Scenario: Smoke check passes on a fresh stack
- **WHEN** the stack is up, an account exists, and the smoke script runs for it
- **THEN** every step succeeds and the script exits zero

#### Scenario: Smoke check fails on a PDS without Spaces
- **WHEN** the smoke script runs against a PDS that doesn't serve the Spaces methods
- **THEN** it prints which call failed and exits non-zero

#### Scenario: Smoke check can rerun
- **WHEN** the smoke script runs twice for the same account
- **THEN** the second run also succeeds

### Requirement: Local lexicon authority for space types
The local stack SHALL make the PDS resolve every space-type declaration in the repository's `lexicons/` directory, by publishing them from a local account the PDS treats as its lexicon authority. Bringing the stack up SHALL do this without manual steps.

#### Scenario: Fresh stack resolves the space type
- **WHEN** a developer brings up a fresh stack with `scripts/dev.sh`
- **THEN** the PDS resolves `app.activitytracker.space.athlete` to its `"type": "space"` declaration

#### Scenario: OAuth sign-in with the space scope
- **WHEN** any client signs in to a local account requesting the `space:app.activitytracker.space.athlete` scope
- **THEN** authorization completes and the consent screen lists access to permissioned spaces

#### Scenario: Declaration changes are republished
- **WHEN** a space declaration in `lexicons/` changes and the developer reruns `scripts/dev.sh`
- **THEN** the PDS serves the updated declaration once its lexicon cache refreshes

#### Scenario: Rerunning is safe
- **WHEN** `scripts/dev.sh` runs against a stack whose lexicon authority account already exists
- **THEN** it reuses that account and its DID, and the PDS keeps resolving the declarations

### Requirement: Local labeler
The local stack SHALL run a pinned Ozone labeler with its own database and a local labeler account. The account's DID document SHALL declare the `#atproto_labeler` service and the `#atproto_label` key. `scripts/dev.sh` SHALL set this up without manual steps and configure the AppView and clients with the labeler's DID.

#### Scenario: Fresh stack has a labeler
- **WHEN** a developer brings up a fresh stack with `scripts/dev.sh`
- **THEN** the labeler account's DID document on the local PLC lists `#atproto_labeler` and `#atproto_label`, and Ozone answers `com.atproto.label.queryLabels`

#### Scenario: Report reaches the local labeler
- **WHEN** a local account sends `createReport` to the local PDS with the proxy header naming the local labeler
- **THEN** the report shows in the local Ozone queue

#### Scenario: Labels reach the local AppView
- **WHEN** a moderator applies `!hide` to a local activity in the local Ozone
- **THEN** the local AppView stops listing that activity in feeds
