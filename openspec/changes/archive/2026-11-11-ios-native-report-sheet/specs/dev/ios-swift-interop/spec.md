# Spec Delta

## ADDED Requirements

### Requirement: A native page reaches its record for a sheet's sub-screen
A view inside a native page SHALL be able to reach the page's host record without the page passing it down, so a sheet modifier can ask the host for its sub-screen. Outside the host, as in a preview or snapshot, there SHALL be no record, and such a sheet SHALL show nothing.

#### Scenario: The profile's report sheet
- **WHEN** alice taps Report on bob's native profile
- **THEN** the sheet asks the profile's record for the report sub-screen and draws its states

### Requirement: A report is opened without Swift building a moderation target
Kotlin SHALL build the event that opens a report sheet for a native page, so Swift never builds a `:core-data` report target.

#### Scenario: A release build
- **WHEN** the app is built in its release configuration
- **THEN** the report sheet's, the name sheet's and the profile's report types are exported with no stub
