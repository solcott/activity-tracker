## MODIFIED Requirements

### Requirement: Any other refresh failure keeps the session
A refresh that fails for any other reason SHALL keep the session, so that a later request refreshes again. The request that needed the refresh SHALL fail, reported as a problem on the server for a server error and as the server being unreachable when no response came from the authorization server.

#### Scenario: Authorization server unavailable
- **WHEN** a refresh gets a `503` from the authorization server
- **THEN** the athlete stays signed in, the request fails saying the server had a problem, and the next request tries the refresh again

#### Scenario: Rate limited
- **WHEN** a refresh gets a `429`
- **THEN** the athlete stays signed in and the request fails

#### Scenario: Offline
- **WHEN** a refresh can't reach the authorization server
- **THEN** the athlete stays signed in and the request fails saying the server couldn't be reached
