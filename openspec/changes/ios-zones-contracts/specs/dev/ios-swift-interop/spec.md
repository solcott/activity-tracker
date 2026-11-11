# Spec Delta

## MODIFIED Requirements

### Requirement: A choice among an enum's values reaches Swift as objects
Where a native view needs a list of a Kotlin enum's values, such as the choices a control offers or the kinds of zones a page shows, the contract SHALL offer it as an immutable list of objects that each hold one value as a property, and SHALL NOT offer a list or a set of the enum itself, which Swift export can't carry. Swift SHALL read each object's value as the enum's own Swift type.

#### Scenario: The grouping choices
- **WHEN** Swift reads the choices Segments & groups offers for grouping
- **THEN** it has three objects, in order, whose values are Off, Connected and Anyone as the Swift enum, each with its label

#### Scenario: The zone kinds
- **WHEN** Swift reads the kinds of zones the default zones and a running override's sample offer
- **THEN** it has heart rate and power for the defaults, and heart rate and speed for running, in that order, as the Swift enum

#### Scenario: A list of enum values
- **WHEN** a contract needs to offer Swift a list of a Kotlin enum's values
- **THEN** it offers objects holding each value, never a list or a set of the enum

## ADDED Requirements

### Requirement: A collection's elements from another module reach Swift as their own type
When a contract's immutable collection holds objects of a class from a module that reaches Swift only through the contracts' signatures, Swift SHALL read each element as that class, with its properties, without the contract changing the collection for Swift's sake.

#### Scenario: The privacy zones
- **WHEN** Swift reads the loaded Privacy zones sample's zones
- **THEN** it has one privacy zone, labelled "Home", with its 400 m radius and its centre

#### Scenario: The computed zones
- **WHEN** Swift reads the default zones sample's heart rate zones
- **THEN** it has each zone, in order, with its label, its name as the Swift enum and its bounds, the last one open-ended

### Requirement: A part of a list reaches Swift as a typed array
When a contract offers part of one of its immutable lists, such as the rows a collapsed section shows, Swift SHALL read it as an array of the element's type whether the part is some of the list or all of it, and reading it SHALL NOT stop the app.

#### Scenario: Every row shown
- **WHEN** Swift reads the rows a threshold's history shows with every row shown
- **THEN** it has every row, newest first

#### Scenario: The newest rows shown
- **WHEN** Swift reads the rows a threshold's history shows while collapsed
- **THEN** it has the newest rows, at most three
