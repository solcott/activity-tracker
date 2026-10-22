# Spec Delta

## MODIFIED Requirements

### Requirement: A published segment carries its candidate's data
A segment the importer publishes SHALL be a `feed.segment` in the service account's repo, with the candidate's sport group, name, line, source and licence, and its attribution when it has one, keyed by the candidate's id. It SHALL be indexed, matched and given a status like any other segment.

#### Scenario: Segment written
- **WHEN** `publish` publishes a ridden Vermont ride candidate named "Hogback Trail"
- **THEN** the service account's repo holds a `feed.segment` under the candidate's id, named "Hogback Trail", with sport `ride`, the candidate's line, `source` `osm` and `license` `ODbL-1.0`

#### Scenario: Agency segment written
- **WHEN** `publish` publishes a ridden candidate from the Forest Service's trails
- **THEN** its `feed.segment` has `source` `agency`, `license` `CC0-1.0` and the attribution "USDA Forest Service"

#### Scenario: Turns active from past rides
- **WHEN** the AppView indexes a segment published from a candidate that three athletes rode
- **THEN** once the matcher has run, those athletes have efforts on it and it is `active`
