# dev/presenter-host Specification

## Purpose
Lets a platform run the shared presenters and their navigation without the shared UI, so a native UI can draw the states they produce and send them events.

## Requirements

### Requirement: Presenters run without a UI
The host SHALL run the presenter of a shown screen with no UI attached, publish each state it produces, and deliver events sent to that state's event sink. It SHALL do so in common code that runs on every client platform.

#### Scenario: A state and an event
- **WHEN** the host shows the sign-in screen and a caller sends Submit with a handle typed
- **THEN** the host publishes the sign-in screen's first state, and then a state whose status is starting

#### Scenario: Text set from outside
- **WHEN** a caller sets the text of a text field held in a published state
- **THEN** the presenter reads that text the next time it acts on the field

#### Scenario: A screen with no presenter
- **WHEN** the host is asked to show a screen no presenter is registered for
- **THEN** it fails with an error that names the screen

### Requirement: Presenters navigate the host's stacks
The host SHALL keep a stack of screens for each tab and give each presenter a navigator onto its own tab's stack. Going to a screen SHALL put it on top, and popping SHALL remove the top screen. The first screen of a stack SHALL NOT be removable.

#### Scenario: Opening a screen
- **WHEN** the feed's presenter goes to an activity's page
- **THEN** the feed tab's stack is the feed with the activity page on top, and the other tabs' stacks are unchanged

#### Scenario: Closing a screen
- **WHEN** an activity page's presenter pops
- **THEN** the stack is the feed alone

#### Scenario: Popping the first screen
- **WHEN** a tab's first screen pops
- **THEN** the stack is unchanged

#### Scenario: Going to the screen already on top
- **WHEN** a presenter goes to a screen equal to the one on top
- **THEN** the stack is unchanged

#### Scenario: Several screens closed at once
- **WHEN** the UI reports that the athlete went back two screens in one gesture
- **THEN** the stack loses its top two screens

### Requirement: A covered screen keeps its state
The host SHALL keep what a screen's presenter retained or saved while another screen covers it or another tab is chosen, and SHALL give it back when the screen shows again. It SHALL drop that state when the screen leaves its stack.

#### Scenario: Returning to a covered screen
- **WHEN** the feed has loaded, an activity page opens over it and then closes
- **THEN** the feed's first state after returning holds the loaded activities, with no load in between

#### Scenario: A screen opened again
- **WHEN** an activity page is closed and the same activity is opened again
- **THEN** its presenter starts with nothing retained from the first visit

#### Scenario: Another account
- **WHEN** the athlete signs out and another account signs in
- **THEN** no screen of the new session holds state from the old one

### Requirement: Only shown screens run
The host SHALL run the presenter of the top screen of the chosen tab and of any screen the UI reports as visible, and SHALL stop every other presenter while keeping the last state it published.

#### Scenario: Covered screen stops
- **WHEN** an activity page covers the feed
- **THEN** the feed's presenter stops collecting its data, and the feed's last state stays readable

#### Scenario: Seen during a back swipe
- **WHEN** the UI reports the feed visible while the athlete is part-way through swiping the activity page away
- **THEN** the feed's presenter runs again before the activity page leaves the stack

#### Scenario: Another tab
- **WHEN** the athlete chooses the Search tab
- **THEN** the feed tab's presenters stop and the Search tab's top screen runs

### Requirement: The session is decided once for every shell
What a signed-in session shows (nothing yet, only the leave in progress, only the choice after leaving elsewhere, or the app) and when the session's background work runs SHALL be decided by the same shared code for the Compose shell and the host, so the platforms can't differ.

#### Scenario: A leave recorded on this device
- **WHEN** a session starts with a leave of its account recorded on the device
- **THEN** both shells show only the leave, and neither runs the session's background work

#### Scenario: Left on another device
- **WHEN** the session learns the athlete left on another device
- **THEN** both shells show only the choice to discard or rejoin, and the background work stops until the athlete rejoins
