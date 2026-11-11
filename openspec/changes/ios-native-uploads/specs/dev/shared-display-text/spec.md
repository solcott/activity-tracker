# Spec Delta

## ADDED Requirements

### Requirement: A length of time reads the same in both UIs
A rough length of time SHALL be chosen by shared code, rounded to its largest unit: whole minutes (at least 1) under an hour, rounded hours under a day, and rounded days from a day up, and both UIs SHALL show that choice.

#### Scenario: Under an hour
- **WHEN** a length of 40 minutes 30 seconds is shown
- **THEN** both UIs read "40 min"

#### Scenario: A day and a bit
- **WHEN** a length of 26 hours is shown
- **THEN** both UIs read "1 day"

### Requirement: The upload queue reads the same in both UIs
The upload queue's words SHALL be chosen by shared code that both UIs ask: its items' and refused changes' names and states, its imports' lines, its spoken actions, its pauses and time left, and its questions. When its controls show and can be used SHALL be decided there too.

#### Scenario: Two exports
- **WHEN** an import was read from Strava and Garmin exports
- **THEN** both UIs read "Strava and Garmin exports"

#### Scenario: Busy
- **WHEN** the refused like is being sent again
- **THEN** both UIs show its Send again and Cancel unavailable

### Requirement: Day words follow the clock the UI gives
Words that depend on the current day (today, yesterday, tomorrow) SHALL be decided by shared code from the current time and time zone the UI gives, so a preview or test with a fixed clock reads the same on any day and in any zone.

#### Scenario: A server pause past midnight
- **WHEN** the server paused uploads until 1:10 AM tomorrow
- **THEN** both UIs read "Uploads paused by the server until tomorrow at 1:10 AM"

#### Scenario: An import saved today
- **WHEN** an import was saved at 7:00 AM today
- **THEN** both UIs read "Saved Today at 7:00 AM" in its group
