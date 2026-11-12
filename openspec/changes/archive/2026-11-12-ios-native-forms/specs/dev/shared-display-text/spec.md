# Spec Delta

## ADDED Requirements

### Requirement: The activity forms' text is chosen in shared code
What the activity forms say that follows a rule (the gear field's summary, an item's line, a collection's line and the left-out count; the type picker's groups for a query and the sub-sport choices with their labels; manual entry's unit labels; the core-charts warning; the dive site form's radius, its reasons no site can be made and its public note) SHALL be chosen in shared code that both UIs ask.

#### Scenario: A retired item
- **WHEN** alice's retired Bike, a road bike she shows to Followers, is offered on the gear sheet
- **THEN** both UIs give it the same line naming its kind, Followers and Retired

#### Scenario: The radius in imperial
- **WHEN** an athlete who reads feet sets a 100 m radius
- **THEN** both UIs show it in feet the same way
