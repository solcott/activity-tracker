# Spec Delta

## MODIFIED Requirements

### Requirement: Features meet only through API modules
A feature's modules SHALL depend on another feature only through that feature's API module. An API module SHALL depend only on the shared contracts, other API modules, the data layer and the text field state module.

#### Scenario: Presenter navigates to another feature
- **WHEN** a feature's presenter opens another feature's screen
- **THEN** it depends only on that feature's API module, and the build passes

#### Scenario: Reaching into another feature's presenter
- **WHEN** a feature's presenter module depends on another feature's presenter or UI module
- **THEN** `./gradlew check` fails and names both modules

#### Scenario: API module depends on an implementation
- **WHEN** an API module depends on any presenter or UI module
- **THEN** `./gradlew check` fails and names both modules

#### Scenario: A state holds a text field
- **WHEN** a state in an API module holds a text field's state
- **THEN** its type comes from the text field state module, and the build passes
