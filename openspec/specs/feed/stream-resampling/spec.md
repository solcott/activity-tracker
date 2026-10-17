# feed/stream-resampling Specification

## Purpose

How an activity's stream samples are turned into per-second values for time in zone, the power curve and normalized power, so these figures match the time actually recorded whatever the file's sampling rate.

## Requirements

### Requirement: Sparse steady recording counts in full
Time in zone, the power curve and normalized power SHALL count each sample of a stream as lasting until the next sample when the gap is within the stream's usual recording interval, whatever that interval is.

#### Scenario: TCX logged every 30 seconds
- **WHEN** the page of an 8:00 run whose heart rate was logged every 30 s, all in zone 3, is shown
- **THEN** its heart-rate zones show about 8:00 in zone 3

#### Scenario: One-second recording unchanged
- **WHEN** the page of a ride recorded at one sample a second is shown
- **THEN** its zone times, power curve and normalized power are the same as before this change

### Requirement: Pauses still count once
A gap much longer than the stream's usual recording interval SHALL count as a pause, adding one second, so time stopped is not counted in any zone.

#### Scenario: Auto-pause in a one-second file
- **WHEN** a one-second ride has a 10-minute gap from auto-pause
- **THEN** that gap adds no more than a second to any zone

#### Scenario: Pause in a sparse file
- **WHEN** a run logged every 30 s has a 20-minute gap
- **THEN** that gap adds no more than one recording interval to any zone

### Requirement: Smart recording handled
A stream recorded at irregular short intervals (smart recording, typically 1–8 s apart) SHALL count each sample until the next.

#### Scenario: Smart-recorded ride
- **WHEN** a ride's samples are 1 to 8 seconds apart
- **THEN** its zone times add up to its recorded time, within a few seconds
