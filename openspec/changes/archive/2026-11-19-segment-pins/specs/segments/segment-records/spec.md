## MODIFIED Requirements

### Requirement: Editing or deleting a segment updates it everywhere
Re-writing a segment record SHALL replace its indexed version. Deleting it SHALL remove the segment, its efforts, proposals, votes and name history, except what a handoff keeps (`segments/segment-handoffs`), but SHALL keep its pins (`segments/segment-pins`), which apply again when its URI is indexed again. Changing its line or sport group SHALL recompute its efforts. A new name SHALL change only its original name (`segments/segment-names`).

#### Scenario: Segment deleted
- **WHEN** a segment's creator deletes its record
- **THEN** the AppView serves neither the segment nor any effort on it, nor any of its name proposals or history

#### Scenario: Active segment deleted
- **WHEN** the creator of an active segment with an adopted name deletes its record
- **THEN** the AppView serves neither it nor its efforts, and holds a handoff with its line, names and history until the importer republishes it

#### Scenario: Line changed
- **WHEN** a segment's creator moves its end further along the trail
- **THEN** its efforts are recomputed, and passes that no longer reach the new end lose their efforts

#### Scenario: Renamed after a community name
- **WHEN** a segment's current name is an adopted "Wiggly Woods" and its creator renames the record
- **THEN** its original name changes and its current name stays "Wiggly Woods"

#### Scenario: Pinned segment deleted and written again
- **WHEN** a segment pinned `archived` is deleted and its record is later written again under the same URI
- **THEN** the AppView indexes it as `archived`

### Requirement: Every segment has a status
The AppView SHALL give each indexed segment one status: `proposed`, `active`, `duplicate` or `archived`. It SHALL derive the status and never read it from the record, unless an operator pinned it (`segments/segment-pins`): then the pinned status SHALL be its status. A new segment SHALL start `proposed`, except the segment importer's (see "The segment importer's segments start active"). It SHALL serve the status in every segment view, and for a `duplicate`, the URI of the segment it duplicates.

#### Scenario: New segment
- **WHEN** the AppView indexes a new segment from an athlete's repo that duplicates nothing
- **THEN** its view shows the status `proposed`

#### Scenario: Status in the record ignored
- **WHEN** a `feed.segment` record in an athlete's repo carries a field claiming it is `active`
- **THEN** the AppView ignores it and the segment starts `proposed`

#### Scenario: Pinned status
- **WHEN** an operator has pinned a proposed segment `active`
- **THEN** its view shows the status `active`

### Requirement: A proposed segment becomes active once ridden
A `proposed` segment without a status pin SHALL become `active` once efforts on it come from at least a configured number of distinct athletes, 3 by default, its creator included. Efforts of every visibility SHALL count, but only the status SHALL be served, never who counted. An `active` segment SHALL stay `active` when efforts are later removed.

#### Scenario: Third athlete
- **WHEN** a proposed segment has efforts from its creator and one other athlete, and a third athlete's run matches it
- **THEN** the segment becomes `active`

#### Scenario: Private efforts count
- **WHEN** the third athlete's effort is on an Only me activity
- **THEN** the segment still becomes `active`, and no one else learns whose effort counted

#### Scenario: Athletes leave
- **WHEN** an active segment's athletes later turn matching off and their efforts are removed
- **THEN** the segment stays `active`

### Requirement: Unridden segments are archived
A segment without a status pin still `proposed` 180 days after the AppView first indexed it SHALL become `archived`. An archived segment, whether archived this way or pinned `archived`, SHALL keep its existing efforts and SHALL be served by URI, but SHALL get no new efforts and SHALL NOT appear in search.

#### Scenario: Never ridden
- **WHEN** a segment created 181 days ago has efforts from only its creator
- **THEN** it becomes `archived` and stops appearing in search

#### Scenario: Pinned active, never ridden
- **WHEN** a segment created 181 days ago with efforts from only its creator is pinned `active`
- **THEN** it stays `active`

### Requirement: Duplicates are detected on indexing
On indexing a new or changed segment, the AppView SHALL mark it `duplicate` of the earliest-indexed `proposed` or `active` segment of its sport group and kind whose line it duplicates, and not match it. One line SHALL duplicate another when they run the same direction, their lengths differ by at most 20%, and they're never more than 30 m apart from start to end. A status pin in effect SHALL keep its status. Its efforts SHALL still be recomputed, and unpinning SHALL start from what was derived.

#### Scenario: Same climb written straight to a PDS
- **WHEN** a client writes a ride segment over an existing ride segment's climb, with endpoints 10 m off
- **THEN** the AppView marks the new one `duplicate` of the existing one, and it gets no efforts

#### Scenario: Opposite direction
- **WHEN** a segment covers an existing segment's climb as a descent
- **THEN** it isn't a duplicate

#### Scenario: Different sport group
- **WHEN** a run segment follows an existing ride segment's line
- **THEN** it isn't a duplicate

#### Scenario: Longer stretch
- **WHEN** a segment covers an existing 1 km segment plus 2 km more of the trail
- **THEN** it isn't a duplicate

#### Scenario: Run beside a lift
- **WHEN** an `alpine` run follows an existing lift's line in the same direction
- **THEN** it isn't a duplicate

#### Scenario: Pinned segment re-lined onto another
- **WHEN** a segment pinned `active` is re-lined so it duplicates an earlier active segment
- **THEN** it stays `active` and its efforts are recomputed against its new line

### Requirement: Duplicates are re-checked when their original changes
When a segment that others duplicate is deleted, archived (by the status pass or by a pin), pinned `duplicate`, or changed so they no longer duplicate it, the AppView SHALL check each of those duplicates again, earliest-indexed first. One that duplicates nothing else SHALL become `proposed` and be matched against past segment tracks. Earliest-indexed SHALL mean first indexed, not last changed. A duplicate whose status is pinned SHALL NOT be re-checked while its pin applies.

#### Scenario: Original deleted
- **WHEN** the creator of a segment that one other segment duplicates deletes theirs
- **THEN** the other segment becomes `proposed`, and past activities that pass it get their efforts

#### Scenario: Original pinned archived
- **WHEN** an operator pins `archived` a segment that one other segment duplicates
- **THEN** the other segment becomes `proposed`, and past activities that pass it get their efforts

#### Scenario: Pinned duplicate kept
- **WHEN** segment A is pinned `duplicate` of B, and B is then pinned `archived`
- **THEN** A stays `duplicate` of B
