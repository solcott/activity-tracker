# feed/power-curve Specification

## Purpose

How an activity's best average power for each length of time is worked out from its power stream, and how the activity page's power curve lays those durations out, so the figures match what Garmin Connect reports for the same ride.

## Requirements

### Requirement: Best power over clock time
An activity's best average power for a duration SHALL be the highest average over any stretch of that length of the activity's clock time. A gap longer than the stream's usual recording interval (a timer stop or dropout) SHALL count its real length at 0 W, so no stretch joins efforts from either side of it as if they were continuous. Gaps within the usual interval SHALL hold the earlier sample.

#### Scenario: Ride with timer stops
- **WHEN** the page of a ride is shown whose timer stopped 16 times for about 30 minutes in all, and whose best 20 minutes of clock time averages 179 W
- **THEN** its best 20 min power is 179 W, not a higher figure joining efforts from either side of a stop

#### Scenario: Ride without stops
- **WHEN** the page of a ride recorded at one sample a second with no gaps is shown
- **THEN** its best power for each duration is the same as before this change

#### Scenario: Smart recording
- **WHEN** the page of a ride whose power samples are 1 to 8 seconds apart with no stops is shown
- **THEN** each sample counts until the next and no gap is filled with 0 W

### Requirement: Curve durations
The power curve SHALL be read at 1, 2, 5, 10, 20 and 30 seconds, 1, 2, 5, 10, 20 and 30 minutes, and 1, 2, 3 and 5 hours, each only where the activity's clock time from its first power sample to its last is at least that long.

#### Scenario: Ride of 2:08 elapsed
- **WHEN** the page of a ride with 1:38 of timer time and 2:08 of clock time between its first and last power samples is shown
- **THEN** its power curve runs from 1 s to 2 h, with no 3 h point

#### Scenario: Short effort
- **WHEN** the page of a 4-minute ride with power is shown
- **THEN** its power curve runs from 1 s to 2 min

### Requirement: Best 20 min power follows the curve
The activity page's best 20 min power figure SHALL be the power curve's 20 min point. An activity whose clock time is shorter than 20 minutes SHALL show its curve's longest point from 1 minute up instead, as today.

#### Scenario: Matches Garmin Connect
- **WHEN** the page of a FIT ride whose session Garmin Connect reports as Max Avg Power (20 min) 179 W is shown
- **THEN** its power card shows best 20 min power 179 W, and the curve's 20 min point is 179 W

### Requirement: Curve durations evenly spaced
The power curve chart SHALL place its durations at equal distances along its x axis, in order, and label the axis with them. Between two durations the line SHALL follow durations on a log scale. No stretch of durations SHALL take more of the axis than another.

#### Scenario: Sub-minute efforts don't dominate
- **WHEN** the power curve of a ride with points from 1 s to 2 h is shown
- **THEN** the durations under 1 minute take 6 of its 13 equal steps, and 20 min sits as far from 5 min as 5 s does from 1 s

#### Scenario: Narrow window
- **WHEN** the power curve is shown on a window under 600dp wide
- **THEN** its axis labels never overlap: labels that would collide are left out, keeping the first and last

### Requirement: Curve values on demand
The power curve chart SHALL NOT print a watt value beside points on its line. Tapping or hovering the line SHALL show the duration and watts at that point. Its screen-reader description SHALL give the best power at 5 s, 1 min, 5 min, 20 min and 1 h, where the activity has them.

#### Scenario: Hover a point
- **WHEN** a viewer hovers the power curve at its 5 min point
- **THEN** a marker shows "5m · 219 W"

#### Scenario: Screen reader
- **WHEN** a screen reader reads the power curve of a 2-hour ride
- **THEN** it reads its best power at 5 s, 1 min, 5 min, 20 min and 1 h
