# Spec Delta

## MODIFIED Requirements

### Requirement: Editing or deleting a segment updates it everywhere
Re-writing a segment record SHALL replace its indexed version. Deleting it SHALL remove the segment, every effort on it, and its name proposals, their votes and its name history. Changing a segment's line or sport group SHALL recompute its efforts against the new version. Changing its name SHALL change only its original name (`segments/segment-names`).

#### Scenario: Segment deleted
- **WHEN** a segment's creator deletes its record
- **THEN** the AppView serves neither the segment nor any effort on it, nor any of its name proposals or history

#### Scenario: Line changed
- **WHEN** a segment's creator moves its end further along the trail
- **THEN** its efforts are recomputed, and passes that no longer reach the new end lose their efforts

#### Scenario: Renamed after a community name
- **WHEN** a segment's current name is an adopted "Wiggly Woods" and its creator renames the record
- **THEN** its original name changes and its current name stays "Wiggly Woods"

### Requirement: A segment can be looked up by URI
`feed.getSegment` SHALL return a segment's view by its URI, whatever its status: its creator, current and original names, sport group, line, length, source, licence and status, and for a duplicate the segment it duplicates. It SHALL include its name history and open name proposals (`segments/segment-names`). For a signed-in viewer it SHALL also give the viewer's own fastest effort on it, if they have one, whether they may propose a name or vote, and their own votes. A segment that isn't indexed SHALL be `NotFound`.

#### Scenario: Viewer has efforts
- **WHEN** a signed-in athlete with three efforts on a segment opens it
- **THEN** the view includes their fastest effort

#### Scenario: Duplicate
- **WHEN** a viewer asks for a segment marked `duplicate`
- **THEN** the view has the status `duplicate` and the URI of the segment it duplicates

#### Scenario: Unknown segment
- **WHEN** a viewer asks for a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

#### Scenario: Renamed segment
- **WHEN** a viewer asks for a segment whose adopted name is "Wiggly Woods" and whose record is named "Trail 214 Connector"
- **THEN** the view's name is "Wiggly Woods", its original name is "Trail 214 Connector", and its history lists the adoption
