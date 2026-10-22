# Spec Delta

## MODIFIED Requirements

### Requirement: The importer learns only which candidates are ridden
`importer.getRiddenSegmentCandidates` SHALL list a region's ridden candidates, by id with a cursor, each with its id, sport group, name, line, source and licence. It SHALL leave out a candidate once the AppView has indexed a segment from the importer account keyed by that candidate's id. The AppView SHALL NOT reveal to anyone how many riders a candidate has, who they are, or when they rode.

#### Scenario: Listing ridden candidates
- **WHEN** the importer lists `us-vermont`'s ridden candidates
- **THEN** it gets each ridden candidate's line and name, and no counts, athletes or dates

#### Scenario: Published candidate
- **WHEN** the AppView has indexed the importer's segment for one of `us-vermont`'s ridden candidates
- **THEN** the listing leaves that candidate out, and still lists the region's other ridden candidates

#### Scenario: Same key from another account
- **WHEN** an athlete's segment happens to be keyed by a ridden candidate's id
- **THEN** the listing still includes that candidate
