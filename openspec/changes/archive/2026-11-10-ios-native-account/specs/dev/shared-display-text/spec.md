## ADDED Requirements

### Requirement: The Account page reads the same in both UIs
The sign-in status with an outdated grant's missing features in their order, each data export status's lines, and the end-session question's title, message and action SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: An outdated grant
- **WHEN** a grant is missing Leaving and Gear
- **THEN** both UIs list "Private gear, gear defaults and collections" before "Leaving Activity Tracker"

#### Scenario: Listing records
- **WHEN** an export is running and has listed no records yet
- **THEN** both UIs show the listing line and not a count of records

#### Scenario: Signing in again with an import saving
- **WHEN** the athlete chooses Sign in again while an import is saving
- **THEN** both UIs title the question "Sign in again?", say the import is saving, and offer "Sign in again anyway"

### Requirement: The Leave page reads the same in both UIs
The shared places' counts, the lines for what this device would lose, a running leave's step, its records deleted, and the time it was asked to wait until SHALL be chosen by shared code, and both UIs SHALL show that choice. The time SHALL follow the device's 12- or 24-hour clock.

#### Scenario: No shared places
- **WHEN** the athlete has no segments or dive sites
- **THEN** both UIs show the "none" line and no count

#### Scenario: A leave that hasn't reported a step
- **WHEN** a resumed leave has no step yet
- **THEN** both UIs read "Leaving Activity Tracker…"

#### Scenario: A 24-hour clock
- **WHEN** a leave was asked to wait until 19:05 on a device that uses a 24-hour clock
- **THEN** both UIs read "Carrying on at 19:05."
