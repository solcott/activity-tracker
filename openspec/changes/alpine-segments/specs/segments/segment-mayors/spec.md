# Spec Delta

## MODIFIED Requirements

### Requirement: Only active segments have Mayors
`feed.getSegmentMayors` SHALL return a segment's board only while the segment is `active` and of kind `run`. For a segment of any other status, and for a lift, it SHALL return no entries. A segment that isn't indexed or isn't served to the viewer SHALL be `NotFound`.

#### Scenario: Proposed segment
- **WHEN** a viewer asks for the Mayors of a proposed segment that three efforts are on
- **THEN** the AppView returns no entries

#### Scenario: Unknown segment
- **WHEN** a viewer asks for the Mayors of a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

#### Scenario: Active lift
- **WHEN** a viewer asks for the Mayors of an active chairlift
- **THEN** the AppView returns no entries
