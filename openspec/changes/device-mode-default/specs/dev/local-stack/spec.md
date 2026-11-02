# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
