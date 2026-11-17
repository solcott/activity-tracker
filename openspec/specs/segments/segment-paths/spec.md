# segment-paths Specification

## Purpose

Trails and roads as public paths that segments lie on, so athletes find a place by its trail or road and then choose among the segments along it.

## Requirements

### Requirement: A path is a public importer record
A `feed.path` record SHALL hold a name, a kind (`trail` or `road`), one or more line pieces as encoded polylines, a source, a licence, an optional attribution and optionally the sport groups it is open to. The AppView SHALL index paths only from the segment importer's repo and ignore a path in any other repo or in any space.

#### Scenario: Importer's trail
- **WHEN** the segment importer publishes a `feed.path` of kind `trail` named "Long Trail"
- **THEN** the AppView indexes it

#### Scenario: Athlete's path
- **WHEN** an athlete publishes a `feed.path` in their own repo
- **THEN** the AppView doesn't index it

### Requirement: A path isn't bound by segment lengths
A path SHALL be indexed whatever its length, as long as each line piece holds at least two positions and the pieces together stay within the record's limits. A path whose kind the AppView doesn't know, or with a blank name, SHALL NOT be indexed.

#### Scenario: Long trail
- **WHEN** the importer publishes a 440 km trail as four line pieces
- **THEN** the AppView indexes it as one path

#### Scenario: Unknown kind
- **WHEN** a path's kind is `river`
- **THEN** the AppView doesn't index it

### Requirement: A segment lies on a path when much of it follows the path
A live run segment SHALL lie on a path when the stretches of its line that follow the path, within the corridor duplicates use, add up to at least a quarter of its length or 1 km, whichever is shorter. A stretch SHALL count only for the paths nearest it, within 5 m of the nearest. Direction SHALL NOT matter. A segment MAY lie on several paths. Lifts SHALL lie on none.

#### Scenario: Stretch of a trail
- **WHEN** a 1.2 km segment follows Ann's for its whole length
- **THEN** it lies on Ann's

#### Scenario: Two trails combined
- **WHEN** an athlete's 4 km segment follows Ann's for 1.5 km and then Hogback for 2.5 km
- **THEN** it lies on both Ann's and Hogback

#### Scenario: Clip at a junction
- **WHEN** a 3 km road segment follows a crossing trail for 40 m
- **THEN** it doesn't lie on that trail

#### Scenario: Parallel trail
- **WHEN** a segment follows Hog Hollow Road, which runs 20 m from Achtung Baby for a kilometre
- **THEN** it lies on Hog Hollow Road and not on Achtung Baby

#### Scenario: Shared tread
- **WHEN** two trails follow the same ways for a stretch and a segment follows that stretch
- **THEN** it lies on both

#### Scenario: Reverse direction
- **WHEN** a westbound segment follows a path drawn eastbound
- **THEN** it lies on the path

### Requirement: A segment lies only on paths open to its sport
When a path names the sport groups it is open to, a segment of any other sport group SHALL NOT lie on it, nor count when choosing the nearest path.

#### Scenario: Foot path
- **WHEN** a ride segment runs beside Hoof N Boot Path, open to run and hike only
- **THEN** it doesn't lie on Hoof N Boot Path

### Requirement: Path membership follows segments and paths
When a segment is indexed, re-lined, deleted or changes status, and when a path is indexed, re-lined or deleted, the AppView SHALL work out again which paths each nearby segment lies on. Segments indexed before paths existed SHALL be given their paths.

#### Scenario: New path
- **WHEN** the importer publishes a road path under three existing athlete segments
- **THEN** all three lie on it

#### Scenario: Segment re-lined
- **WHEN** a segment on Ann's is re-lined onto Hogback
- **THEN** it lies on Hogback and no longer on Ann's

### Requirement: Paths don't change what a segment is
Lying on a path SHALL NOT change a segment's status, duplicate checks, efforts, leaderboards, Mayors, names, matching or segment group.

#### Scenario: Segment on two paths
- **WHEN** a segment lies on two paths
- **THEN** it keeps one leaderboard and one status

### Requirement: Segment views carry their paths
Every segment view the AppView serves SHALL list the URIs of the paths the segment lies on, empty when it lies on none.

