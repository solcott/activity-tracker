## MODIFIED Requirements

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity`, `feed.activityStreams` and `feed.activityZones` records. From `interactions` spaces it SHALL index only `feed.like`, `feed.comment` and `feed.commentHide` records. It SHALL NOT store `actor.privateDetails`, `graph.followDecline` or `actor.activityDefaults` records from any space.

#### Scenario: Private details ignored
- **WHEN** a synced space's ops include an `actor.privateDetails` write
- **THEN** the AppView stores nothing from it

#### Scenario: Zones indexed from a tier space
- **WHEN** a synced `mutuals` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes the zones at Mutuals

#### Scenario: Zones in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes no zones from it

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Comment in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.comment` write
- **THEN** the AppView indexes no comment from it

#### Scenario: Comment hide in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.commentHide` write
- **THEN** the AppView indexes no hide from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it
