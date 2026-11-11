# dev/shared-display-text Specification

## Purpose
Has text that follows a rule (a quantity with its unit, a label with a fallback, a failure with its reason) chosen once in shared code, so the shared and the native UIs read the same and neither rewrites the rule.

## Requirements

### Requirement: Rule-bound text is chosen in shared code
Text whose wording follows a rule (a quantity with its unit, a label looked up by a token, a failure with its reason) SHALL be chosen by shared code that every client runs, as a shared string's name with its arguments, a plural with its quantity, or text as written. A UI SHALL only resolve that choice to the string its platform holds.

#### Scenario: A distance in each unit system
- **WHEN** 4,828 meters is formatted as a distance for an athlete who uses imperial units, and for one who uses metric
- **THEN** the shared and native UIs both read "3.00 mi", and both read "4.83 km"

#### Scenario: A missing value
- **WHEN** a pace is formatted for an activity with no distance
- **THEN** both UIs show the shared string for a missing value

#### Scenario: A sport's rate
- **WHEN** the average rate of a run, a ride, a swim and a yoga session is asked for
- **THEN** it is a pace, a speed, a pace per 100 meters or yards, and nothing, in both UIs

### Requirement: A token without a string falls back the same way everywhere
A label looked up by a token from an open set SHALL read as the shared string for that token where one exists, and otherwise as the fallback shared code names, identically in the shared and native UIs.

#### Scenario: A known sport
- **WHEN** the label of the sport `run` is shown
- **THEN** both UIs read "Run"

#### Scenario: An unknown sport
- **WHEN** the label of a sport token the app has no string for, `quidditch`, is shown
- **THEN** both UIs read "Quidditch"

#### Scenario: A sub-sport
- **WHEN** the label of a ride whose sub-sport is mountain biking is shown, and of a ride whose sub-sport the app doesn't name
- **THEN** both UIs read the sub-sport's own name for the first and "Ride" for the second

### Requirement: Error text is chosen once
What an error says SHALL be chosen by shared code from what failed and why, as `ui/error-messages` describes, and SHALL read the same in the shared and native UIs.

#### Scenario: A failure with a reason
- **WHEN** loading the feed fails because the server can't be reached
- **THEN** both UIs read "Couldn't load your feed. Couldn't reach the server."

#### Scenario: A failure without one
- **WHEN** loading the feed fails for a reason the athlete can't act on
- **THEN** both UIs read "Couldn't load your feed"

### Requirement: Every name shared code can choose exists
`./gradlew check` SHALL fail when shared code can choose a string or image name that the shared resources don't hold: for every kind of error and reason, every unit formatter in both unit systems, every closed label, and every sport's icon.

#### Scenario: A new kind of error without its string
- **WHEN** a kind of error is added and no string is added for it
- **THEN** `./gradlew check` fails and names the missing string

#### Scenario: A sport's icon is renamed away
- **WHEN** a drawable a sport's icon names is removed
- **THEN** `./gradlew check` fails and names it

### Requirement: Moving the choice changes nothing shown
Android, desktop and web SHALL show the same text and icons as before the choice moved to shared code.

#### Scenario: Reference screenshots
- **WHEN** the screenshot suite runs after the move
- **THEN** no reference image differs

### Requirement: Visibility choices read the same in both UIs
The name and help of each detail category, the note shown for Followers without approval, the note for a visibility that can't be chosen, and the note under each settings page's choices SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: Cadence without running form
- **WHEN** the cadence category is offered for an activity that records cadence but no running form
- **THEN** both UIs name it "Cadence", and for the defaults "Cadence and running form"

#### Scenario: A category the app doesn't name
- **WHEN** a detail category token the app has no name for is offered
- **THEN** both UIs show the token as it is

#### Scenario: A server without spaces
- **WHEN** an athlete's server doesn't host spaces
- **THEN** both UIs read "Your server doesn't support private activities yet, so they're Public." under the choices

#### Scenario: The segments note while saving
- **WHEN** segment matching is saving on a session that can use segments
- **THEN** both UIs read "Saving…" under the segment choices, and "Sign in again to use segments." instead on a session that can't

### Requirement: Gear reads the same in both UIs
A gear type's and kind's names, an item's usage total, the line under an item's name, and a collection's item count SHALL be chosen by shared code in the athlete's units, and both UIs SHALL show that choice.

#### Scenario: A kind the app doesn't name
- **WHEN** an item's kind is one this client has no name for
- **THEN** both UIs show its type's name in its place

#### Scenario: Skis in imperial
- **WHEN** skis have 14 days and 41,000 m of vertical and the athlete uses imperial units
- **THEN** both UIs read "14 days · 134,514 ft"

#### Scenario: A retired item where retirement is shown
- **WHEN** a retired bike visible to everyone is listed where retirement is shown
- **THEN** both UIs read "Bike · Public · Retired" under its name

