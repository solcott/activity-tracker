## ADDED Requirements

### Requirement: An import's zoneless start reads like every other start
The start a zone notice quotes for a file whose times have no zone SHALL be written by shared code as the date written out at its time of
day, the time on the device's 12- or 24-hour clock. It is the file's own wall clock, the same whichever zone is picked. The state SHALL
carry that wall clock, not text.

#### Scenario: A dive log on a 12-hour device
- **WHEN** a dive log whose first dive started at 17:30 on 2024-10-16 by the dive computer's clock is picked on a device using a 12-hour clock
- **THEN** both UIs' zone notice quotes its start as "Oct 16, 2024 at 5:30 PM", not "2024-10-16 17:30"

#### Scenario: A 24-hour device
- **WHEN** the same file is picked on a device using a 24-hour clock
- **THEN** both UIs quote the date and "17:30" (Compose) or the locale's short time (iOS)

#### Scenario: Another zone picked
- **WHEN** the athlete picks another zone for the file
- **THEN** the quoted start stays the same
