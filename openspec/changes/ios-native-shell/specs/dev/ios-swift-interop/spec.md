# Spec Delta

## ADDED Requirements

### Requirement: Swift follows a screen's states
Swift code SHALL be told of each new state of a screen it is showing, without asking repeatedly, and SHALL stop being told once it stops showing that screen. A state SHALL arrive as the screen's own state type, so a view reads its fields without casting each one.

#### Scenario: A new state
- **WHEN** the sign-in screen's status changes to waiting for the browser while a Swift view shows it
- **THEN** the view receives a sign-in state whose status is waiting for the browser

#### Scenario: The view goes away
- **WHEN** the Swift view showing a screen is removed
- **THEN** it receives no further states of that screen

### Requirement: Swift follows text a presenter changes
A Swift text field bound to a state's text field SHALL show text the presenter sets, clears or replaces without the athlete acting, and SHALL NOT have its cursor moved by text the athlete typed coming back from the presenter.

#### Scenario: The presenter clears the text
- **WHEN** a presenter clears a field after a comment is sent
- **THEN** the Swift field shows empty

#### Scenario: The presenter replaces the field
- **WHEN** a session expires and the sign-in screen's presenter fills in that account's handle
- **THEN** the Swift field shows the handle

#### Scenario: Typing mid-text
- **WHEN** an athlete places the cursor in the middle of the text and types three characters
- **THEN** the characters go in at the cursor, which ends after them, and the presenter reads the edited text

#### Scenario: A rule on the field
- **WHEN** an athlete pastes "12kg" into a Swift field limited to digits
- **THEN** the field and the presenter both hold the text the field had before the paste

## MODIFIED Requirements

### Requirement: One Swift export build
The iOS app's Swift code SHALL reach shared Kotlin code through a single Swift export build that holds all the Kotlin code the app links. The app SHALL NOT also link an Objective-C framework built from the project's Kotlin.

#### Scenario: Building from Xcode
- **WHEN** the app is built from Xcode or with `xcodebuild`
- **THEN** the build runs the Gradle Swift export task, the Swift sources compile against the exported Swift modules, and no Objective-C framework of the project's Kotlin is linked

#### Scenario: Entry points only
- **WHEN** Swift code calls a Kotlin entry point, such as the one that returns a screen's view controller
- **THEN** every type in that entry point's signature is one the Swift export build exposes, so the call can't reach a stub that stops the app

### Requirement: No declaration Swift can reach is a stub
The iOS build SHALL fail when a declaration in a contract module, the text field state module, the presenter host or the Swift-facing entry points is generated as a stub that stops the app when called. Declarations Swift never calls MAY be listed as exceptions, each with its reason. The failure SHALL name the declaration.

#### Scenario: A hidden module's type in a state
- **WHEN** a state gains a field whose type comes from a module the export hides
- **THEN** the iOS build fails and names the state's stubbed declaration

#### Scenario: A hidden module's type in the host
- **WHEN** a type of the presenter host that Swift reads gains a member whose type comes from a module the export hides
- **THEN** the iOS build fails and names the stubbed member

#### Scenario: A listed exception
- **WHEN** a contract class's generated serializer accessor is a stub and is listed as never called from Swift
- **THEN** the iOS build passes

#### Scenario: Stubs in other modules
- **WHEN** a data-layer class that a contract names has a stubbed member
- **THEN** the iOS build passes and the build's report lists the stub
