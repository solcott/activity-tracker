# Spec Delta

## MODIFIED Requirements

### Requirement: Name results are ranked by match, then distance
`feed.searchSegmentNames` SHALL list segment groups (segments/segment-groups), each ranked by its best-matching listed member: the whole name, then names starting with the query, then a word starting with it, then the rest; within a rank, nearest first to a given centre, else by name. A page SHALL hold together every member of its segment groups the search would serve, matching or not. The limit and cursor SHALL count segment groups, returning each matching one once.

#### Scenario: Prefix before anywhere
- **WHEN** a viewer searches "hog" and segments "Hogback Climb" and "Big Hog Loop" and "Shoghill" match
- **THEN** "Hogback Climb" comes first, then "Big Hog Loop", then "Shoghill"

#### Scenario: Nearest within a rank
- **WHEN** two segments both named "Summit Trail" match and a centre is given near the second
- **THEN** the second comes first

#### Scenario: One route, six segments
- **WHEN** a viewer searches "ann's" with a limit of 2 and the segment group of Ann's holds ride, run and hike segments each way
- **THEN** the first page holds all six of Ann's segments together and the next matching segment group's segments, and Ann's isn't returned again on later pages

#### Scenario: Renamed member
- **WHEN** one member of a segment group has adopted the name "Wiggly Woods" and the others are still "Trail 214"
- **THEN** a search for "wiggly" returns the whole segment group, ranked by "Wiggly Woods"
