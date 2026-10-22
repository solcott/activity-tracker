# Spec Delta

## MODIFIED Requirements

### Requirement: Segment creation is limited per athlete
The AppView SHALL index at most 10 new segments per creator in any 24 hours and SHALL drop any further new segment from that creator in that window. Re-writing an already indexed segment SHALL NOT count. Segments from the account the AppView is configured to accept as the segment importer SHALL NOT be limited, and `feed.checkSegment` SHALL always report that account's allowance as the full 10.

#### Scenario: Eleventh segment
- **WHEN** an athlete's repo gains an eleventh new segment within 24 hours of their first
- **THEN** the AppView doesn't index it

#### Scenario: Renaming
- **WHEN** an athlete who created 10 segments today renames one
- **THEN** the AppView indexes the new name

#### Scenario: Importer publishes a region
- **WHEN** the segment importer's repo gains 200 new segments in an hour
- **THEN** the AppView indexes all 200