#### Scenario: Combined segment
- **WHEN** a viewer gets a segment that lies on Ann's and Hogback
- **THEN** its view lists both paths

### Requirement: A path's view says what it covers for the viewer
A path view SHALL carry the path's URI, name, kind, length, line pieces, source, licence and attribution, and, counting only the segments on it that the viewer could get from a segment search: their sport groups in the app's order and how many segment groups they form.

#### Scenario: Multi-use trail
- **WHEN** a viewer gets the view of Ann's, which holds ride, run and hike segments in 3 segment groups
- **THEN** it reads Ride, Run, Hike and 3 segment groups

#### Scenario: Proposed segment someone else made
- **WHEN** a path's only segment is proposed by someone else and the viewer has no effort on it
- **THEN** the view lists no sport groups and no segment groups

### Requirement: A path can be looked up with its segments
`feed.getPath` SHALL return a path's view and every segment on it that the viewer could get from a segment search, as segment views. It SHALL answer `NotFound` for a path that isn't indexed. `feed.getPaths` SHALL return the views of up to 25 given paths, leaving out those not indexed.

#### Scenario: Opening a path
- **WHEN** a viewer gets Ann's, which holds its whole-trail segments and two stretches, each for three sports both ways
- **THEN** the AppView returns Ann's and its 18 segment views

#### Scenario: Unknown path
- **WHEN** a viewer gets a path URI the AppView hasn't indexed
- **THEN** it answers `NotFound`

### Requirement: Paths and loose segments can be found by name
`feed.searchPathNames` SHALL list, a page at a time, the paths with a segment the viewer could get from a segment search whose name or whose segments' current names contain the query, ignoring case. It SHALL also list the segment groups whose matching segments lie on no path.

#### Scenario: Trail by its name
- **WHEN** a viewer searches "ann" and Ann's has segments the viewer may see
- **THEN** Ann's is listed once, as a path

#### Scenario: Renamed stretch
- **WHEN** a segment on Hogback has adopted the name "Wiggly Woods"
- **THEN** a search for "wiggly" lists Hogback

#### Scenario: Road segment on no path
- **WHEN** a viewer searches "sprint" and an athlete's "Pine St sprint" lies on no path
- **THEN** its segment group is listed as an item of its own

#### Scenario: Path with no visible segments
- **WHEN** a path's name matches but none of its segments may be seen by the viewer
- **THEN** it isn't listed

### Requirement: Path name results are ranked and paged as items
`feed.searchPathNames` SHALL rank each item by its best match among the path's name and its segments' names: the whole name, then a name starting with the query, then a word starting with it, then the rest. Within a rank, nearest first to a given centre, else by name. The limit and cursor SHALL count items, and following them SHALL return each item once.

#### Scenario: Prefix first
- **WHEN** a viewer searches "hog" and the paths "Hogback" and "Big Hog Loop" match
- **THEN** "Hogback" comes before "Big Hog Loop"

#### Scenario: Paging
- **WHEN** a viewer searches with a limit of 2 and five items match
- **THEN** following the cursors returns all five, each once

### Requirement: Path name search can be limited to an area and a sport group
`feed.searchPathNames` SHALL take an optional bounding box, listing only items with a matching segment whose line crosses it, under the same 1 degree limit as `feed.searchSegments`, and an optional sport group, counting only segments of that group.

#### Scenario: Sport group
- **WHEN** a viewer searches "ann" for `run` and Ann's has only ride segments
- **THEN** Ann's isn't listed

#### Scenario: Box too large
- **WHEN** a viewer searches in a box 3 degrees wide
- **THEN** the AppView answers `InvalidRequest`

### Requirement: The importer learns the segments in an area
`importer.getSegmentLines` SHALL return, a page at a time, the URI, sport group and line of each live, public run segment crossing a box of at most 1 degree. Only the segment importer's account SHALL call it.

#### Scenario: Athlete road segment
- **WHEN** the importer asks for a box holding an athlete's segment on a street
- **THEN** the segment is returned with its line and sport group

#### Scenario: Another caller
- **WHEN** an athlete calls `importer.getSegmentLines`
- **THEN** the AppView refuses the call
