# Spec Delta

## ADDED Requirements

### Requirement: Remembered streams fetch failures record why
A remembered failure to get a streams record's data SHALL record why and when it last failed: the fetch error, a content id mismatch, data too large, or data that isn't valid streams data. It SHALL NOT record any of the fetched bytes or a message quoting them.

#### Scenario: Unreachable host
- **WHEN** fetching a ride's streams data fails because its PDS can't be reached
- **THEN** the remembered failure records the connection error and its time

#### Scenario: Not streams data
- **WHEN** a streams record names data that isn't valid streams data
- **THEN** the remembered failure says it isn't streams data, and contains nothing of the data
