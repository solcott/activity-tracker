# Spec Delta

## ADDED Requirements

### Requirement: A route's shape reaches Swift as placed points
Shared code SHALL place a route's served polyline and breaks on a tile of the size Swift gives, smoothed as the shared line preview smooths it, and Swift SHALL read the result as typed arrays of points for each piece of line, the start and end, and each break's stop and resume, without decoding the polyline itself.

#### Scenario: A route with a break
- **WHEN** Swift asks for the shape of a two-piece route on a 300 by 160 point tile
- **THEN** it reads two pieces of points inside the tile, a start, an end and one break

#### Scenario: Too short to draw
- **WHEN** Swift asks for the shape of a polyline with one point
- **THEN** it reads no shape

### Requirement: A time of day can carry the activity's clock
A time of day chosen in shared code SHALL be able to carry a UTC offset, and Swift SHALL format such a time in that offset rather than the device's time zone, on the device's 12- or 24-hour clock.

#### Scenario: A start at UTC-7
- **WHEN** Swift formats 16:00 UTC carrying an offset of -420 minutes on a 12-hour clock
- **THEN** it reads "9:00 AM", whatever the device's zone
