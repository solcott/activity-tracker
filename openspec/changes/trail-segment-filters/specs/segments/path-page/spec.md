## MODIFIED Requirements

### Requirement: The Trail page keeps the browse sport filter
When opened from browse with one sport group chosen, the Trail page SHALL open listing only that group's segments, with that group chosen in its sport chips (`segments/path-filters`), from which any other sport group or All MAY be chosen.

#### Scenario: Opened with Ride
- **WHEN** an athlete with Ride chosen opens Ann's
- **THEN** the page lists Ann's ride segments only, with Ride chosen in the sport chips

#### Scenario: Showing all sports
- **WHEN** that athlete chooses All
- **THEN** the page lists Ann's segment groups for every sport
