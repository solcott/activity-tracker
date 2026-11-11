# Spec Delta

## ADDED Requirements

### Requirement: The gear editors read the same in both UIs
The gear and collection forms' titles, the shown kind, the sports and default-for summaries, the starting distance's label, the photo button, the mounted-on and retired lines, each field's maximum length, and when a form's fields can be used, its kind is offered, its management shows and its Add items is offered, SHALL be chosen by shared code that both UIs ask.

#### Scenario: Sports joined
- **WHEN** an item is for Ride and Run
- **THEN** both UIs read "Ride, Run" for its sports

#### Scenario: No sports
- **WHEN** an item is for no sport
- **THEN** both UIs read "Any sport" for its sports

#### Scenario: While saving
- **WHEN** the gear form is saving
- **THEN** neither UI lets its fields, choices or buttons be used

#### Scenario: A kind this client doesn't know
- **WHEN** an item's kind isn't among its type's kinds
- **THEN** both UIs show "None" as its kind

### Requirement: The type sheets group the same in both UIs
Which sports and activity types the gear form's sheets offer, by category and in what order, and which types show under their sport as its sub-sports, SHALL be chosen by shared code that both UIs and the other type pickers ask.

#### Scenario: A sub-sport under its sport
- **WHEN** the default-for sheet lists the cycling category
- **THEN** Gravel Ride shows under Ride, not as a row of its own

#### Scenario: Only sports for gear
- **WHEN** the sports sheet lists the cycling category
- **THEN** it offers Ride and no sub-sport of it
