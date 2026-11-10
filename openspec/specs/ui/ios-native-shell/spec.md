# ui/ios-native-shell Specification

## Purpose
How the iOS app's navigation behaves now that the system's own tabs, stacks and transitions hold its screens, whether a screen is drawn natively or still by the shared UI, and what its native sign-in screen shows.

## Requirements

### Requirement: The system's tabs and stacks hold the screens
On iOS the signed-in app SHALL show its three tabs (Feed, Search, You) with the system's tab control, and SHALL open and close screens with the system's navigation transitions. A screen closed by the app itself, such as after a save, SHALL leave the same way a screen closed by the athlete does.

#### Scenario: Opening an activity
- **WHEN** an athlete taps an activity in the feed
- **THEN** its page slides in over the feed with the system's push transition

#### Scenario: Closed by a save
- **WHEN** an athlete saves an edit to an activity
- **THEN** the edit screen leaves with the system's pop transition and the activity's page shows

#### Scenario: iPad
- **WHEN** the app runs on an iPad
- **THEN** the tabs show where iPadOS places an app's tabs

### Requirement: Each tab keeps its place
Each tab SHALL keep its own stack of screens. Choosing another tab and coming back SHALL show the screen that was on top, and choosing the tab already shown SHALL return it to its first screen.

#### Scenario: Away and back
- **WHEN** an athlete opens an activity from the feed, chooses Search, then chooses Feed
- **THEN** the activity's page shows, and back returns to the feed

#### Scenario: Choosing the current tab
- **WHEN** an athlete with a profile and an activity open on the You tab taps You again
- **THEN** the tab shows their own profile, with nothing left to go back to

### Requirement: Swipe back works on every screen
An athlete SHALL be able to go back by swiping from the screen's leading edge on every screen that has one to go back to, whether the screen is drawn natively or by the shared UI. The swipe SHALL follow the finger and SHALL be cancellable.

#### Scenario: A screen drawn by the shared UI
- **WHEN** an athlete swipes from the left edge of an activity's page, which the shared UI draws
- **THEN** the page follows the finger, the feed shows beneath it, and letting go past half way returns to the feed

#### Scenario: Cancelled
- **WHEN** an athlete starts the swipe and lets go near the edge
- **THEN** the activity's page settles back and stays open

#### Scenario: A tab's first screen
- **WHEN** an athlete swipes from the left edge of the feed
- **THEN** nothing closes and the app keeps responding

#### Scenario: A map
- **WHEN** an athlete swipes from the left edge of the full-screen route map
- **THEN** the map screen goes back and the map itself doesn't pan

### Requirement: A covered screen is as it was left
A screen that another screen covered, or whose tab the athlete left, SHALL show what it showed before when the athlete returns: the same content at the same scroll position, without loading again from empty.

#### Scenario: Back to the feed
- **WHEN** an athlete scrolls the feed, opens an activity and goes back
- **THEN** the feed shows the same activities at the same scroll position, with no first-load spinner

### Requirement: Whole-window flows hide the tabs
The tabs SHALL NOT show over a flow that takes the whole window: recording, adding an activity by hand, importing, editing an activity, editing the profile, the media gallery, and the full-screen route and segment maps. They SHALL show again when the flow closes.

#### Scenario: Recording
- **WHEN** an athlete opens Record from the feed
- **THEN** the tabs are hidden, and they show again when Record closes

### Requirement: Screens without a native view are unchanged
Every screen SHALL be reachable on iOS whether or not it has a native view. A screen without one SHALL be drawn by the shared UI and SHALL look and behave as it did before: its own navigation bar with its title and actions, its sheets, menus and alerts, its text fields, and its content ending clear of the tabs while scrolling under them.

#### Scenario: Every screen opens
- **WHEN** an athlete opens any screen of the app that has no native view
- **THEN** it shows its content and its navigation bar as it did before the native shell

#### Scenario: Under the tabs
- **WHEN** an athlete scrolls the feed to its end
- **THEN** the last activity rests above the tabs, and earlier ones scroll beneath them

