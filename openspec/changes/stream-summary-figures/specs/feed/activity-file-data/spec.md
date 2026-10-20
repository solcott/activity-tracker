# Spec Delta

## MODIFIED Requirements

### Requirement: Counts shown by sport
The activity's stats SHALL show `cycles` as steps for on-foot sports, as strokes for swimming, rowing and paddling, as pushes for wheelchair activities, and as pedal strokes for rides (every ride sub-sport, including mountain, gravel, indoor and e-bike) and velomobiles. They SHALL show average stroke distance where stored. Other sports SHALL NOT show `cycles`.

#### Scenario: Run page
- **WHEN** a viewer opens a run with `cycles` stored
- **THEN** its stats show its steps

#### Scenario: Ride page
- **WHEN** a viewer opens a mountain bike ride whose `cycles` holds 6,676 crank revolutions
- **THEN** its stats' Counts group shows 6,676 pedal strokes

#### Scenario: Handcycle
- **WHEN** a viewer opens a handcycle activity with `cycles` stored
- **THEN** its stats show no count

## ADDED Requirements

### Requirement: Average temperature from the samples
An activity imported from FIT whose records carry temperature SHALL publish as its average temperature the time-weighted mean of those samples, in tenths of a degree. The session's average SHALL be used only when no record carries a temperature. Activities imported earlier SHALL keep their stored average.

#### Scenario: Garmin ride
- **WHEN** an athlete imports a FIT ride whose session's average temperature is 19 °C and whose temperature samples average 19.24 °C
- **THEN** the published activity's average temperature is 192 tenths, which the page shows as 66.6 °F

#### Scenario: No temperature samples
- **WHEN** an athlete imports a FIT activity whose session records an average temperature and whose records carry none
- **THEN** the published activity's average temperature is the session's
