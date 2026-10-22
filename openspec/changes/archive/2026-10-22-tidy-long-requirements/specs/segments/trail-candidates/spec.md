# Spec Delta

## MODIFIED Requirements

### Requirement: Candidate ids are stable
A candidate's id SHALL be derived only from its sport group, its direction, the source features it follows in order, its ends, and how often it passed its last end before ending there. Ids SHALL be unique within a candidates file. Extracting the same data twice SHALL give the same ids, and an edit elsewhere SHALL NOT change a candidate's id.

#### Scenario: Re-extract unchanged
- **WHEN** an operator extracts the same region from a newer extract in which only other trails changed
- **THEN** every unchanged candidate keeps its id

#### Scenario: Loop both ways
- **WHEN** a loop mapped as one closed way gives a candidate each way round
- **THEN** the two candidates have different ids

#### Scenario: Trail rerouted
- **WHEN** a trail's middle way is replaced by a new way
- **THEN** the candidates that followed it get new ids

#### Scenario: Agency feature reshaped
- **WHEN** a newer Forest Service dataset moves a trail feature's interior vertices but keeps its feature id and its ends
- **THEN** the candidates that follow it keep their ids

## ADDED Requirements

### Requirement: What an id's features and ends are, by source
For OSM data, the features a candidate's id follows SHALL be its ways and its ends SHALL be nodes. For an agency dataset, the features SHALL be the dataset's stable feature ids and the ends their positions to about a metre.

#### Scenario: OSM way split
- **WHEN** an OSM trail way a candidate follows is split into two ways
- **THEN** the candidate gets a new id, since the ways it follows changed

#### Scenario: Agency feature given a new id
- **WHEN** a newer agency dataset gives a trail a new feature id but keeps its line and ends
- **THEN** the candidates that follow it get new ids