### Requirement: The block lists read the same in both UIs
Each block list's title, empty message, remove action, the note for a block this app can't undo, and what failed when the list can't load SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: The muted list
- **WHEN** the muted list is shown
- **THEN** both UIs title it "Muted accounts" and offer "Unmute"

### Requirement: A paged list's statuses are decided once
What a paged list shows around its items in each loading state SHALL be decided by shared code that both UIs ask, so the two can't differ.

#### Scenario: A refresh of an empty list
- **WHEN** an answered list holds nothing and is refreshing
- **THEN** both UIs show the refresh's progress above and not the empty message

### Requirement: The Account page reads the same in both UIs
The sign-in status with an outdated grant's missing features in their order, each data export status's lines, and the end-session question's title, message and action SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: An outdated grant
- **WHEN** a grant is missing Leaving and Gear
- **THEN** both UIs list "Private gear, gear defaults and collections" before "Leaving Activity Tracker"

#### Scenario: Listing records
- **WHEN** an export is running and has listed no records yet
- **THEN** both UIs show the listing line and not a count of records

#### Scenario: Signing in again with an import saving
- **WHEN** the athlete chooses Sign in again while an import is saving
- **THEN** both UIs title the question "Sign in again?", say the import is saving, and offer "Sign in again anyway"

### Requirement: The Leave page reads the same in both UIs
The shared places' counts, the lines for what this device would lose, a running leave's step, its records deleted, and the time it was asked to wait until SHALL be chosen by shared code, and both UIs SHALL show that choice. The time SHALL follow the device's 12- or 24-hour clock.

#### Scenario: No shared places
- **WHEN** the athlete has no segments or dive sites
- **THEN** both UIs show the "none" line and no count

#### Scenario: A leave that hasn't reported a step
- **WHEN** a resumed leave has no step yet
- **THEN** both UIs read "Leaving Activity Tracker…"

#### Scenario: A 24-hour clock
- **WHEN** a leave was asked to wait until 19:05 on a device that uses a 24-hour clock
- **THEN** both UIs read "Carrying on at 19:05."

### Requirement: Devices read the same in both UIs
A device's name from its maker and model, the line under it (the end of its serial, or that it may match other units), and the spoken labels of its Un-ignore and Unlink actions SHALL be chosen by shared code, and both UIs SHALL show that choice wherever a device is listed.

#### Scenario: A model that names its maker
- **WHEN** a device's maker is "Shearwater" and its model "Shearwater Perdix AI"
- **THEN** both UIs name it "Shearwater Perdix AI"

#### Scenario: A device known only by its model
- **WHEN** a Polar H10 has no serial and is known only by its model
- **THEN** both UIs read "Known only by its model, so it may match other units" under its name

#### Scenario: Unlinking read aloud
- **WHEN** a screen reader reads Unlink beside a linked SRAM 1052
- **THEN** both UIs read "Unlink SRAM 1052"

### Requirement: A gear collection's page reads the same in both UIs
A collection page's title, and when its Edit and Delete show and can be used, SHALL be decided by shared code that both UIs ask.

#### Scenario: Being deleted
- **WHEN** a loaded collection is being deleted
- **THEN** both UIs show Edit and Delete, and neither can be used

### Requirement: The follow lists read the same in both UIs
Each follow list's title, the handle under it, its empty message and what failed when it can't load, and when an account's Remove can be used, SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: The following list
- **WHEN** bob's Following list is shown
- **THEN** both UIs title it "Following" with "@bob.test" under it, and an empty one reads "Not following anyone yet."

#### Scenario: A removal in flight
- **WHEN** bob's removal from alice's followers is being written
- **THEN** both UIs show his Remove and neither lets it be used

### Requirement: Follow requests read the same in both UIs
The Follow requests page's tab labels, each tab's empty message, which accounts each tab lists, when Decline is offered, when an account's answers can be used, and when the declined and truncated notes show SHALL be decided by shared code that both UIs ask.

#### Scenario: An account that can't be answered
- **WHEN** a request comes from an account whose DID isn't a valid record key
- **THEN** both UIs show its Decline and Approve and neither lets them be used

#### Scenario: The truncated note on Declined
- **WHEN** there were more than 500 requests and the athlete chooses Declined
- **THEN** neither UI shows "Showing the first 500 requests."

### Requirement: The sign-in-again notice is decided once
Whether a list shows that an action failed or that the session must sign in again, which of the two it says, and whether it offers Sign in again SHALL be decided by shared code that every list showing the notice asks, in both UIs.

#### Scenario: A session that must sign in again
- **WHEN** the session can't read or write declines and no answer has failed
- **THEN** both UIs read "Sign in again to share activities or details with Followers, Mutuals or Only me, …" with Sign in again

#### Scenario: A failed answer
- **WHEN** approving a request fails because the server can't be reached and the session can read declines
- **THEN** both UIs show the failure and offer no Sign in again

