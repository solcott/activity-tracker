# Spec Delta

## ADDED Requirements

### Requirement: Swift reads a contract's immutable collections as typed arrays
A screen contract SHALL be free to hold a list or a set as an immutable collection, and SHALL NOT change that type for Swift's sake. Swift code SHALL read such a field of text, whole numbers or objects as an array of the element's own type, in the collection's order, through one shared helper, so no native view casts elements one at a time. An element that isn't of the type the view named SHALL stop a debug build where it is read rather than be dropped unnoticed.

#### Scenario: A list of text
- **WHEN** Swift reads a state's immutable list of sport tokens holding `run` and `ride`
- **THEN** it has the Swift strings `run` and `ride`, in that order

#### Scenario: A list of numbers
- **WHEN** Swift reads a state's immutable list of the distances 0, 200, 400 and 800
- **THEN** it has those four numbers as Swift integers, in that order

#### Scenario: A list of a contract's objects
- **WHEN** Swift reads a threshold history group's immutable list holding one threshold with two rows
- **THEN** it has one threshold object, and reads two rows from it the same way

#### Scenario: An empty list
- **WHEN** Swift reads an immutable list that holds nothing
- **THEN** it has an empty array
