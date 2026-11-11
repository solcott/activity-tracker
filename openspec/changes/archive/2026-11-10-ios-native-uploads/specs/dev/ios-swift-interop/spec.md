# Spec Delta

## ADDED Requirements

### Requirement: A model's plain lists reach Swift as typed arrays
When a contract's loaded content is a `:core-data` model whose fields are plain Kotlin lists of other models, Swift SHALL read each as a typed array of that model, in order, an empty one included, and SHALL read the model's sealed fields as their cases, its enums as Swift enums, and a length of time as a value with its whole seconds, without the model changing for Swift's sake.

#### Scenario: The empty queue
- **WHEN** Swift reads the empty upload queue sample
- **THEN** its items, imports, finished items and refused changes read as empty arrays and the app keeps running

#### Scenario: An import part way
- **WHEN** Swift reads the loaded upload queue sample
- **THEN** it has one Strava export import with 26 waiting activities, the first refused, and a refused like

#### Scenario: The time left
- **WHEN** Swift reads the time left of the import-part-way sample
- **THEN** it reads 93,600 seconds
