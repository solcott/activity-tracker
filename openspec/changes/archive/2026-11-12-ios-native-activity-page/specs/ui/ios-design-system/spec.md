# Spec Delta

## ADDED Requirements

### Requirement: A native chart can be inverted
A native line chart whose shared chart is inverted SHALL draw its larger values at the bottom and its smaller at the top, with the same gridlines and labels, as a pace chart puts the faster pace at the top.

#### Scenario: A pace chart
- **WHEN** a native page draws a run's pace chart
- **THEN** 4:30 /km is drawn above 6:00 /km

### Requirement: A native chart marks its average
A native line chart whose shared chart has an average SHALL mark it with a dashed line across the chart at that value.

#### Scenario: Heart rate
- **WHEN** a native page draws a heart rate chart averaging 142 bpm
- **THEN** a dashed line crosses the chart at 142 bpm
