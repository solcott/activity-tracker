# Spec Delta

## MODIFIED Requirements

### Requirement: Athlete sees each kind's source
A combined activity's page SHALL show its athlete every kind of data any member recorded. A kind SHALL be included by default. For each included kind it SHALL name the member it comes from, by device or start time. Other viewers SHALL NOT see this section.

#### Scenario: Watch and bike computer
- **WHEN** an athlete opens a ride combining a watch with GPS and heart rate and a bike computer with GPS, heart rate, power and altitude, and opens its combination editor
- **THEN** the data sources section lists map, distance and speed, and heart rate, each with its member and a choice of the watch or the bike computer, and elevation and power from the bike computer, all included

#### Scenario: Follower
- **WHEN** a follower opens the same combined ride
- **THEN** they see no data sources section

## ADDED Requirements

### Requirement: Combination is edited apart from the page
On a combined activity's page, its athlete SHALL see the members and an Edit control, and SHALL NOT see the data sources or any control to remove a member until they choose Edit. Edit SHALL open one editor holding the data sources and removing a member. Other viewers SHALL NOT see the Edit control.

#### Scenario: Page stays compact
- **WHEN** an athlete opens their combined ride of a watch and a bike computer
- **THEN** the page lists both members with an Edit control, and shows no source choices, include switches or remove buttons

#### Scenario: Editing
- **WHEN** the athlete chooses Edit
- **THEN** an editor shows each member with a way to remove it, and each kind of data with its source and whether it is included

#### Scenario: Follower sees no Edit
- **WHEN** a follower opens the same combined ride
- **THEN** they see the members but no Edit control

#### Scenario: Last split closes the editor
- **WHEN** the athlete removes a member from a combined activity of two in the editor and confirms
- **THEN** the activity is split and the editor closes
