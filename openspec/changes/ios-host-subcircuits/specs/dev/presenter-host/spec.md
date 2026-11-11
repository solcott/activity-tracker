## ADDED Requirements

### Requirement: The host runs a screen's sub-screens
The host SHALL run the presenter of a sub-screen that a UI asks a shown screen for, publish each state it produces, deliver events sent to that state's event sink, and pass each outer event it reports to where the UI said. It SHALL run it while its screen's presenter runs and stop it with that one, keeping its last state. Asking again for an equal sub-screen SHALL give the same one; one that isn't equal SHALL start afresh.

#### Scenario: A sub-screen's state and an event
- **WHEN** a UI asks the feed for its filter bar and sends it a date range of the last 30 days
- **THEN** the host publishes the filter bar's first state, then one whose filter is the last 30 days, and the feed hears that the filter changed

#### Scenario: Asked for twice
- **WHEN** a UI asks the feed for its filter bar again while drawing a new state of the feed
- **THEN** it gets the same filter bar, with its filter as the athlete left it

#### Scenario: A replaced sub-screen
- **WHEN** the feed's filters are cleared from outside the bar, which gives the feed a new filter bar screen that isn't equal to the old one
- **THEN** the filter bar the UI asks for next starts from the new screen, with nothing kept from the old one

#### Scenario: A covered screen's sub-screen stops
- **WHEN** an activity page covers the feed
- **THEN** the filter bar's presenter stops with the feed's, and its last state stays readable

#### Scenario: A sub-screen nothing presents
- **WHEN** a UI asks for a sub-screen no presenter is registered for, or for an object that isn't a sub-screen
- **THEN** it fails with an error that names it

### Requirement: A sub-screen keeps its state for as long as its screen
The host SHALL keep what a sub-screen's presenter retained or saved while its screen is covered or another tab is chosen, and give it back when the screen shows again. It SHALL drop that state when its screen leaves its stack, and when the UI closes the sub-screen, as a sheet that is dismissed does.

#### Scenario: Returning to a covered screen
- **WHEN** the athlete narrows the feed to rides, opens an activity and comes back
- **THEN** the filter bar's first state after returning still holds rides

#### Scenario: The screen leaves its stack
- **WHEN** a screen with a sub-screen is popped and the same screen is opened again
- **THEN** its sub-screen starts with nothing kept from the first visit

#### Scenario: Closed by the UI
- **WHEN** the UI closes a sheet's sub-screen and asks for an equal one later
- **THEN** the sub-screen starts with nothing kept from the first time it showed
