# Spec Delta

## ADDED Requirements

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
