# Spec Delta

## MODIFIED Requirements

### Requirement: An effort's time is measured from the track
An effort SHALL start the last time the track crosses the segment's start line going forward before it follows the segment. It SHALL end the first time the track then crosses the end line. Each line SHALL run across the segment, at right angles to its first or last stretch. Each crossing SHALL be interpolated between the samples either side of it. When the track moves slower than walking pace between those samples (a stop or a recording gap), the start SHALL take the later sample's time and the end the earlier one's. Elapsed time SHALL be in whole seconds. An effort SHALL also record when it began, as an offset from the activity's start, and SHALL NOT be stored with a wall-clock time.

#### Scenario: Elapsed time
- **WHEN** a run reaches a segment's start 120.4 s into the run and its end 731.6 s in
- **THEN** its effort's elapsed time is 611 s, starting 120 s into the activity

#### Scenario: Stop short of the start
- **WHEN** a ride stops 8 m short of a segment's start from 4812 s to 4956 s into the ride, recording all the while, then rides the segment and crosses its end line at 5448 s
- **THEN** its effort starts within one second of 4956 s into the activity and its elapsed time is within one second of 492 s

#### Scenario: Recording paused at the start
- **WHEN** a ride's last sample before a 144 s recording gap is 4812 s in, 8 m short of a segment's start, its next is 4956 s in, 7 m past the start, and it then rides the segment and crosses its end line at 5448 s
- **THEN** its effort starts 4956 s into the activity and its elapsed time is 492 s

#### Scenario: Stop just past the start
- **WHEN** a ride crosses a segment's start line, stops 8 m past it for 60 s, then rides on to the end
- **THEN** the stop counts toward its effort

#### Scenario: Stop at the end
- **WHEN** a run crosses a segment's end line 731 s in and then stands within 25 m of the end until 900 s
- **THEN** its effort ends at 731 s

#### Scenario: Switchbacks just past the start
- **WHEN** a segment starts with tight switchbacks, and a later leg crosses the start line again going forward within 25 m of the start
- **THEN** its effort still starts where the track first set off along the segment

#### Scenario: Switchbacks just before the end
- **WHEN** a segment ends with tight switchbacks, and an earlier leg crosses the end line going forward within 25 m of the end
- **THEN** its effort doesn't end until the track crosses the end line on the segment's last stretch

#### Scenario: Sparse samples
- **WHEN** a ride sampled every 40 m at a steady speed passes a segment's start halfway between two samples
- **THEN** its effort starts at the time halfway between those two samples
