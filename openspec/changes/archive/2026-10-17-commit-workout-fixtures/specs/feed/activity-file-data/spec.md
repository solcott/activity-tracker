## ADDED Requirements

### Requirement: Lap speed from distance and time
When a lap has a distance and a time but the file gives no average speed, the lap table SHALL show its speed as its distance over its moving time, or over its elapsed time when it has no moving time.

#### Scenario: TCX lap without the speed extension
- **WHEN** a TCX run whose laps have no `AvgSpeed` extension, one lap 1,000 m in 5:00, is shown
- **THEN** that lap's row shows 12.0 km/h (7.5 mph)

#### Scenario: Lap without distance
- **WHEN** a lap has no distance
- **THEN** its speed shows a dash
