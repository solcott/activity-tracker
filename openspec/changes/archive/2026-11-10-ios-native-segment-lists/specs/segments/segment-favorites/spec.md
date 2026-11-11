# Spec Delta

## ADDED Requirements

### Requirement: The starred list is native on iOS
On iOS the starred segments list SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared list shows, in the same words: each star with its segment's current name and its length and type, opening the segment, and a star that unstars it. It SHALL load page by page following `ui/content-loading`, asking for the next page only once the last star loaded is on screen.

#### Scenario: Two starred segments
- **WHEN** an athlete with a metric profile has starred a 1.6 km ride segment and a ski lift
- **THEN** the page is titled "Starred segments" and lists each by name, one with "1.6 km · Ride" and the other with its length and "Ski lift"

#### Scenario: Opening a starred segment
- **WHEN** the athlete taps a starred segment outside its star
- **THEN** that segment's page opens, as from the shared list

#### Scenario: No stars
- **WHEN** the athlete has starred nothing
- **THEN** the page reads "Star a segment to keep it here"

### Requirement: Unstarring and unavailable stars on iOS
On iOS a star's button SHALL unstar it, and SHALL be unavailable while that unstar is written. A star whose segment is gone SHALL read "This segment is no longer available", open nothing, and still offer its star. A failed unstar SHALL show above the stars, which stay. A session that must sign in again to read stars SHALL say so instead of the empty message.

#### Scenario: An unstar in flight
- **WHEN** the athlete has tapped the star of one segment and the unstar is being written
- **THEN** that star can't be tapped and the other stars can

#### Scenario: A gone segment
- **WHEN** a starred segment has been deleted
- **THEN** its row reads "This segment is no longer available", tapping it opens nothing, and its star can still be tapped

#### Scenario: Signing in again
- **WHEN** the session predates the grant stars need
- **THEN** the page says to sign in again and shows no empty message
