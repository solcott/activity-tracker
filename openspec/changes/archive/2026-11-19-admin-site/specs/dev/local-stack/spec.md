# Spec Delta

## ADDED Requirements

### Requirement: Local admin account
Bringing up the local stack SHALL create a local admin account if it doesn't exist and configure the AppView to list it as an `operator`, without manual steps. A wipe SHALL recreate it. Signing in to the AppView's admin site as that account SHALL work in both localhost and device mode.

#### Scenario: Fresh stack
- **WHEN** a developer runs `scripts/dev.sh` on a stack with no admin account
- **THEN** the admin account exists, the AppView lists it as an operator, and signing in at the AppView's `/admin` with its handle succeeds

#### Scenario: Rerun
- **WHEN** a developer runs `scripts/dev.sh` again
- **THEN** the same admin account is kept and still configured

#### Scenario: Wipe
- **WHEN** a developer wipes the stack's data
- **THEN** the new stack has the admin account again and it is configured
