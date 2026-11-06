# segments/segment-handoffs Specification

## Purpose

Keeps a segment that other athletes ride on the map after its creator deletes it, by handing it to the segment importer to republish, with its names, and pointing the old URI at its successor.

## Requirements

### Requirement: Deleting an active segment stages a handoff
When the AppView removes an `active` segment that isn't the importer's, whether its record was deleted or its creator's account was, it SHALL keep a handoff holding the segment's sport group, kind, line, current and original names and name history, and the old URI. It SHALL keep nothing about who rode it or when. A `proposed`, `duplicate` or `archived` segment, an importer segment, or one whose record carries `!takedown`, SHALL get no handoff.

#### Scenario: Creator deletes an active segment
- **WHEN** alice deletes her active segment "Hogback Climb"
- **THEN** the AppView removes it and its efforts, and holds a handoff with its line and names

#### Scenario: Account deleted at the PDS
- **WHEN** bob's account is deleted at his PDS, and he created one active and one proposed segment
- **THEN** the AppView holds a handoff for the active one only

#### Scenario: Importer segment deleted
- **WHEN** an importer segment is removed
- **THEN** no handoff is kept

#### Scenario: Taken-down segment deleted
- **WHEN** the creator deletes an active segment labelled `!takedown`
- **THEN** no handoff is kept

#### Scenario: Original comes back
- **WHEN** a deleted segment's record is written again under the same URI while its handoff is still pending
- **THEN** the segment is indexed as usual and is its own handoff's successor, so the importer doesn't republish it

#### Scenario: Create and delete replayed
- **WHEN** the AppView replays an active segment's create and then its delete after its handoff was staged
- **THEN** the handoff is pending again with its adopted names, as before the replay

### Requirement: A live duplicate takes over a handoff
When the AppView checks a removed segment's duplicates again and one becomes live, it SHALL make that segment the handoff's successor instead of waiting for the importer.

#### Scenario: Athlete's copy takes over
- **WHEN** carol's segment was `duplicate` of alice's, and alice deletes hers
- **THEN** carol's becomes `proposed` and is matched, and it is the successor of alice's handoff

### Requirement: The importer lists pending handoffs
`importer.getHandoffs` SHALL list handoffs that have no successor yet and that no live segment duplicates, by id with a cursor, each with its kind (segment or site) and the data needed to republish it: for a segment, its sport group, kind, line and original name. It SHALL make a live segment that duplicates a pending handoff its successor. It SHALL reveal nothing about riders or the old creator, and SHALL refuse every caller but the configured importer.

#### Scenario: Covered by a live segment
- **WHEN** a pending handoff's line is duplicated by an athlete's active segment
- **THEN** the listing leaves it out, and the athlete's segment is its successor

#### Scenario: Listing
- **WHEN** the importer calls `importer.getHandoffs`
- **THEN** it gets each pending handoff's id, line, sport group and name, and no athletes, counts or dates

#### Scenario: Athlete calls
- **WHEN** a signed-in athlete calls `importer.getHandoffs`
- **THEN** the AppView refuses it as forbidden

### Requirement: The importer republishes handoffs
The importer's `handoff` command SHALL publish each segment handoff `importer.getHandoffs` lists as a `feed.segment` in its repo keyed by the handoff id, with the handoff's sport group, kind, line and original name, `source` `athlete`, `license` `CC0-1.0` and no attribution. It SHALL never write twice under one id, and SHALL report one already published as `publish` does. If the republished segment is indexed as `duplicate`, the segment it duplicates SHALL be the successor.

#### Scenario: Republished
- **WHEN** the operator runs `handoff` with alice's "Hogback Climb" pending
- **THEN** the importer's repo holds a `feed.segment` under that handoff id with the same line, `source` `athlete`, `license` `CC0-1.0` and no attribution

#### Scenario: Run twice
- **WHEN** the operator runs `handoff` again
- **THEN** nothing new is written

### Requirement: The republished segment picks up where it left off
When the AppView indexes the importer's segment keyed by a handoff id, it SHALL make it that handoff's successor, give it the handoff's current name and name history, and match it against stored segment tracks so efforts, leaderboards and Mayors boards are rebuilt from tracks still usable.

#### Scenario: Names carried
- **WHEN** alice's segment, renamed "Wiggly Woods" by adoption, is republished
- **THEN** the new segment's current name is "Wiggly Woods" and its history lists the adoption

#### Scenario: Efforts back
- **WHEN** the republished segment is indexed and the matcher has run
- **THEN** riders who still have usable tracks over it have their efforts back, and alice, whose tracks are gone, has none

### Requirement: An old URI resolves to its successor
`feed.getSegment` for a handed-off segment's old URI SHALL return its successor's view, under the successor's URI, once it has one, and `NotFound` until then.

#### Scenario: Old link
- **WHEN** a viewer opens a link to alice's deleted segment after its republication
- **THEN** they get the republished segment's view

#### Scenario: Not yet republished
- **WHEN** a viewer asks for the old URI before the importer has run
- **THEN** the AppView answers `NotFound`

### Requirement: Stars follow the successor
When the app finds that a starred segment's view has a different URI, it SHALL replace the star with one for the successor.

#### Scenario: Star moved
- **WHEN** dave starred alice's segment and opens his starred list after its republication
- **THEN** the list shows the republished segment, and his `self` space holds a star for it and none for the old URI
