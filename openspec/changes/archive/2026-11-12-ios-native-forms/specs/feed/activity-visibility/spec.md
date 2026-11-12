# Spec Delta

## ADDED Requirements

### Requirement: Editing an activity is native on iOS
On iOS editing an activity SHALL be drawn with the system's own grouped form: the title (showing the activity's type while blank), a dive's site, the description, the photos, the gear, who sees the activity and each detail category, and Leave out of leaderboards when offered. Cancel SHALL lead the bar and Save trail it, a failed save SHALL show at the top, and loading SHALL follow `ui/content-loading`.

#### Scenario: Photos
- **WHEN** alice's activity holds two of its four photos
- **THEN** each shows with Remove, and Add photos offers two more

#### Scenario: Unsaved changes
- **WHEN** alice changes the title on the native edit form and taps Cancel
- **THEN** she is asked whether to discard her changes, and Keep editing leaves the form as it was

#### Scenario: Narrowing a public activity
- **WHEN** alice makes a Public activity Followers and taps Save
- **THEN** she is warned that published copies can't be recalled before anything is saved

#### Scenario: Widening the core charts
- **WHEN** saving would make an older activity's core charts as visible as the activity
- **THEN** she is asked first, naming that visibility

### Requirement: Entering an activity by hand is native on iOS
On iOS manual entry SHALL be drawn with the system's own grouped form: the type, the title, the units, distance, duration and temperature with the units in their labels, the gear and who sees the activity and each category. Save SHALL be in the bar and enabled only once a duration above zero is entered and nothing is saving; a failed save SHALL show at the top.

#### Scenario: No duration
- **WHEN** alice has typed no duration
- **THEN** Save is disabled, and typing 45 enables it

#### Scenario: Imperial units
- **WHEN** alice switches the form to imperial
- **THEN** distance reads in miles and temperature in °F
