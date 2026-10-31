# Spec Delta

## ADDED Requirements

### Requirement: Physical-device mode
The local stack SHALL offer an opt-in mode in which the PDS, PLC and AppView are reachable over HTTPS on stable public hostnames on a developer's own domain. The PDS SHALL name its public hostname in new accounts' DID documents and as its OAuth issuer, so a physical iPhone or iPad can sign in to a local account, and its writes are indexed by the local AppView.

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
Device mode SHALL be configured only in git-ignored local files, by one host on the developer's own domain. No tracked file SHALL name a developer's domain or tunnel, and the repository SHALL provide no default host.

#### Scenario: Another developer's checkout
- **WHEN** someone else clones the repository and sets the device host to a subdomain of their own Cloudflare zone
- **THEN** device mode uses hostnames on their domain only, and nothing in the checkout refers to anyone else's domain

#### Scenario: No host configured
- **WHEN** the device host is unset
- **THEN** the stack runs in localhost mode and device setup refuses to run, naming the setting to add

#### Scenario: Turning device mode on
- **WHEN** a developer turns device mode on
- **THEN** only git-ignored files (`local.properties`, `docker/.env`, `.dev/` and the tunnel's own credentials outside the repository) change

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
