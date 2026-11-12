# Spec Delta

## ADDED Requirements

### Requirement: The gear picker is native on iOS
On iOS the edit and manual entry forms SHALL show the gear as a row naming the chosen items, or None, that opens a sheet of the athlete's items to tick, each with its kind or type and who may see it, marked retired when it is; with Add collection when the athlete has collections, Clear when any is chosen, how many of a collection's items the limit left out, and a note when the athlete has no gear.

#### Scenario: Adding a collection
- **WHEN** alice taps Add collection and picks her "Race day" collection of three items
- **THEN** its items are ticked, and the row names them
