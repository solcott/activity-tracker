# Spec Delta

## ADDED Requirements

### Requirement: A choice among an enum's values reaches Swift as objects
Where a native view needs a list of a Kotlin enum's values, such as the choices a control offers, the contract SHALL offer it as an immutable list of objects that each hold one value as a property, and SHALL NOT offer a list of the enum itself, which Swift export can't carry. Swift SHALL read each object's value as the enum's own Swift type.

#### Scenario: The grouping choices
- **WHEN** Swift reads the choices Segments & groups offers for grouping
- **THEN** it has three objects, in order, whose values are Off, Connected and Anyone as the Swift enum, each with its label

#### Scenario: A list of enum values
- **WHEN** a contract needs to offer Swift a list of a Kotlin enum's values
- **THEN** it offers objects holding each value, never a list of the enum
