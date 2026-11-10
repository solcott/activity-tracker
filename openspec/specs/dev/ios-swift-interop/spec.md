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
- **WHEN** Swift code calls a Kotlin entry point, such as the one that returns a screen's view controller
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

### Requirement: A choice among an enum's values reaches Swift as objects
Where a native view needs a list of a Kotlin enum's values, such as the choices a control offers, the contract SHALL offer it as an immutable list of objects that each hold one value as a property, and SHALL NOT offer a list of the enum itself, which Swift export can't carry. Swift SHALL read each object's value as the enum's own Swift type.

#### Scenario: The grouping choices
- **WHEN** Swift reads the choices Segments & groups offers for grouping
- **THEN** it has three objects, in order, whose values are Off, Connected and Anyone as the Swift enum, each with its label

#### Scenario: A list of enum values
- **WHEN** a contract needs to offer Swift a list of a Kotlin enum's values
- **THEN** it offers objects holding each value, never a list of the enum

### Requirement: A paged list reaches Swift as its own types
Swift SHALL read a state's paged list's items as objects of the list's own item type, in order, and its loading statuses as their Swift types, without the contract changing its paged state for Swift's sake. A set of account ids in a state SHALL read as Swift's type for an id.

#### Scenario: The blocked accounts
- **WHEN** Swift reads the loaded blocked list's sample
- **THEN** it has two accounts, bob's and carol's, each with its name, handle and id

#### Scenario: Accounts being changed
- **WHEN** Swift reads the accounts whose unblock is being written while bob's is
- **THEN** it has bob's id

### Requirement: A held photo's bytes reach Swift
Swift SHALL be able to ask Kotlin for the bytes of a photo the signed-in session holds by its `heldphoto:` URL, and SHALL get none for another URL or when signed out.

#### Scenario: Signed out
- **WHEN** Swift asks for a held photo while no one is signed in
- **THEN** it gets none

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
