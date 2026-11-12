# Spec Delta

## ADDED Requirements

### Requirement: A native chart can draw steps
A native line chart given the shared step style SHALL hold each value level until the next point and change there, with the area beneath following the steps, as the shared step chart does.

#### Scenario: A threshold that rose
- **WHEN** a native chart draws a step chart of 230 W then 250 W
- **THEN** the line stays at 230 W until the second point and rises straight up to 250 W there

### Requirement: A native chart can mark its points with labelled dots
A native line chart given the shared dots SHALL draw a dot on each of those points, filled or hollow as the shared chart chooses, with the shared label over it when there is one, kept inside the chart.

#### Scenario: The current value
- **WHEN** a native threshold chart marks its last point as current with the label "250"
- **THEN** that point has a filled dot with "250" over it, and the earlier points hollow dots with their values
