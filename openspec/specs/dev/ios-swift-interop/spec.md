# dev/ios-swift-interop Specification

## Purpose

Says which iOS versions the app supports and how the iOS app's Swift code and the shared Kotlin code call each other, so both sides can rely on one bridge.

## Requirements

### Requirement: Supported iOS versions
The iOS app SHALL install and run on iOS 18.0 and later, and SHALL NOT be installable on an earlier version.

#### Scenario: Current device
- **WHEN** the app is installed on a device or simulator running iOS 18.0 or later
- **THEN** it installs, launches and shows the sign-in screen or the signed-in app

#### Scenario: Older device
- **WHEN** someone tries to install the app on a device running iOS 17
- **THEN** the system refuses the install

### Requirement: One Swift export build
The iOS app's Swift code SHALL reach shared Kotlin code through a single Swift export build that holds all the Kotlin code the app links. The app SHALL NOT also link an Objective-C framework built from the project's Kotlin.

#### Scenario: Building from Xcode
- **WHEN** the app is built from Xcode or with `xcodebuild`
- **THEN** the build runs the Gradle Swift export task, the Swift sources compile against the exported Swift modules, and no Objective-C framework of the project's Kotlin is linked

#### Scenario: Entry points only
- **WHEN** Swift code calls a Kotlin entry point, such as the one that returns the app's root view controller
- **THEN** every type in that entry point's signature is one the Swift export build exposes, so the call can't reach a stub that stops the app

### Requirement: Kotlin calls Swift through an exported interface
Kotlin SHALL be able to call Swift code the app supplies as an implementation of an exported Kotlin interface, and a failure that code reports SHALL reach Kotlin as the failure the interface declares.

#### Scenario: Importing a FIT file
- **WHEN** an athlete imports a valid FIT file on iOS
- **THEN** the Swift FIT decoder decodes it and the import shows the same activity it showed before this change

#### Scenario: Unreadable FIT file
- **WHEN** an athlete imports a file that isn't a FIT file, or one that is truncated
- **THEN** the import fails with the unreadable-file error and the app keeps running

#### Scenario: Background work
- **WHEN** iOS runs the app's background upload, import-save or data-export task
- **THEN** the task has the Swift FIT decoder it was registered with and runs as it did before this change

### Requirement: Screen contracts are exported to Swift
The Swift export build SHALL include every screen contract: the shared contracts and each feature's API module. The app's Swift code SHALL be able to read any screen's state, switch over its sealed types and send any of its events. A feature API module added later SHALL be exported without a step of its own.

#### Scenario: Swift reads a state and sends an event
- **WHEN** Swift code in `iosApp` reads a field of a screen's state and calls its event sink with one of the screen's events
- **THEN** the iOS app builds

#### Scenario: A new feature
- **WHEN** a new feature's API module is added to the build
- **THEN** its screens, states and events are in the next Swift export build with no change to the export configuration

#### Scenario: An event Swift can't build
- **WHEN** an event's only constructor takes an immutable collection, so the export gives it no Swift initialiser
- **THEN** the iOS build fails and names the event

#### Scenario: Nothing changes for the athlete
- **WHEN** the app is built with every contract exported and run on any platform
- **THEN** every screen looks and behaves as it did before

### Requirement: Contracts hold nothing Swift export rejects
`./gradlew check` SHALL fail when a contract module names a Compose type other than the stability annotations, declares a property or parameter named `self`, or types a field or parameter as a `Pair` or `Triple`. The failure SHALL name the file and line.

#### Scenario: A Compose type in a state
- **WHEN** a state in a feature's API module gains a field of a Compose type
- **THEN** `./gradlew check` fails and names the file and line

#### Scenario: A property named self
- **WHEN** a class in a contract module declares a property named `self`
- **THEN** `./gradlew check` fails and names the file and line

#### Scenario: A pair in a state
- **WHEN** a state gains a field typed `Pair<Double, Double>`
- **THEN** `./gradlew check` fails and names the file and line

#### Scenario: Stability annotations
- **WHEN** a state is annotated `@Immutable` or `@Stable`
- **THEN** the check passes

### Requirement: No declaration Swift can reach is a stub
The iOS build SHALL fail when a declaration in a contract module, the text field state module or the Swift-facing entry points is generated as a stub that stops the app when called. Declarations Swift never calls MAY be listed as exceptions, each with its reason. The failure SHALL name the declaration.

#### Scenario: A hidden module's type in a state
- **WHEN** a state gains a field whose type comes from a module the export hides
- **THEN** the iOS build fails and names the state's stubbed declaration

#### Scenario: A listed exception
- **WHEN** a contract class's generated serializer accessor is a stub and is listed as never called from Swift
- **THEN** the iOS build passes

#### Scenario: Stubs in other modules
- **WHEN** a data-layer class that a contract names has a stubbed member
- **THEN** the iOS build passes and the build's report lists the stub

### Requirement: Text field state is a type Swift reads and writes
A screen's state SHALL hold each text field's state as a type that is exported to Swift, from which Swift reads the field's text and sets it. A presenter SHALL see text Swift set, and Swift SHALL see text a presenter set or cleared. On Android, desktop and web the type SHALL be the Compose text field state itself.

#### Scenario: Swift sets the text
- **WHEN** Swift sets a field's text to "alice.test"
- **THEN** the presenter holding that field reads "alice.test"

#### Scenario: The presenter clears the text
- **WHEN** a presenter clears a field after a comment is sent
- **THEN** Swift reads an empty string from that field

#### Scenario: Compose fields on iOS
- **WHEN** an athlete types in, selects in, or pastes into a text field of the Compose UI on iOS, then leaves the app and returns
- **THEN** the cursor, the selection and the restored text behave as they did before this change

#### Scenario: Other platforms
- **WHEN** a presenter test on the JVM edits a state's text field with the Compose text field API
- **THEN** it compiles and passes unchanged
