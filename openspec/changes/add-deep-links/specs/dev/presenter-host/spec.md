# Spec Delta

## ADDED Requirements

### Requirement: The host opens links on its stacks
The host SHALL open a link while its session is open. It SHALL choose the link's tab without returning that tab to its first screen. It SHALL keep that tab's stack up to the first screen of the link's chain when that screen is already in the stack, and push the rest of the chain. A link that arrives before the session is open SHALL wait until it opens. The Compose shell SHALL apply links by the same rule.

#### Scenario: Link to another tab
- **WHEN** the Search tab is shown and a link to the viewer's Settings page opens
- **THEN** the You tab is chosen, and its stack is the profile with Settings on top

#### Scenario: Link waiting for a session
- **WHEN** a link arrives while the host is signed out and an account then signs in
- **THEN** the link's page is on top of its tab once the session is open

#### Scenario: Chain already in the stack
- **WHEN** a link to a segment's leaderboard opens while the segment's page is on top of the Feed tab
- **THEN** the Feed tab's stack is the feed, the segment and its leaderboard
