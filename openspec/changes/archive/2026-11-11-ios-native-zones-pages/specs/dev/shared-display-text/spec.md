# Spec Delta

## ADDED Requirements

### Requirement: Computed zones read the same in both UIs
A computed zone's name, its range with its unit (bpm or W) or as paces open-ended at either end, a pace unit, a zone model's name (its token where the app has no name for it), what a model is based on and what it needs SHALL be chosen by shared code, and both UIs SHALL show that choice. A zone's name SHALL be chosen once for the zones pages and the activity charts.

#### Scenario: A heart rate zone's range
- **WHEN** a heart rate zone runs from 143 to 150
- **THEN** both UIs read "143–150 bpm", and the last zone from 180 reads "180+ bpm"

#### Scenario: A pace zone's range
- **WHEN** a running pace zone sits between 5:10 and 4:50 per kilometer
- **THEN** both UIs read "5:10–4:50 /km"

#### Scenario: A model the app doesn't name
- **WHEN** another client saved a zone model whose token the app has no name for
- **THEN** both UIs show the token as it is

#### Scenario: A zone's name in an activity
- **WHEN** an activity's time in zone lists Zone 2 of the default heart rate model
- **THEN** it is named as the zones pages name that zone

### Requirement: The zones pages' words and conditions are decided once
Each set of zones' heading and what it applies to, which thresholds it lists with each one's value, "Not set" or marked as the default's, a sport's form title, help, pace label, the default a blank field falls back to and Remove, SHALL be chosen by shared code. Which fields hold a valid value, how many characters each takes, which thresholds imports may update, and when each zones page can save SHALL be decided by shared code too.

#### Scenario: A threshold from the defaults
- **WHEN** a sport sets no max heart rate of its own and the default is 190
- **THEN** both UIs read "190 bpm (default)" for it on Training zones, and "Default: 190" in the sport's empty field

#### Scenario: Out of range
- **WHEN** a max heart rate reads 300
- **THEN** both UIs mark it as wrong and neither can save

### Requirement: Privacy zones read the same in both UIs
A privacy distance (Off, or the distance in the athlete's units), the hidden distances offered, the add-here button's words, and when the page can change SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: Off
- **WHEN** route ends are not hidden
- **THEN** both UIs read "Off" for Hide start and end

#### Scenario: A radius in miles
- **WHEN** an athlete whose units are imperial has a zone of 800 m
- **THEN** both UIs show its radius in miles, as stats read
