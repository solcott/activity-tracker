# Spec Delta

## MODIFIED Requirements

### Requirement: A starred segment that is gone
A star whose segment `feed.getSegment` reports `NotFound`, whether deleted, never indexed, or taken down, SHALL be listed as unavailable and SHALL still be removable. The other starred segments SHALL list as usual. A lookup that fails for any other reason SHALL NOT list its segment as unavailable: the page it is on fails to load, with a retry.

#### Scenario: Segment deleted by its creator
- **WHEN** a segment an athlete starred is deleted
- **THEN** their list shows it as unavailable with an option to unstar, and their other stars are unaffected

#### Scenario: Lookup fails offline
- **WHEN** an athlete opens their starred segments, their stars are read, and the device goes offline before the segments are looked up
- **THEN** no segment is shown as unavailable, and the list says it couldn't load their starred segments because the device is offline, and offers to try again

### Requirement: Unreadable stars are not shown as none
When the athlete's `self` space can't be read, the starred list SHALL report that it couldn't load and why, and offer a retry instead of showing an empty list. Trying again SHALL show that it is loading, then the list once the space can be read. The segment screen SHALL then offer no star state rather than show the segment as unstarred.

#### Scenario: Offline list
- **WHEN** an athlete opens "Starred segments" while the device is offline, so their PDS can't be reached
- **THEN** the list reports it couldn't load their starred segments because the device is offline, and offers a retry

#### Scenario: Retry once online
- **WHEN** the athlete then tries again once their PDS can be reached
- **THEN** the list shows that it is loading, then their starred segments, and no error

#### Scenario: Offline segment screen
- **WHEN** an athlete opens a segment they starred while their PDS can't be reached
- **THEN** the screen doesn't show it as unstarred
