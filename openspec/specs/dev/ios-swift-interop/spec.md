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

### Requirement: A loaded list reaches Swift as its own types
When a state's loaded content is itself a list of contract objects, Swift SHALL read it as objects of that type, in order, with each property under a name Swift accepts, without the contract changing its content for Swift's sake.

#### Scenario: The ignored devices
- **WHEN** Swift reads the loaded Ignored devices sample
- **THEN** it has two devices, the Garmin Edge 840 with its serial's end "2071" and the Polar H10 marked as known only by its model

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

### Requirement: A natively picked photo reaches its presenter as shared code prepares it
A photo the iOS app picks natively SHALL be handed to shared code, which SHALL re-encode, scale and strip it exactly as it does for the shared UI's pick, and the result SHALL reach the screen's presenter through the event the shared UI sends, with no contract change for iOS.

#### Scenario: A large photo
- **WHEN** Swift hands shared code a 4,032 × 3,024 photo
- **THEN** the presenter receives a WebP or JPEG photo no more than 2,048 pixels on its long edge and under 1.5 MB, with its width and height

#### Scenario: Not a picture
- **WHEN** the bytes picked can't be read as a picture
- **THEN** nothing reaches the presenter and the form is as it was

### Requirement: A calendar date crosses between Swift and Kotlin unchanged
A calendar date a contract holds SHALL reach a native date control as the same day, and a day chosen there SHALL reach the presenter as that day, in any time zone the device is in.

#### Scenario: Acquired on the first of July
- **WHEN** an item was acquired on 1 July 2026 and the device is in a time zone west of UTC
- **THEN** the native calendar shows 1 July 2026, and choosing 2 July sends 2 July 2026

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

### Requirement: A natively picked profile photo reaches its presenter as shared code prepares it
A profile photo the iOS app picks natively SHALL be handed to shared code, which SHALL scale and re-encode it exactly as it does for the shared UI's pick, and the result SHALL reach the presenter through the event the shared UI sends, with no contract change for iOS.

#### Scenario: A large photo
- **WHEN** Swift hands shared code a 2,000 × 1,500 photo
- **THEN** the presenter receives a JPEG no more than 512 pixels on its long edge

#### Scenario: A HEIC
- **WHEN** the photo picked is a HEIC
- **THEN** the presenter receives it as a JPEG, as for any other photo

#### Scenario: Not a picture
- **WHEN** the bytes picked can't be read as a picture
- **THEN** nothing reaches the presenter and the form is as it was

### Requirement: A date held as text crosses to Swift as the same day
A calendar date a contract holds as `YYYY-MM-DD` text SHALL reach a native date control as that day, a day chosen there SHALL reach the presenter as that day's text, and empty or malformed text SHALL reach Swift as no date, in any time zone the device is in.

#### Scenario: A birthdate west of UTC
- **WHEN** alice's birthdate is 1990-04-12 and the device is in a time zone west of UTC
- **THEN** the native calendar shows 12 April 1990, and choosing 13 April sends "1990-04-13"

### Requirement: A choice among an enum's members reaches Swift one member at a time
Where a contract holds a list of an enum's members that a native view needs (which detail groups are now hidden), the native view SHALL ask a shared condition per member rather than read the list, since such a list can't cross to Swift.

#### Scenario: Now hidden, read per group
- **WHEN** the Location group is now hidden
- **THEN** the native view learns it by asking about Location, and reads no list of groups

### Requirement: A route's shape reaches Swift as placed points
Shared code SHALL place a route's served polyline and breaks on a tile of the size Swift gives, smoothed as the shared line preview smooths it, and Swift SHALL read the result as typed arrays of points for each piece of line, the start and end, and each break's stop and resume, without decoding the polyline itself.

#### Scenario: A route with a break
- **WHEN** Swift asks for the shape of a two-piece route on a 300 by 160 point tile
- **THEN** it reads two pieces of points inside the tile, a start, an end and one break

#### Scenario: Too short to draw
- **WHEN** Swift asks for the shape of a polyline with one point
- **THEN** it reads no shape

### Requirement: A time of day can carry the activity's clock
A time of day chosen in shared code SHALL be able to carry a UTC offset, and Swift SHALL format such a time in that offset rather than the device's time zone, on the device's 12- or 24-hour clock.

#### Scenario: A start at UTC-7
- **WHEN** Swift formats 16:00 UTC carrying an offset of -420 minutes on a 12-hour clock
- **THEN** it reads "9:00 AM", whatever the device's zone

### Requirement: Swift follows a sub-screen's states
Swift code drawing a screen whose state embeds a sub-screen SHALL be able to ask the host for that sub-screen, passing the screen object the state holds and a closure for its outer events, and SHALL be told of each new state of it as the sub-screen's own state type. Its events SHALL go to that state's event sink, and its outer events SHALL reach the closure as the sub-screen's own outer event types, so Swift can hand them to the screen's events as the shared UI does.

#### Scenario: The feed's filter bar from Swift
- **WHEN** a Swift view asks a shown feed record for its filter bar and sends it a date range
- **THEN** the view receives a filter bar state with that range, and its closure receives a filter-changed outer event with that filter

#### Scenario: A release build
- **WHEN** the app is built in its release configuration
- **THEN** the host's sub-screen lookup and the filter bar's types are exported with no stub

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
