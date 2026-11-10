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
