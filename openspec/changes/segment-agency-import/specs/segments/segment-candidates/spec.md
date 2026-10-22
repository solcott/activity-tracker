# Spec Delta

## MODIFIED Requirements

### Requirement: Candidates are staged by region in batches
`importer.putSegmentCandidates` SHALL take a region id, an import revision and up to 500 candidates, each with an id, sport group, name, line, source and licence, and optionally an attribution, and store or replace each by its id. It SHALL validate each candidate as segments are validated, and an attribution as blank or at most 100 graphemes, and return the ids it rejected, with why, while storing the rest.

#### Scenario: Batch with one bad line
- **WHEN** the importer stages 500 candidates for `us-vermont`, one of whose lines is 120 m long
- **THEN** the AppView stores 499 and returns the short one's id as rejected

#### Scenario: Too many
- **WHEN** the importer sends 501 candidates in one call
- **THEN** the AppView refuses the call as `InvalidRequest`

#### Scenario: Agency candidate staged
- **WHEN** the importer stages a candidate with `source` `agency`, `license` `CC0-1.0` and the attribution "USDA Forest Service"
- **THEN** the AppView stores it with its attribution

#### Scenario: Attribution too long
- **WHEN** the importer stages a candidate whose attribution is 150 graphemes long
- **THEN** the AppView returns its id as rejected and stores the rest of the batch

### Requirement: The importer learns only which candidates are ridden
`importer.getRiddenSegmentCandidates` SHALL list a region's ridden candidates, by id with a cursor, each with its id, sport group, name, line, source, licence and attribution, if it has one. It SHALL leave out a candidate once the AppView has indexed a segment from the importer account keyed by that candidate's id. The AppView SHALL NOT reveal to anyone how many riders a candidate has, who they are, or when they rode.

#### Scenario: Listing ridden candidates
- **WHEN** the importer lists `us-vermont`'s ridden candidates
- **THEN** it gets each ridden candidate's line and name, and no counts, athletes or dates

#### Scenario: Ridden agency candidate
- **WHEN** a ridden candidate was staged with the attribution "National Park Service"
- **THEN** the listing gives that attribution with it

#### Scenario: Published candidate
- **WHEN** the AppView has indexed the importer's segment for one of `us-vermont`'s ridden candidates
- **THEN** the listing leaves that candidate out, and still lists the region's other ridden candidates

#### Scenario: Same key from another account
- **WHEN** an athlete's segment happens to be keyed by a ridden candidate's id
- **THEN** the listing still includes that candidate
