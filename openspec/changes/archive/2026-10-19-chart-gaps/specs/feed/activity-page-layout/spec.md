# Spec Delta

## ADDED Requirements

### Requirement: Charts break at gaps in their samples
A time-series chart on the activity page SHALL draw no line and no fill between two consecutive samples of a series that are further apart than that series' gap limit: 2 minutes, or four of its usual sample intervals when that is longer. This SHALL hold for every series drawn over time, including a second line, the elevation behind a chart and a dive's lines, on every activity, combined or not. A sample missing its value SHALL count as no sample. Gears and the power curve SHALL NOT break.

#### Scenario: Device change on a combined ride
- **WHEN** a combined ride whose bike computer stopped recording heart rate and power, and whose watch started recording 23 minutes later, is opened
- **THEN** its heart rate and power charts draw no line or fill across those 23 minutes, and the line resumes where the watch's samples begin

#### Scenario: Auto-pause on a single recording
- **WHEN** a ride recorded at one sample a second with a 10-minute auto-pause is opened
- **THEN** each of its charts breaks across the 10 minutes and draws unbroken on either side

#### Scenario: Short stops stay joined
- **WHEN** a ride recorded at one sample a second with a 90-second stop is opened
- **THEN** its charts draw unbroken lines across the stop

#### Scenario: Smart recording
- **WHEN** a ride with smart recording, samples 1 to 8 seconds apart and no longer gaps, is opened
- **THEN** its charts draw unbroken lines

#### Scenario: Sparse steady recording
- **WHEN** a run with heart rate logged every 60 seconds is opened
- **THEN** its heart rate chart draws one unbroken line

#### Scenario: Sensor dropout
- **WHEN** a ride whose heart rate strap stopped for 5 minutes while power kept recording is opened
- **THEN** the heart rate chart breaks across those 5 minutes and the power chart does not

#### Scenario: Gears
- **WHEN** a ride with a gear shift 10 minutes after the previous one is opened
- **THEN** the gears chart holds the earlier gear until the shift, unbroken

### Requirement: Thinned charts keep the recorded gaps
When a long series is thinned to fewer chart points, the chart SHALL break exactly where the recorded samples have a gap: no point SHALL be drawn inside a gap, and thinning SHALL neither hide a gap nor make one where the samples had none.

#### Scenario: Long ride with a short gap
- **WHEN** a 10-hour ride recorded at one sample a second, with a 3-minute gap in the middle, is opened
- **THEN** its charts break across those 3 minutes, with no point inside them, and draw unbroken elsewhere
