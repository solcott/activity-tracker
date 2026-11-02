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

### Requirement: Physical-device mode
The local stack SHALL offer a mode in which the PDS, PLC and AppView are reachable over HTTPS on stable public hostnames on a developer's own domain, and SHALL run in it whenever a device host is configured. The PDS SHALL name its public hostname in new accounts' DID documents and as its OAuth issuer, so a physical iPhone or iPad can sign in to a local account, and its writes are indexed by the local AppView.

#### Scenario: Sign in from a physical iPad
- **WHEN** device mode is on and a Personal-Team-signed iOS build on a physical iPad signs in to a local `.test` account, on Wi-Fi or cellular
- **THEN** sign-in completes

#### Scenario: Device write reaches the AppView
- **WHEN** that iPad saves a manual activity
- **THEN** the record lands in the account's repo on the local PDS and the local AppView serves it in the athlete's feed

#### Scenario: Hostnames survive restarts
- **WHEN** a developer stops and restarts the stack in device mode
- **THEN** the PDS, PLC and AppView keep the same public hostnames, and accounts and builds made before the restart keep working

### Requirement: No open sign-up on a public PDS
In device mode the PDS SHALL require an invite code to create an account, while the repository's seeding scripts SHALL still create local accounts without manual steps.

#### Scenario: Stranger tries to sign up
- **WHEN** someone calls `com.atproto.server.createAccount` on the public PDS hostname without an invite code
- **THEN** the PDS refuses it

#### Scenario: Seeding in device mode
- **WHEN** a developer runs `scripts/dev.sh --seed alice` in device mode
- **THEN** `alice.test` is created

### Requirement: Device hostnames stay out of the repository
Device mode SHALL be configured only outside tracked files, by one host on the developer's own domain: in a git-ignored file of the checkout, or in a user-level file outside every checkout. No tracked file SHALL name a developer's domain or tunnel, and the repository SHALL provide no default host.

#### Scenario: Another developer's checkout
- **WHEN** someone else clones the repository and sets the device host to a subdomain of their own Cloudflare zone
- **THEN** device mode uses hostnames on their domain only, and nothing in the checkout refers to anyone else's domain

#### Scenario: No host configured
- **WHEN** the device host is set neither in the checkout nor in the developer's user-level configuration
- **THEN** the stack runs in localhost mode, bringing it up says how to turn device mode on, and device setup refuses to run, naming the setting to add

#### Scenario: Turning device mode on
- **WHEN** a developer turns device mode on
- **THEN** the only files that change are git-ignored files of the checkout (`local.properties`, `docker/.env`, `.dev/`) and user-level files outside the repository (the Gradle user home's `gradle.properties` and the tunnel's own files)

### Requirement: Localhost mode unchanged
With device mode off, the stack and every client SHALL behave as before: the PDS identifies as `http://localhost:2583`, and the iOS simulator, Android over `adb reverse`, desktop and web sign in without extra configuration.

#### Scenario: Default checkout
- **WHEN** a developer who never configured device mode runs `scripts/dev.sh` and builds the apps
- **THEN** the PDS public URL is `http://localhost:2583` and every client resolves identities through `http://localhost:2582` and `http://localhost:2583`

### Requirement: Mode switches never mix identities
Bringing up the stack SHALL refuse to start in a mode whose PDS hostname differs from the one its existing accounts were created under, unless the developer asks to wipe the stack's data. A wipe SHALL leave a working stack with lexicon authority, labeler and requested seed accounts recreated.

#### Scenario: Switch without wiping
- **WHEN** a stack created in localhost mode is brought up after device mode is configured, without the wipe option
- **THEN** the command exits non-zero, explains that existing accounts point at the old hostname, and names the wipe option

#### Scenario: Switch with wiping
- **WHEN** the same command runs with the wipe option
- **THEN** it deletes the stack's volumes, recreates the lexicon authority and labeler, recreates requested seed accounts under the new hostname, and the PDS reports the new public URL

### Requirement: Device mode follows the developer across checkouts
A device host set in the developer's user-level configuration SHALL apply to every checkout and worktree of the repository, for both the stack and the builds, with nothing to copy into the checkout. Bringing up the stack from any checkout SHALL recognise the mode the shared stack's accounts were created under from the stack itself, not from that checkout's files.

#### Scenario: New worktree against a device-mode stack
- **WHEN** the device host is set user-wide, the shared stack already runs in device mode, and a developer brings it up from a worktree created afterwards without the wipe option
- **THEN** it comes up in device mode with the existing accounts, and native builds from that worktree target the public hostnames

#### Scenario: Worktree with a stale local copy
- **WHEN** a worktree's git-ignored files are copies from before device mode was configured
- **THEN** the user-level device host still applies, and the stale copies don't switch it to localhost mode

### Requirement: Every client uses the public hosts in device mode
In device mode the web and desktop clients SHALL resolve identities through the public PLC and PDS hostnames and call the AppView at its public hostname, as native clients do. The AppView SHALL then accept only its public identity as the service-auth audience.

#### Scenario: Web sign-in in device mode
- **WHEN** device mode is on and a developer signs in to a local `.test` account from the web app on the Mac
- **THEN** sign-in completes, and the app's AppView calls go to the AppView's public hostname

#### Scenario: Desktop sign-in in device mode
- **WHEN** device mode is on and a developer signs in from the desktop app
- **THEN** sign-in completes through its loopback redirect, and the app's AppView calls go to the AppView's public hostname

#### Scenario: Localhost audience in device mode
- **WHEN** device mode is on and a request reaches the AppView with a service-auth token whose audience is `did:web:localhost%3A8081`
- **THEN** the AppView rejects it
