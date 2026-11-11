## ADDED Requirements

### Requirement: Swift follows a sub-screen's states
Swift code drawing a screen whose state embeds a sub-screen SHALL be able to ask the host for that sub-screen, passing the screen object the state holds and a closure for its outer events, and SHALL be told of each new state of it as the sub-screen's own state type. Its events SHALL go to that state's event sink, and its outer events SHALL reach the closure as the sub-screen's own outer event types, so Swift can hand them to the screen's events as the shared UI does.

#### Scenario: The feed's filter bar from Swift
- **WHEN** a Swift view asks a shown feed record for its filter bar and sends it a date range
- **THEN** the view receives a filter bar state with that range, and its closure receives a filter-changed outer event with that filter

#### Scenario: A release build
- **WHEN** the app is built in its release configuration
- **THEN** the host's sub-screen lookup and the filter bar's types are exported with no stub
