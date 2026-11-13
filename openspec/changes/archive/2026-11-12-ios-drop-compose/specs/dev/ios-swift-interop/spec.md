## MODIFIED Requirements

### Requirement: Text field state is a type Swift reads and writes
A screen's state SHALL hold each text field's state as a type that is exported to Swift, from which Swift reads the field's text and sets it. A presenter SHALL see text Swift set, and Swift SHALL see text a presenter set or cleared. On iOS the type SHALL hold the text itself, with no Compose text state inside it, and a presenter's remembered field SHALL come back with its text when the screen's saved state is restored. On Android, desktop and web the type SHALL be the Compose text field state itself.

#### Scenario: Swift sets the text
- **WHEN** Swift sets a field's text to "alice.test"
- **THEN** the presenter holding that field reads "alice.test"

#### Scenario: The presenter clears the text
- **WHEN** a presenter clears a field after a comment is sent
- **THEN** Swift reads an empty string from that field

#### Scenario: Restored on iOS
- **WHEN** a presenter's remembered field holds "Morning ride" and its screen's saved state is restored on iOS
- **THEN** the field holds "Morning ride"

#### Scenario: Compose fields on iOS
- **WHEN** the iOS app is built
- **THEN** no Compose text field is in it, and only native fields edit a state's text field state

#### Scenario: Other platforms
- **WHEN** a presenter test on the JVM edits a state's text field with the Compose text field API
- **THEN** it compiles and passes unchanged
