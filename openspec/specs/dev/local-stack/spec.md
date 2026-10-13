# dev/local-stack Specification

## Purpose

Defines what the local development stack (PLC, PDS, relay, Jetstream, Postgres and the AppView) guarantees to developers, including atproto Spaces support on the local PDS.

## Requirements

### Requirement: Spaces-capable local PDS
The local PDS SHALL run an image that serves the `com.atproto.space.*` and `com.atproto.simplespace.*` XRPC methods. The image SHALL be pinned to an exact digest, so a rebuild never picks up an unreviewed alpha.

#### Scenario: PDS serves simplespace
- **WHEN** a signed-in local account calls `com.atproto.simplespace.createSpace` on the local PDS
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
