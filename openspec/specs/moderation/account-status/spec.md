# moderation/account-status Specification

## Purpose

Defines how an account's hosting status, as the network reports it, changes what the AppView serves, so accounts taken down or suspended by their PDS or relay disappear from the app.

## Requirements

### Requirement: Inactive accounts aren't served
When Jetstream reports an account as not active with status `takendown`, `suspended` or `deactivated`, the AppView SHALL stop serving that account's profile, activities, likes and comments to anyone. It SHALL keep what it indexed. A `deleted` status SHALL still remove the account's data as it does today.

#### Scenario: Taken down by the PDS
- **WHEN** Jetstream delivers an `#account` event for bob with `active: false` and `status: takendown`
- **THEN** bob's profile answers not found, and his activities, likes and comments leave every feed, list and count

#### Scenario: Deactivated
- **WHEN** bob deactivates his account
- **THEN** the AppView stops serving his content but keeps it indexed

### Requirement: Reactivated accounts are served again
When Jetstream reports a previously inactive account as active, the AppView SHALL serve its indexed content again under the usual rules, without reindexing it.

#### Scenario: Reactivation
- **WHEN** bob's account was `deactivated` and Jetstream then reports it `active: true`
- **THEN** bob's profile and activities are served again as before

### Requirement: Status survives restarts
The AppView SHALL store each account's last reported status, so a restart doesn't serve an inactive account again.

#### Scenario: Restart
- **WHEN** bob is `suspended` and the AppView restarts
- **THEN** bob's content is still not served
