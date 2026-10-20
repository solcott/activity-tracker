# Spec Delta

## MODIFIED Requirements

### Requirement: Single activities break at far-apart gaps
A single activity's map line SHALL break, drawing no route line between them, where two consecutive positions were recorded further apart in time than the track's gap limit (2 minutes, or four of its usual position intervals when that is longer) and are more than 200 m apart. Where either condition fails, the line SHALL join them.

#### Scenario: Dead GPS
- **WHEN** a ride recorded every second loses its position for 6 minutes and the next position is 3 km on
- **THEN** its map draws two route lines, joined only by a break's dashed connector

#### Scenario: Café stop
- **WHEN** a ride recorded every second records no position for 30 minutes and resumes 20 m from where it stopped
- **THEN** its map draws one line

#### Scenario: Short dropout
- **WHEN** a ride recorded every second loses its position for 60 seconds and resumes 500 m on
- **THEN** its map draws one line

#### Scenario: Sparse steady recording
- **WHEN** a run with a position logged every 60 seconds, 250 m apart, is saved
- **THEN** its map draws one line

## ADDED Requirements

### Requirement: A dashed connector bridges each break
Every route map SHALL join the last position before each break of its line and the first after it with a straight dashed line in the break markers' colour, drawn beneath the markers. It SHALL NOT be drawn solid or in the route's colour, so it never reads as recorded route. A line with no breaks SHALL draw no connector.

#### Scenario: Accidental pause
- **WHEN** a ride whose timer was stopped for 7 minutes while the athlete rode on 288 m is shown
- **THEN** its map draws a dashed connector from where recording stopped to where it resumed

#### Scenario: Combined activity
- **WHEN** an activity combined from two recordings is shown
- **THEN** a dashed connector joins where the first recording ends and the second begins

#### Scenario: No breaks
- **WHEN** a run recorded without a gap is shown
- **THEN** its map draws no dashed connector
