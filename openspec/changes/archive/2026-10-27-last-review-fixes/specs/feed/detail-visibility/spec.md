# Spec Delta

## ADDED Requirements

### Requirement: Streams data that can't be fetched isn't fetched on every view
When the AppView can't get a streams record's data, it SHALL serve the viewer the other parts as it does now, and remember the failure. Data that isn't valid streams data, or is too large, SHALL never be fetched again under the same content id. Data that was missing, or whose host couldn't be reached, SHALL be fetched again only after a pause that grows with each failure, up to one hour. A later successful fetch SHALL be served and SHALL clear the failure.

#### Scenario: PDS down
- **WHEN** the PDS holding a Public ride's streams data is unreachable and ten viewers open the ride within a minute
- **THEN** the AppView tries the PDS once, each viewer gets the ride without that part, and the AppView tries again only after the pause

#### Scenario: PDS back
- **WHEN** the PDS is reachable again and a viewer opens the ride after the pause
- **THEN** the AppView fetches the data and serves the ride's streams, and later views use the held data

#### Scenario: Undecodable data
- **WHEN** a streams record names data that isn't valid streams data
- **THEN** the AppView fetches it once, and every later view leaves that part out without fetching it

### Requirement: Streams data is fetched once, and parts together
The AppView SHALL fetch the streams data of an activity's parts at the same time, not one after another. Several views asking at once for the same data the AppView doesn't hold SHALL cause one fetch. Sharing a fetch SHALL NOT serve a part to a viewer its tier doesn't admit.

#### Scenario: Two tiers fetched together
- **WHEN** a follower opens a ride with Public and Followers streams records, neither held yet
- **THEN** the AppView fetches both at the same time

#### Scenario: Two viewers at once
- **WHEN** two viewers open the same newly published ride at the same moment
- **THEN** the AppView fetches its streams data once, and both get it

#### Scenario: Shared fetch and a stranger
- **WHEN** a stranger opens a ride while its Followers data is being fetched for a follower
- **THEN** the stranger gets only the ride's Public streams
