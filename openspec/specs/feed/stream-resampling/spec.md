# feed/stream-resampling Specification

## Purpose

How an activity's stream samples are turned into per-second values for time in zone and normalized power, so these figures match the time actually recorded whatever the file's sampling rate. The power curve holds samples the same way but reads stops as clock time (`feed/power-curve`).

## Requirements

### Requirement: Sparse steady recording counts in full
Time in zone, the power curve and normalized power SHALL count each sample of a stream as lasting until the next sample when the gap is within the stream's usual recording interval, whatever that interval is. Gaps longer than that are pauses: time in zone and normalized power follow Requirement "Pauses still count once", and the power curve follows `feed/power-curve`.

#### Scenario: TCX logged every 30 seconds
- **WHEN** the page of an 8:00 run whose heart rate was logged every 30 s, all in zone 3, is shown
- **THEN** its heart-rate zones show about 8:00 in zone 3

#### Scenario: One-second recording unchanged
- **WHEN** the page of a ride recorded at one sample a second is shown
- **THEN** its zone times, power curve and normalized power are the same as before this change

### Requirement: Pauses still count once
For time in zone and normalized power, a gap much longer than the stream's usual recording interval SHALL count as a pause, adding one second, so time stopped is not counted in any zone. The power curve SHALL NOT count a pause this way: it counts a pause's real length at 0 W (`feed/power-curve`).

#### Scenario: Auto-pause in a one-second file
- **WHEN** a one-second ride has a 10-minute gap from auto-pause
- **THEN** that gap adds no more than a second to any zone

#### Scenario: Pause in a sparse file
- **WHEN** a run logged every 30 s has a 20-minute gap
- **THEN** that gap adds no more than one recording interval to any zone

#### Scenario: Pause and the power curve
- **WHEN** a one-second ride has a 10-minute gap from auto-pause
- **THEN** its normalized power ignores the gap, and its power curve counts it as 10 minutes at 0 W

### Requirement: Smart recording handled
A stream recorded at irregular short intervals (smart recording, typically 1–8 s apart) SHALL count each sample until the next.

#### Scenario: Smart-recorded ride
- **WHEN** a ride's samples are 1 to 8 seconds apart
- **THEN** its zone times add up to its recorded time, within a few seconds
