# Spec Delta

## ADDED Requirements

### Requirement: Manual entry is saved with its description
Manual entry SHALL offer the details form's description field under the title, on every platform. Saving SHALL save what is typed there, trimmed, as the activity's description; a blank one SHALL save none.

#### Scenario: Described entry
- **WHEN** alice enters a 55-minute ride with the description "Easy spin along the river." and saves
- **THEN** the saved ride's description is "Easy spin along the river."

#### Scenario: Nothing written
- **WHEN** alice saves a manual entry with an empty description
- **THEN** the saved activity has no description

## MODIFIED Requirements

### Requirement: Entering an activity by hand is native on iOS
On iOS manual entry SHALL be drawn with the system's own grouped form: the type, the title, the description, the units, distance, duration and temperature with the units in their labels, the gear and who sees the activity and each category. Save SHALL be in the bar and enabled only once a duration above zero is entered and nothing is saving; a failed save SHALL show at the top.

#### Scenario: No duration
- **WHEN** alice has typed no duration
- **THEN** Save is disabled, and typing 45 enables it

#### Scenario: Imperial units
- **WHEN** alice switches the form to imperial
- **THEN** distance reads in miles and temperature in °F
