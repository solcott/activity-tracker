# Spec Delta

## ADDED Requirements

### Requirement: The presenter host sees only contracts
The presenter host module SHALL depend only on the shared contracts, feature API modules, the data layer and the text field state module, never on a presenter module or a UI module. It receives presenters from the app's dependency graph, not from a module it depends on.

#### Scenario: The host opens a screen
- **WHEN** the host names a feature's screen, such as a tab's first screen
- **THEN** it depends only on that feature's API module, and the build passes

#### Scenario: The host depends on a UI module
- **WHEN** the presenter host module depends on any UI module, or on the design-system or resources module
- **THEN** `./gradlew check` fails and names both modules

#### Scenario: The host depends on a presenter module
- **WHEN** the presenter host module depends on a feature's presenter module or the shared presenter module
- **THEN** `./gradlew check` fails and names both modules
