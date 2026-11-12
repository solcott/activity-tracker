# Spec Delta

## ADDED Requirements

### Requirement: Accept invite is native on iOS
On iOS accepting an invite SHALL be a native page under the system's navigation bar: the invite's summary, its route as the line
preview, the intro and overlap warning, the copy's title, the native visibility choices, and Decline (when the session can) and Accept,
held while either runs. An invite that is gone SHALL say so.

#### Scenario: Accepting
- **WHEN** alice opens Bob's invite to his ride on iOS, keeps the title and taps Accept
- **THEN** her copy is published with the visibility she chose, and the page goes back

#### Scenario: A gone invite
- **WHEN** alice opens an invite Bob cancelled
- **THEN** the native page says the invite is no longer available