#### Scenario: Neither
- **WHEN** the session can read declines and no answer has failed
- **THEN** neither UI shows the notice

### Requirement: Zones are named the same in both UIs
A computed zone's label and a privacy zone's name SHALL be chosen by shared code, and both UIs SHALL show that choice wherever the zone is listed.

#### Scenario: A computed zone
- **WHEN** a heart rate zone's label is "5a"
- **THEN** both UIs label it "Zone 5a"

#### Scenario: A labelled privacy zone
- **WHEN** a privacy zone is labelled "Home"
- **THEN** both UIs name it "Home"

#### Scenario: A privacy zone with no label
- **WHEN** the second privacy zone in the list has no label
- **THEN** both UIs name it "Zone 2"

### Requirement: The gear editors read the same in both UIs
The gear and collection forms' titles, the shown kind, the sports and default-for summaries, the starting distance's label, the photo button, the mounted-on and retired lines, each field's maximum length, and when a form's fields can be used, its kind is offered, its management shows and its Add items is offered, SHALL be chosen by shared code that both UIs ask.

#### Scenario: Sports joined
- **WHEN** an item is for Ride and Run
- **THEN** both UIs read "Ride, Run" for its sports

#### Scenario: No sports
- **WHEN** an item is for no sport
- **THEN** both UIs read "Any sport" for its sports

#### Scenario: While saving
- **WHEN** the gear form is saving
- **THEN** neither UI lets its fields, choices or buttons be used

#### Scenario: A kind this client doesn't know
- **WHEN** an item's kind isn't among its type's kinds
- **THEN** both UIs show "None" as its kind

### Requirement: The type sheets group the same in both UIs
Which sports and activity types the gear form's sheets offer, by category and in what order, and which types show under their sport as its sub-sports, SHALL be chosen by shared code that both UIs and the other type pickers ask.

#### Scenario: A sub-sport under its sport
- **WHEN** the default-for sheet lists the cycling category
- **THEN** Gravel Ride shows under Ride, not as a row of its own

#### Scenario: Only sports for gear
- **WHEN** the sports sheet lists the cycling category
- **THEN** it offers Ride and no sub-sport of it

### Requirement: The invites list reads the same in both UIs
The Activity invites page's tab labels, each tab's empty message, an invite's title, who added the athlete and its distance and time, which invites each tab lists, when Decline is offered, when an invite's buttons can be used and when the Declined note shows SHALL be decided by shared code that both UIs ask.

#### Scenario: An untitled invite
- **WHEN** an invite's activity has no title
- **THEN** both UIs title it with the activity's type

#### Scenario: An invite without a distance
- **WHEN** an invite's activity has no distance
- **THEN** both UIs show its time alone

### Requirement: Add people reads the same in both UIs
Each invitee's status and its button's word, which lists the page shows, when the activity is full, and when an invitee's or follower's button can be used SHALL be decided by shared code that both UIs ask.

#### Scenario: A pending invitee
- **WHEN** bob was added and hasn't joined
- **THEN** both UIs show him as Pending with Cancel

#### Scenario: An add in flight
- **WHEN** carol is being added
- **THEN** both UIs show her Add and neither lets it be used

### Requirement: A length of time reads the same in both UIs
A rough length of time SHALL be chosen by shared code, rounded to its largest unit: whole minutes (at least 1) under an hour, rounded hours under a day, and rounded days from a day up, and both UIs SHALL show that choice.

#### Scenario: Under an hour
- **WHEN** a length of 40 minutes 30 seconds is shown
- **THEN** both UIs read "40 min"

#### Scenario: A day and a bit
- **WHEN** a length of 26 hours is shown
- **THEN** both UIs read "1 day"

### Requirement: The upload queue reads the same in both UIs
The upload queue's words SHALL be chosen by shared code that both UIs ask: its items' and refused changes' names and states, its imports' lines, its spoken actions, its pauses and time left, and its questions. When its controls show and can be used SHALL be decided there too.

#### Scenario: Two exports
- **WHEN** an import was read from Strava and Garmin exports
- **THEN** both UIs read "Strava and Garmin exports"

#### Scenario: Busy
- **WHEN** the refused like is being sent again
- **THEN** both UIs show its Send again and Cancel unavailable

### Requirement: Day words follow the clock the UI gives
Words that depend on the current day (today, yesterday, tomorrow) SHALL be decided by shared code from the current time and time zone the UI gives, so a preview or test with a fixed clock reads the same on any day and in any zone.

#### Scenario: A server pause past midnight
- **WHEN** the server paused uploads until 1:10 AM tomorrow
- **THEN** both UIs read "Uploads paused by the server until tomorrow at 1:10 AM"

#### Scenario: An import saved today
- **WHEN** an import was saved at 7:00 AM today
- **THEN** both UIs read "Saved Today at 7:00 AM" in its group
