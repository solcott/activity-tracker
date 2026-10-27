# Spec Delta

## MODIFIED Requirements

### Requirement: Segments can be found by area
`feed.searchSegments` SHALL return the indexed segments whose line passes through a bounding box, optionally only those of one sport group, at most 100 per page with a cursor. It SHALL return `active` segments to every viewer, and a `proposed` segment only to its creator and to athletes who have an effort on it. It SHALL never return `duplicate` or `archived` segments. A bounding box wider than 1 degree on either side SHALL be refused as `InvalidRequest`. One request SHALL examine at most a fixed number of segments, whoever asks and however many segments lie near the box. When it stops before filling a page, it SHALL return the segments it found, which may be fewer than asked for or none, with a cursor that continues the search. A page without a cursor SHALL mean the search is complete, and following the cursors SHALL return every matching segment exactly once.

#### Scenario: Map area
- **WHEN** a viewer searches a 5 km box around a trail centre for `ride` segments
- **THEN** they get the active ride segments whose lines cross that box and no run segments

#### Scenario: Proposed segment
- **WHEN** a proposed segment lies in the box
- **THEN** its creator and athletes with an effort on it get it, and other viewers don't

#### Scenario: Box too large
- **WHEN** a viewer searches a box 3 degrees wide
- **THEN** the AppView answers `InvalidRequest`

#### Scenario: Many segments near the box but not through it
- **WHEN** more segments than one request examines have a bounding box overlapping the search box but a line that misses it, and one segment indexed after them crosses the box
- **THEN** the first response has no segments and a cursor, and following the cursors returns the crossing segment and then a page without a cursor
