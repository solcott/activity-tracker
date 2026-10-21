# Spec Delta

## Purpose

Defines candidate segments staged in the AppView before they are published: who may stage and read them, how they are validated and replaced, how distinct riders are counted from segment tracks, and what the AppView reveals about them.

## ADDED Requirements

### Requirement: Only the configured importer may use candidate calls
The AppView SHALL accept the candidate calls only from the account its configuration names as the segment importer, authenticated like any other caller. It SHALL refuse every other caller, and refuse every caller when no importer is configured.

#### Scenario: Importer stages
- **WHEN** the configured importer account calls `importer.putSegmentCandidates`
- **THEN** the AppView accepts the call

#### Scenario: Athlete tries
- **WHEN** a signed-in athlete calls `importer.getRiddenSegmentCandidates`
- **THEN** the AppView refuses it as forbidden

#### Scenario: No importer configured
- **WHEN** the AppView has no importer configured and any account calls a candidate call
- **THEN** the AppView refuses it

### Requirement: Candidates are staged by region in batches
`importer.putSegmentCandidates` SHALL take a region id, an import revision and up to 500 candidates, each with an id, sport group, name, line, source and licence, and store or replace each by its id. It SHALL validate each candidate as segments are validated and return the ids it rejected, with why, while storing the rest.

#### Scenario: Batch with one bad line
- **WHEN** the importer stages 500 candidates for `us-vermont`, one of whose lines is 120 m long
- **THEN** the AppView stores 499 and returns the short one's id as rejected

#### Scenario: Too many
- **WHEN** the importer sends 501 candidates in one call
- **THEN** the AppView refuses the call as `InvalidRequest`

### Requirement: An import run replaces a region's candidates
`importer.finishSegmentCandidates` SHALL take a region id and an import revision, and remove every candidate of that region that wasn't staged in that revision, with its riders. Candidates of other regions SHALL be untouched.

#### Scenario: Trail removed from OSM
- **WHEN** a new import run for `us-vermont` no longer stages a candidate it staged before, and the importer finishes the run
- **THEN** that candidate and its riders are gone, and `us-vermont`'s other candidates remain

### Requirement: Candidates stay out of segment features
A staged candidate SHALL NOT appear in any segment query, get efforts, count toward or against a segment's status, or be reported as a duplicate by `feed.checkSegment`.

#### Scenario: Searching the area
- **WHEN** an athlete searches segments over a trail that has staged candidates and no segments
- **THEN** they get no segments

#### Scenario: Checking a line over a candidate
- **WHEN** an athlete checks a new segment line that follows a staged candidate
- **THEN** the AppView reports no duplicate

### Requirement: Riders are counted from usable segment tracks
The AppView SHALL count a candidate's riders as the distinct athletes with a usable segment track, of an activity in the candidate's sport group, that passes the candidate from start to end by the same test as a segment effort. Tracks of every visibility SHALL count. A newly staged candidate, or one whose line or sport group changed, SHALL be counted against existing tracks.

#### Scenario: Past rides count
- **WHEN** the importer stages a candidate over a climb that three consenting athletes rode last month
- **THEN** once the matcher has run, the candidate has three riders

#### Scenario: Wrong sport group
- **WHEN** athletes run a trail that has only a `ride` candidate
- **THEN** their runs don't count as riders of it

#### Scenario: Partial pass
- **WHEN** an athlete's ride covers only the first half of a candidate
- **THEN** they don't count as a rider of it

### Requirement: A candidate becomes ridden at the configured threshold
A candidate SHALL become ridden once it has at least the configured number of distinct riders, 3 by default, and SHALL stay ridden when riders are later dropped. Changing a candidate's line or sport group SHALL make it unridden and count it again.

#### Scenario: Third rider
- **WHEN** a candidate with two riders gets a third athlete's pass
- **THEN** it becomes ridden

#### Scenario: Riders leave
- **WHEN** a ridden candidate's riders turn matching off
- **THEN** their rider records are dropped, and the candidate stays ridden

### Requirement: The importer learns only which candidates are ridden
`importer.getRiddenSegmentCandidates` SHALL list a region's ridden candidates, by id with a cursor, each with its id, sport group, name, line, source and licence. The AppView SHALL NOT reveal to anyone how many riders a candidate has, who they are, or when they rode.

#### Scenario: Listing ridden candidates
- **WHEN** the importer lists `us-vermont`'s ridden candidates
- **THEN** it gets each ridden candidate's line and name, and no counts, athletes or dates
