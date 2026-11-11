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

### Requirement: The host says when Settings can show beside its page
For each stack, the host SHALL tell a UI that has reported a size with room for two panes when the top of the stack is Settings, alone or with only settings pages above it: which screen is Settings, the pages above it in the order they were opened, and the page whose row the list marks. With any other screen on top, or for a UI that hasn't reported such a size, it SHALL report nothing. Room for two panes SHALL be at least 700 points of width and 500 of height.

#### Scenario: A page open
- **WHEN** a wide UI's stack is a profile, Settings and Privacy zones
- **THEN** the host reports Settings with Privacy zones beside it, and Privacy zones as the marked page

#### Scenario: A deeper page
- **WHEN** a wide UI's stack is a profile, Settings, Training zones and a sport's zones
- **THEN** the host reports Settings with Training zones and the sport's zones beside it in that order, and Training zones as the marked page

#### Scenario: Another screen on top
- **WHEN** a wide UI's stack is a profile, Settings, Blocked accounts and another athlete's profile
- **THEN** the host reports nothing, and the UI shows the profile over the rest

#### Scenario: A narrow UI
- **WHEN** a UI that reported 699 points of width has a stack of a profile, Settings and Privacy zones
- **THEN** the host reports nothing

#### Scenario: A wide UI that is short
- **WHEN** a UI that reported 956 points of width and 440 of height has a stack of a profile, Settings and Privacy zones
- **THEN** the host reports nothing

#### Scenario: A UI turned on its side
- **WHEN** a UI showing Settings beside Privacy zones at 834 by 1,210 points reports 1,210 by 834
- **THEN** the host still reports Settings with Privacy zones beside it

### Requirement: A UI's size never changes its stack
The host SHALL hold the same screens in a stack whatever size the UI reports: laying Settings out beside its page, or one screen at a time, SHALL be only a way of showing the stack.

#### Scenario: Size doesn't change the stack
- **WHEN** a UI showing Settings beside Privacy zones reports a narrower width
- **THEN** the stack is still the profile, Settings and Privacy zones, and the host reports nothing to show beside Settings

### Requirement: The page shown before a row is chosen is not on the stack
While Settings is on top of a wide UI's stack with no page above it, the host SHALL offer Activity defaults as the page to show beside it, without putting it on the stack: going back from Settings SHALL leave Settings. Its presenter SHALL run while the UI reports it visible, and what it retained SHALL be kept for as long as that Settings screen is on the stack and dropped when it leaves.

#### Scenario: Opening Settings wide
- **WHEN** a wide UI opens Settings from a profile
- **THEN** the stack is the profile and Settings, the host offers Activity defaults beside it as the marked page, and Activity defaults publishes its state once the UI reports it visible

#### Scenario: Choosing a row
- **WHEN** an athlete then chooses Gear
- **THEN** the stack is the profile, Settings and Gear, and the host reports Gear beside Settings in place of Activity defaults

#### Scenario: Leaving Settings
- **WHEN** Settings is popped and opened again
- **THEN** the Activity defaults offered beside it starts with nothing retained from the first visit

### Requirement: A pane's first page knows it has no way back
The host SHALL tell the first page shown beside Settings that the list is beside it, and every other screen that it isn't, so a UI that draws the page gives it no back button there and gives it one when the same page is shown on its own.

#### Scenario: The same page, wide then narrow
- **WHEN** Privacy zones is shown beside the list and the UI then reports a narrow width
- **THEN** the page is told first that it is a pane's first page and then that it isn't

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
