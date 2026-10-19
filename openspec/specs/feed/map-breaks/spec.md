# feed/map-breaks Specification

## Purpose


Where an activity's map line breaks, so the map never draws a straight line across ground the athlete's device didn't record.

## Requirements


### Requirement: Single activities break at far-apart gaps
A single activity's map line SHALL break, drawing no line between them, where two consecutive positions were recorded further apart in time than the track's gap limit (2 minutes, or four of its usual position intervals when that is longer) and are more than 200 m apart. Where either condition fails, the line SHALL join them.

#### Scenario: Dead GPS
- **WHEN** a ride recorded every second loses its position for 6 minutes and the next position is 3 km on
- **THEN** its map draws two lines with nothing between them

#### Scenario: Café stop
- **WHEN** a ride recorded every second records no position for 30 minutes and resumes 20 m from where it stopped
- **THEN** its map draws one line

#### Scenario: Short dropout
- **WHEN** a ride recorded every second loses its position for 60 seconds and resumes 500 m on
- **THEN** its map draws one line

#### Scenario: Sparse steady recording
- **WHEN** a run with a position logged every 60 seconds, 250 m apart, is saved
- **THEN** its map draws one line

### Requirement: Breaks are stored with the activity
The breaks SHALL be written with the activity's polyline, after it is trimmed for privacy, whenever the polyline is written: when an activity is recorded, imported, or retrimmed. Every viewer and the feed card SHALL draw them. At most 10 breaks SHALL be kept, those between the positions furthest apart; the rest SHALL be joined.

#### Scenario: Feed card
- **WHEN** a follower's feed shows a ride imported with a 3 km GPS gap
- **THEN** its card's map draws the ride in two lines

#### Scenario: Many gaps
- **WHEN** a ride with 14 far-apart gaps is saved
- **THEN** its map breaks at the 10 gaps whose positions are furthest apart and joins the other 4

#### Scenario: Activities saved before
- **WHEN** a ride saved before breaks were stored is shown on its owner's detail page
- **THEN** the owner's map breaks at its gaps, while its feed card and other viewers draw it unbroken until it is saved again

### Requirement: Import preview shows the breaks
The import preview's map SHALL break where the activity will once saved.

#### Scenario: Previewing a file with a gap
- **WHEN** a FIT file whose GPS stopped for 6 minutes and resumed 3 km on is picked for import
- **THEN** the preview's map draws two lines
