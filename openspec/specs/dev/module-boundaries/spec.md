# dev/module-boundaries Specification

## Purpose

Divides the client's UI code into shared modules and per-feature modules with enforced dependency rules. Features stay isolated from each other, presenters and UIs stay apart, and code several features use has one shared home.

## Requirements

### Requirement: Feature module kinds
Every feature SHALL be split into three modules:
- an API module: its screen keys, states and events;
- a presenter module;
- a UI module.

No shared module and no app module SHALL hold a feature's screens, states, events, presenters or UIs. Each module's kind SHALL be recognizable from its name alone.

#### Scenario: Moved feature
- **WHEN** a feature has been moved into feature modules
- **THEN** its screens, states and events are in its API module, its presenters in its presenter module and its UIs in its UI module, and each module's name ends in `-api`, `-presenter` or `-ui`

#### Scenario: A new screen
- **WHEN** a screen is added to the app
- **THEN** it is added to a feature's API, presenter and UI modules, not to a shared module or the app module

### Requirement: Presenters and UIs never depend on each other
The build SHALL fail when a presenter module depends, directly or through another project module, on a UI module, the shared UI modules or the shared resources. The build SHALL also fail when a UI module depends on a presenter module or the shared presenter module.

#### Scenario: Presenter depends on a UI module
- **WHEN** a presenter module's build script adds a dependency on any `-ui` module
- **THEN** `./gradlew check` fails and names both modules

#### Scenario: UI depends on a presenter module
- **WHEN** a UI module's build script adds a dependency on any `-presenter` module
- **THEN** `./gradlew check` fails and names both modules

### Requirement: Features meet only through API modules
A feature's modules SHALL depend on another feature only through that feature's API module. An API module SHALL depend only on the shared contracts, other API modules and the data layer.

#### Scenario: Presenter navigates to another feature
- **WHEN** a feature's presenter opens another feature's screen
- **THEN** it depends only on that feature's API module, and the build passes

#### Scenario: Reaching into another feature's presenter
- **WHEN** a feature's presenter module depends on another feature's presenter or UI module
- **THEN** `./gradlew check` fails and names both modules

#### Scenario: API module depends on an implementation
- **WHEN** an API module depends on any presenter or UI module
- **THEN** `./gradlew check` fails and names both modules

### Requirement: Shared code has one home
Code used by more than one feature SHALL live in exactly one shared module for its layer:
- contracts in the shared contracts module;
- presenter helpers in the shared presenter module;
- design-system components in the design-system module;
- domain widgets in the shared UI module;
- strings and drawables in the shared resources module.

It SHALL NOT be copied into features.

#### Scenario: Two features use the same widget
- **WHEN** two features' UIs render the same widget
- **THEN** both import it from the shared UI module, and no copy of it exists in either feature

#### Scenario: Strings
- **WHEN** any UI module or the app module reads a localized string
- **THEN** it reads it from the shared resources module

### Requirement: Moved screens stay covered
Moving a screen into feature modules SHALL NOT drop it from the screenshot coverage check or the content-loading conformance test.

#### Scenario: Screen without a screenshot after the move
- **WHEN** a UI in a feature UI module has no screenshot test file
- **THEN** the screenshot coverage check fails and names the UI

#### Scenario: Loading screen in a feature module
- **WHEN** a state in a feature API module holds content that loads
- **THEN** the content-loading conformance test checks its samples

### Requirement: Moving code does not change the app
Moving code between modules SHALL NOT change what any screen shows or how it behaves.

#### Scenario: Screenshots after the move
- **WHEN** the screenshot tests run after a move
- **THEN** every reference screenshot matches without being updated
