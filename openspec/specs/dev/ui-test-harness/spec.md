# dev/ui-test-harness Specification

## Purpose

Lets developers render any screen's UI in isolation from a sample state, deterministically and without a session, network or local stack, so UI tests, previews and screenshot tests are cheap to write and stable to run.

## Requirements

### Requirement: A screen renders from its state alone
Every screen's UI SHALL render from a state value and the harness's provided values alone. It SHALL NOT need a signed-in session, dependency graph, network access, local stack or platform permission.

#### Scenario: Rendering the feed offline
- **WHEN** a JVM test renders the feed's UI from a loaded sample state inside the harness, with no network
- **THEN** the feed's activities show and nothing tries to sign in or fetch

#### Scenario: A screen with a sub-screen
- **WHEN** a test renders the feed's UI, which embeds the feed filter sub-screen, inside the harness
- **THEN** it renders without a session's sub-circuit and without an error

### Requirement: The harness chooses look, theme, units and size
The harness SHALL let a test or preview choose:
- the platform look (Android, desktop or web)
- light or dark theme
- the unit system
- the window width class (compact, medium or expanded)

The UI SHALL render with the choices made.

#### Scenario: Dark theme
- **WHEN** a screen renders in the harness with dark theme chosen, on a machine set to light mode
- **THEN** it uses the app's dark colors

#### Scenario: Imperial units
- **WHEN** the activity page renders with imperial units chosen
- **THEN** distances show in miles and elevation in feet

#### Scenario: Expanded width
- **WHEN** a screen that lays out by window width renders at the expanded width
- **THEN** it shows its expanded layout

### Requirement: Time is provided, not read from the system
A UI that shows or computes from the current time, time zone or 12/24-hour clock SHALL take them from values the harness can set. With the same state and the same values, it SHALL render the same on any machine, on any date, in any time zone.

#### Scenario: Age on a profile
- **WHEN** a profile with a birth date renders with the current time set to a fixed instant
- **THEN** the age shown is computed from that instant, whatever the machine's date

#### Scenario: Start time clock style
- **WHEN** an activity's start time renders once with the 24-hour clock set and once with the 12-hour clock set
- **THEN** it shows "14:05" and then "2:05 PM", whatever the machine's locale

#### Scenario: Time zone
- **WHEN** the data sources section renders a source with no device name, with the time zone set to UTC, on a machine in another zone
- **THEN** the source is named by its start time in UTC

### Requirement: Maps and images render offline
Under inspection (tests, previews and screenshot tools), maps SHALL draw a local placeholder for the route without loading remote styles or tiles. Images SHALL draw a placeholder the harness provides, without a network request.

#### Scenario: Route map in a test
- **WHEN** the activity page renders a route map in the harness with no network
- **THEN** a placeholder showing the route draws in the map's place, and no style or tile request is made

#### Scenario: Avatar in a test
- **WHEN** a profile with an avatar URL renders in the harness
- **THEN** the harness's placeholder image draws, and no image is fetched

### Requirement: Sample states for every screen
Each screen SHALL have named sample states that tests and previews share. They SHALL cover:
- loaded
- each ui/content-loading state the screen has: first load, empty, first-load failure, refreshing, refresh failure
- for a form, an invalid input

A sample's event sink SHALL accept and ignore every event.

#### Scenario: Feed samples
- **WHEN** a test asks for the feed's samples
- **THEN** it gets loading, empty, failed, loaded, refreshing and refresh-failed feed states, each renderable in the harness

#### Scenario: Building a lexicon view
- **WHEN** a test builds a segment view, changing only its source and licence
- **THEN** every other field has a valid default, and the result renders on the segment screen

### Requirement: Shared loading pieces carry stable anchors
The shared pieces SHALL each expose a stable test anchor that does not depend on the displayed text or language:
- the first-load progress indicator
- the load error and its retry action
- the refresh indicator and refresh-failure report
- a paged list's footer: progress, error, retry and end
- the top bar's back action

#### Scenario: Finding retry without its text
- **WHEN** a test renders a first-load failure on any screen in the group the ui/content-loading spec names
- **THEN** it finds the retry action by its anchor, whatever the error's wording

### Requirement: Content-loading conformance is checked across screens
A single test SHALL check every screen that loads content against the ui/content-loading requirements, using the screens' sample states and the shared anchors:
- progress before the first answer
- empty shown as empty
- retry on first-load failure
- content kept while refreshing and after a failed refresh

A screen added without the samples this needs SHALL fail the test.

#### Scenario: Content kept on refresh failure
- **WHEN** the conformance test renders a screen's refresh-failure sample
- **THEN** the loaded content is shown, the refresh failure is reported, and no first-load error is shown

#### Scenario: A new screen without samples
- **WHEN** a screen that loads content is added without its content-loading samples
- **THEN** the conformance test fails, naming the screen

### Requirement: UI code does not read the system clock
The build's checks SHALL flag UI code that reads the system clock or the system time zone directly.

#### Scenario: Clock.System in a UI
- **WHEN** a composable in a UI file calls the system clock for the current time
- **THEN** `./gradlew check` reports it and names the value to read instead
