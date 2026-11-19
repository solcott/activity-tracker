# Spec Delta

## MODIFIED Requirements

### Requirement: The Trail page names its path
The Trail page SHALL show the path's name, whether it is a trail or a road, its length in the athlete's units, the sport groups of its listed segments with the one-way wording a trail row gives them (`segments/segment-browse`), and its data credit as segment pages credit a line's source.

#### Scenario: OSM trail
- **WHEN** an athlete opens Ann's, an OSM trail of 5.4 mi with ride, run and hike segments
- **THEN** the page reads "Ann's", Trail, 5.40 mi, Ride, Run, Hike, and credits OpenStreetMap contributors

#### Scenario: Downhill-only trail
- **WHEN** an athlete opens Rush, whose ride segments go one way only, downhill
- **THEN** the line under its name reads "Trail · 4.70 mi · Ride · Downhill only"
