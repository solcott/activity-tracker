# Spec Delta

## Purpose

Lets an athlete keep a private list of starred segments: where each star is stored, who can read it, and how the app stars, unstars and lists them.

## ADDED Requirements

### Requirement: A star is stored only in the self space
Starring a segment SHALL write one `feed.segmentStar` record, holding a strong ref to the segment, in the athlete's `self` space. It SHALL NOT be in the repo or any other space.

#### Scenario: Star a segment
- **WHEN** an athlete stars a segment
- **THEN** a `feed.segmentStar` for it is in their `self` space, and their repo and other spaces hold no record of it

### Requirement: One star per segment
A segment's star SHALL have a record key derived from the segment's URI alone, so starring the same segment again, on any device, writes the same record. Unstarring SHALL delete that record.

#### Scenario: Starred on two devices
- **WHEN** an athlete stars the same segment on their phone and on their desktop
- **THEN** their `self` space holds one `feed.segmentStar` for it

#### Scenario: Unstar
- **WHEN** an athlete unstars a segment they starred on another device
- **THEN** its `feed.segmentStar` is gone from their `self` space

### Requirement: Star and unstar from the segment screen
The segment screen SHALL show whether the athlete has starred the segment and let them star or unstar it. A change SHALL show at once and be queued, so it is written when the app is next online, even after a restart.

#### Scenario: Star offline
- **WHEN** an athlete stars a segment while offline, then reconnects
- **THEN** the segment shows as starred at once, and its `feed.segmentStar` is written once the app is online

#### Scenario: Unstar then star again before sending
- **WHEN** an athlete unstars and stars a segment again while offline
- **THEN** once online their `self` space holds one star for it

### Requirement: The starred list
The athlete's own profile SHALL open a list of their starred segments, read from their `self` space, newest star first. Each entry SHALL show the segment as `feed.getSegment` returns it for that athlete and open the segment screen. Other athletes' profiles SHALL NOT offer it.

#### Scenario: Open the list
- **WHEN** an athlete who starred three segments opens "Starred segments" from their profile
- **THEN** the three segments are listed, the most recently starred first, each with its name, length and sport group

#### Scenario: Another athlete's profile
- **WHEN** an athlete views someone else's profile
- **THEN** no starred segments list is offered

#### Scenario: Star elsewhere shows in the list
- **WHEN** an athlete stars a segment and then opens the list on the same device before the star is sent
- **THEN** the segment is in the list

### Requirement: A starred segment that is gone
A star whose segment `feed.getSegment` reports `NotFound`, whether deleted, never indexed, or taken down, SHALL be listed as unavailable and SHALL still be removable. The other starred segments SHALL list as usual.

#### Scenario: Segment deleted by its creator
- **WHEN** a segment an athlete starred is deleted
- **THEN** their list shows it as unavailable with an option to unstar, and their other stars are unaffected

### Requirement: Unreadable stars are not shown as none
When the athlete's `self` space can't be read, the starred list SHALL report that it couldn't load and offer a retry instead of showing an empty list. The segment screen SHALL then offer no star state rather than show the segment as unstarred.

#### Scenario: Offline list
- **WHEN** an athlete opens "Starred segments" while their PDS can't be reached
- **THEN** the list reports it couldn't load and offers a retry

#### Scenario: Offline segment screen
- **WHEN** an athlete opens a segment they starred while their PDS can't be reached
- **THEN** the screen doesn't show it as unstarred

### Requirement: Stars need the space grant
A session whose `space:` grant doesn't cover `feed.segmentStar` SHALL NOT offer starring, and its starred list SHALL tell the athlete to sign in again to use stars. Other segment features SHALL keep working on that session.

#### Scenario: Session from before stars
- **WHEN** an athlete signed in before `feed.segmentStar` was declared opens a segment
- **THEN** no star control is shown, and the segment and their efforts on it show as usual

### Requirement: The AppView never stores or serves stars
The AppView SHALL NOT store anything from a `feed.segmentStar` record, and no AppView view or count SHALL reveal whether anyone starred a segment. Only the athlete SHALL read their stars.

#### Scenario: Segment view
- **WHEN** any viewer, the starring athlete included, gets a segment from `feed.getSegment`
- **THEN** the view holds no star state and no star count
