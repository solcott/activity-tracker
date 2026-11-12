# Spec Delta

## ADDED Requirements

### Requirement: Every screen has a native view
Every screen the iOS app can show (each serializable screen of a screen contract) SHALL have a native view registered in the app's
screen registry, and `./gradlew check` SHALL fail naming any screen that has none.

#### Scenario: A new screen without its view
- **WHEN** a feature adds a screen to its contract and registers no native view for it
- **THEN** `./gradlew check` fails and names the screen