#### Scenario: A sub-screen
- **WHEN** an athlete opens the feed's filter
- **THEN** the filter shows and changing it changes the feed, as before

### Requirement: The app follows the session
The iOS app SHALL show the sign-in screen while signed out and the tabs, starting on Feed, once signed in. While a leave of the account is in progress it SHALL show only the leave, and after the athlete left on another device only the choice to discard or rejoin. Signing out SHALL forget every tab's stack.

#### Scenario: Signing in
- **WHEN** an athlete completes sign-in
- **THEN** the tabs show with the feed on top

#### Scenario: Signing out
- **WHEN** an athlete with screens open on two tabs signs out and signs in again
- **THEN** each tab shows its first screen

#### Scenario: Leaving
- **WHEN** an athlete starts leaving Activity Tracker
- **THEN** only the leave's progress shows, with no tabs

### Requirement: Requests from outside open their screen
A tap on the recording notification SHALL open Record, and a tap on the export-ready notification SHALL open Settings and its Account page, on the tab then shown. An unfinished recording of the signed-in account SHALL be offered when the app opens.

#### Scenario: Recording notification
- **WHEN** an athlete on the Search tab taps the recording notification
- **THEN** Record opens over the Search tab

#### Scenario: Unfinished recording
- **WHEN** the app opens signed in with a recording of that account still going
- **THEN** Record opens over the feed

### Requirement: The sign-in screen is native
On iOS the sign-in screen SHALL be drawn with the system's own controls and SHALL offer what the shared sign-in screen offers: the handle field, Sign in, progress while signing in, and Cancel while waiting for the browser. The handle field SHALL not capitalise or autocorrect, and its keyboard's action SHALL submit.

#### Scenario: Nothing typed
- **WHEN** the handle field is empty or only spaces
- **THEN** Sign in is disabled

#### Scenario: Submitting from the keyboard
- **WHEN** an athlete types `alice.test` and taps the keyboard's Go
- **THEN** sign-in starts, the field is disabled and progress shows in place of Sign in

#### Scenario: Waiting for the browser
- **WHEN** the system's sign-in sheet is open
- **THEN** the screen says to continue in the browser and offers Cancel, which returns it to the handle field

### Requirement: The native sign-in screen says what the shared one says
The native sign-in screen SHALL show the same messages, in the same words, as the shared sign-in screen for the same state: a failed sign-in with its reason, an expired session, a recording still running behind it, a chosen Sign in again, and having left Activity Tracker.

#### Scenario: Server unreachable
- **WHEN** sign-in can't start because the server can't be reached
- **THEN** the screen shows "Couldn't start sign-in. Couldn't reach the server."

#### Scenario: Expired session
- **WHEN** the last session ended because it expired
- **THEN** the screen says the session expired and the handle is filled in

#### Scenario: After leaving
- **WHEN** `alice.test` has just left Activity Tracker
- **THEN** the screen says she left and that her atproto account `alice.test` is unchanged, with the handle field empty

### Requirement: The iOS app turns with the device
The iOS app SHALL show upright and on either side on an iPhone, and any way up on an iPad, turning with the device on every screen, whether a native view or the shared UI draws it. Turning SHALL keep each tab's stack, the screen on top and what it shows.

#### Scenario: An iPhone turned on the feed
- **WHEN** an athlete turns an iPhone showing the feed onto its side
- **THEN** the feed and the tabs lay out across the wider screen, at the same place in the feed

#### Scenario: A screen the shared UI draws
- **WHEN** an athlete turns an iPhone showing an activity, which the shared UI draws, onto its side
- **THEN** the activity lays out across the wider screen, and back returns to the screen it was opened from

#### Scenario: An iPad upside down
- **WHEN** an athlete turns an iPad upside down
- **THEN** the app turns to stay upright for them

#### Scenario: An iPhone upside down
- **WHEN** an athlete turns an iPhone upside down
- **THEN** the app stays as it was
