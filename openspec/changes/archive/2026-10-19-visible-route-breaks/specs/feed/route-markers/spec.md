# Spec Delta

## MODIFIED Requirements

### Requirement: Breaks are marked differently
Where the line breaks (`polylineGaps`), the map SHALL mark the last position before the break with a stop marker and the first after it with a resume marker. Both SHALL be at least as large as the start and end markers and share a colour that differs from the route's, the start marker's and the end marker's; the stop and resume markers SHALL differ from each other in shape. A line with no breaks SHALL show no break markers.

#### Scenario: Dead GPS
- **WHEN** a ride whose line breaks once at a GPS gap is shown
- **THEN** its map shows a start marker, an end marker, a stop marker at the last position before the gap and a resume marker at the first after it

#### Scenario: Combined activity
- **WHEN** an activity combined from two recordings is shown
- **THEN** its map shows a stop marker where the first recording ends, a resume marker where the second begins, and start and end markers only at the ends of the whole activity

#### Scenario: Break seen on a zoomed-out card
- **WHEN** a feed card shows a ride whose 288 m break spans a few points of the card
- **THEN** the break's markers stand out from the route line in colour and are no smaller than its start marker
