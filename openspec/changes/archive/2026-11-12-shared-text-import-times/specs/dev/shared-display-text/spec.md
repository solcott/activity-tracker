## ADDED Requirements

### Requirement: An import's starts read like every other start
An import's start (a batch row, the compare sheet, a duplicate's existing activity, the recording a file is added to) SHALL be written by shared code as the date written out at a time each UI formats on the device's 12- or 24-hour clock, in the UTC offset of the zone it's read in. The state SHALL carry the moment and that offset, not text.

#### Scenario: A 12-hour device
- **WHEN** a batch row of a ride started at 07:02 on 2024-05-12 in its zone is shown on a device using a 12-hour clock
- **THEN** both UIs begin its line with "May 12, 2024 at 7:02 AM"

#### Scenario: A 24-hour device
- **WHEN** the same row is shown on a device using a 24-hour clock
- **THEN** both UIs begin its line with the date and "07:02" (Compose) or the locale's short time (iOS), not "2024-05-12 07:02"

#### Scenario: A start on another offset
- **WHEN** an existing activity started at 23:30 UTC and the file is read in UTC+02:00
- **THEN** the duplicate warning reads the next day's date at 1:30 AM
