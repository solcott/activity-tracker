## ADDED Requirements

### Requirement: A finished export is written for Swift to share
Swift SHALL be able to ask Kotlin to write a finished export's archive to a file Swift names, streamed rather than held in memory, and SHALL hear once it has been written or has failed. Swift SHALL NOT need a file type from a module that isn't exported to do so.

#### Scenario: Written
- **WHEN** Swift asks for a ready export's archive to be written into the app's cache
- **THEN** it hears that the zip was written, and the file there is the archive

#### Scenario: Failed
- **WHEN** writing the archive fails
- **THEN** Swift hears that it failed, and no partial file is offered

### Requirement: A set of enums reaches Swift as the text it stands for
Where a native view needs to show the members of a set of Kotlin enum values, the contract SHALL offer the text for each member, in the order shared code chooses. It SHALL NOT expect Swift to read the set itself, which Swift export can't carry.

#### Scenario: An outdated grant's features
- **WHEN** Swift reads what an outdated sign-in is missing
- **THEN** it has one text per missing feature, in the order the shared page lists them

### Requirement: A moment in time reaches Swift as a value it can format
Where a native view shows a moment from a contract, Swift SHALL be able to read it as a value its own date formatting takes, without a time type from a module that isn't exported.

#### Scenario: A leave's pause
- **WHEN** Swift reads until when a paused leave waits
- **THEN** it has that moment, to the second, as a Swift date
