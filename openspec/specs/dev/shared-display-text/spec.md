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

### Requirement: A board's choices read the same in both UIs
For the leaderboard, Mayors and Regulars boards, each choice's options and their labels (periods and windows, scope, the board's types, and the age, weight and gender brackets, weight in the viewer's units), which option shows as chosen, whether a choice marks itself as narrowing the board, and whether the type is a menu or a label SHALL be decided by shared code that both UIs ask.

#### Scenario: A chosen weight class in pounds
- **WHEN** an imperial viewer has chosen the `55-64` weight class
- **THEN** both UIs show the weight choice as "121–142 lb", marked as narrowing the board

#### Scenario: One type
- **WHEN** a board lists a single type, `run`
- **THEN** both UIs show "Run" as a label and offer no menu

### Requirement: A board's entries read the same in both UIs
A board entry's rank, whether the rank is accented, the day line (the effort's day, or "Reached" with the day its count was reached) and its count SHALL be chosen by shared code that both UIs ask; the leaderboard's time is the shared clock form of a duration.

#### Scenario: A Mayors entry
- **WHEN** Bob leads a Mayors board with 1204 efforts, the latest on 2026-10-26
- **THEN** both UIs show rank 1 accented, "Reached Oct 26, 2026" and "1,204 efforts"

#### Scenario: A fourth place on a leaderboard
- **WHEN** an entry is ranked 4th
- **THEN** neither UI accents its rank

### Requirement: A written-out date reads the same in both UIs
A calendar date written out, as a board entry, a segment effort and a threshold history row show it, SHALL be chosen by shared code: the short month name, the day and the year, never grouped; a `YYYY-MM-DD` text that doesn't parse SHALL read as itself.

#### Scenario: An effort's day
- **WHEN** an effort's date is `2026-01-05`
- **THEN** both UIs read "Jan 5, 2026"

#### Scenario: A date that doesn't parse
- **WHEN** an entry's date is `soon`
- **THEN** both UIs read "soon"

### Requirement: The starred list reads the same in both UIs
A starred segment's line (its length in the viewer's units and its type: a ski lift, a ski run, or its sport group), whether a star opens its segment, whether its unstar can be used, the notice that the session must sign in again, and whether the empty message shows SHALL be decided by shared code that both UIs ask.

#### Scenario: A ski run
- **WHEN** a metric athlete has starred a 1.2 km alpine run
- **THEN** both UIs read "1.2 km · Ski run" under its name

#### Scenario: A star whose segment is gone
- **WHEN** a star's segment is no longer available
- **THEN** neither UI opens anything when it is tapped, and both offer its unstar

### Requirement: Edit profile reads the same in both UIs
Edit profile's counters, its units' labels, the warning for a detail now hidden, the gender choices with which one shows as chosen, who may see each detail group, whether a field holds a value the lexicon allows, and when Save is offered SHALL be chosen by shared code that both UIs ask.

#### Scenario: A counter
- **WHEN** the bio holds 31 characters
- **THEN** both UIs read "31/256" under it

#### Scenario: A saved self-description
- **WHEN** alice's saved gender is "Agender"
- **THEN** both UIs show Self-describe as chosen, with "Agender" in the description field

#### Scenario: While saving
- **WHEN** the form is saving
- **THEN** neither UI offers Save

#### Scenario: No group chosen
- **WHEN** a group has no visibility chosen
- **THEN** both UIs show it as Only me

### Requirement: Countries are searched the same in both UIs
Which countries the edit profile country choice lists for what was typed, and in what order, SHALL be decided by shared code that both UIs ask: every country by name in the device's language when nothing is typed, and otherwise the country whose code was typed first, then those whose names contain it, ignoring case and accents.

#### Scenario: Accents ignored
- **WHEN** "cote" is typed
- **THEN** both UIs list Côte d'Ivoire

#### Scenario: A code first
- **WHEN** "us" is typed
- **THEN** both UIs list United States first

### Requirement: Computed zones read the same in both UIs
A computed zone's name, its range with its unit (bpm or W) or as paces open-ended at either end, a pace unit, a zone model's name (its token where the app has no name for it), what a model is based on and what it needs SHALL be chosen by shared code, and both UIs SHALL show that choice. A zone's name SHALL be chosen once for the zones pages and the activity charts.

#### Scenario: A heart rate zone's range
- **WHEN** a heart rate zone runs from 143 to 150
- **THEN** both UIs read "143–150 bpm", and the last zone from 180 reads "180+ bpm"

#### Scenario: A pace zone's range
- **WHEN** a running pace zone sits between 5:10 and 4:50 per kilometer
- **THEN** both UIs read "5:10–4:50 /km"

#### Scenario: A model the app doesn't name
- **WHEN** another client saved a zone model whose token the app has no name for
- **THEN** both UIs show the token as it is

#### Scenario: A zone's name in an activity
- **WHEN** an activity's time in zone lists Zone 2 of the default heart rate model
- **THEN** it is named as the zones pages name that zone

### Requirement: The zones pages' words and conditions are decided once
Each set of zones' heading and what it applies to, which thresholds it lists with each one's value, "Not set" or marked as the default's, a sport's form title, help, pace label, the default a blank field falls back to and Remove, SHALL be chosen by shared code. Which fields hold a valid value, how many characters each takes, which thresholds imports may update, and when each zones page can save SHALL be decided by shared code too.

#### Scenario: A threshold from the defaults
- **WHEN** a sport sets no max heart rate of its own and the default is 190
- **THEN** both UIs read "190 bpm (default)" for it on Training zones, and "Default: 190" in the sport's empty field

#### Scenario: Out of range
- **WHEN** a max heart rate reads 300
- **THEN** both UIs mark it as wrong and neither can save

### Requirement: Privacy zones read the same in both UIs
A privacy distance (Off, or the distance in the athlete's units), the hidden distances offered, the add-here button's words, and when the page can change SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: Off
- **WHEN** route ends are not hidden
- **THEN** both UIs read "Off" for Hide start and end

#### Scenario: A radius in miles
- **WHEN** an athlete whose units are imperial has a zone of 800 m
- **THEN** both UIs show its radius in miles, as stats read

### Requirement: An activity card reads the same in both UIs
An activity card's words SHALL be chosen by shared code that both UIs ask: its author, visibility badge, whom it was done with, moderation notices and photo labels, title and icon's spoken label, stats, locks and counts. Whether Like is offered and given, and whether photos are blurred or placeholders, SHALL be decided there too.

#### Scenario: Done with three others
- **WHEN** a card's activity lists Bob, Carol and Dan as partners
- **THEN** both UIs read "with Bob and 2 others"

#### Scenario: Own warned activity
- **WHEN** the owner sees their activity labelled Spam
- **THEN** both UIs show the Spam notice and no Like

#### Scenario: A strength workout
- **WHEN** a card shows a strength workout with no distance
- **THEN** both UIs show only its time

### Requirement: A start time reads on the activity's clock in both UIs
An activity's start SHALL be chosen by shared code on the activity's own clock, its time of day formatted by each UI on the device's 12- or 24-hour clock in the activity's UTC offset, and its day beside the current day in that offset, so both UIs read the same start for the same activity.

#### Scenario: Started in another zone
- **WHEN** a viewer in UTC sees a run started at 9:00 AM local time at UTC-7, the same day
- **THEN** both UIs read "Today at 9:00 AM"

#### Scenario: Day only
- **WHEN** a viewer isn't served a ride's start time
- **THEN** both UIs read only its day

### Requirement: The batch page's months read the same in both UIs
The batch page's month headings SHALL be chosen by shared code, from each activity's start in the viewer's time zone, and both UIs SHALL show them over the same activities.

#### Scenario: A month heading
- **WHEN** a batch's newest activity started on November 5, 2025 in the viewer's zone
- **THEN** both UIs head it "Nov 2025"

### Requirement: The feed filter bar's chips read the same in both UIs
What the feed filter bar's chips say SHALL be chosen once in shared code and resolved by each UI: the types chip names all types when none is chosen, the one type when one is, and the first type with how many more when several are (sorted by sport, then sub-sport); the date chip names any time, the last N days, this year, or a custom range from its first day to its last.

#### Scenario: Several types
- **WHEN** the filter holds a ride and two runs' sub-sports
- **THEN** both UIs' types chip reads the first of them by sport with "+2"

#### Scenario: A custom range
- **WHEN** the filter's range is a custom range from 1 March to 15 March 2026
- **THEN** both UIs' date chip names that range with its two days

### Requirement: The gear page reads the same in both UIs
The gear page's words SHALL be chosen by shared code that both UIs ask: the item's kind line, brand and model, visibility and Retired notes, which totals apply to its type and how each reads, its starting usage, its breakdown rows, each component's line and mount dates, the mount sheet's title and each candidate's line.

#### Scenario: Skis
- **WHEN** both UIs show skis with no distance and 41,000 m of vertical
- **THEN** both list Vertical and no Distance among the totals

#### Scenario: A past component
- **WHEN** Chain #3 was mounted from 1 March 2025 to 1 July 2026
- **THEN** both UIs read its dates the same way in the viewer's time zone

### Requirement: The gear page's conditions are decided once
Which sections of the gear page show, whether Edit, the collections and component management are offered, whether mounting can be used, which error shows by Components, and the unmount date's first and initial day SHALL be decided by shared code that both UIs ask.

#### Scenario: One activity type
- **WHEN** an item's activities are all of one type
- **THEN** neither UI shows the By activity type breakdown

### Requirement: The profile reads the same in both UIs
The profile's title, its counts, the details' location and facts lines in the viewer's units, the follow button's label and style, and the own profile's entries with their numbers SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: More requests than counted
- **WHEN** alice approves followers and has more requests than were read, 500 counted
- **THEN** both UIs show "Follow requests (500+)"

#### Scenario: A height in imperial units
- **WHEN** a viewer using imperial units sees a profile whose height is 1803 mm
- **THEN** both UIs show 5′ 11″ in the facts line

#### Scenario: Following
- **WHEN** alice follows bob
- **THEN** both UIs show Following on bob's profile as the secondary button

### Requirement: The profile's actions are decided once
Which of the own entries, Unblock, the Bluesky note and the follow button a profile shows, the menu's Mute and Block labels and whether Block is offered, the block confirmation's words, and whether the activities wait behind the hidden warning SHALL be decided by shared code that both UIs ask.

#### Scenario: Blocked on Bluesky
- **WHEN** alice's only block of dave is on Bluesky
- **THEN** neither UI offers Block or Unblock on dave's profile, and both show the Bluesky note

### Requirement: A post card reads the same in both UIs
A post card's reposted-by line, audience, proposal place, name, status and totals, the quoted post's header, "Post unavailable", the counts, which of Like, Repost, Quote, the votes and the menu it offers, and what a vote tap sends SHALL be chosen by shared code that both UIs ask.

#### Scenario: Reposted by the viewer
- **WHEN** alice's timeline shows carol's post through alice's own repost
- **THEN** both UIs head it "Reposted by you"

#### Scenario: Counts
- **WHEN** a post has 2 likes, 1 comment, no reposts and 1 quote
- **THEN** both UIs show "2 likes · 1 comment · 0 reposts · 1 quote"

### Requirement: A comment's actions are decided once
Which actions a comment offers (Reply, Delete, Hide or Unhide, Mute author, Block author, Report), in what order, and the words of its hidden note and of the delete and block confirmations SHALL be decided by shared code that both UIs ask. What a comment's Report opens (the report sheet for that comment, naming its author) SHALL be built there too, so neither UI builds a report target.

#### Scenario: Someone else's comment on someone else's post
- **WHEN** alice sees carol's comment on bob's post with a labeler configured
- **THEN** both UIs decide Reply, Mute author, Block author and Report, and no Delete or Hide

#### Scenario: A comment's Report
- **WHEN** alice chooses Report on carol's comment in either UI
- **THEN** the report sheet opens for carol's comment, by its URI and CID, naming carol

### Requirement: The report sheet reads the same in both UIs
What the report sheet says SHALL be chosen once in shared code and resolved by each UI: its title naming the author, the six reasons in order, the note's label (required for Other) and its limit, what moderators may see, Send, the thanks, the Block offer naming the author, and the Block confirmation. Whether the note is required and which reason is chosen SHALL be decided there too.

#### Scenario: Other chosen
- **WHEN** alice chooses Other in either UI
- **THEN** the note's label says it is required, and Send stays off until she writes one

#### Scenario: The Block offer
- **WHEN** alice's report of Bob's profile is sent and she doesn't block him
- **THEN** both UIs offer "Block Bob"

### Requirement: The place-name sheet reads the same in both UIs
What the place-name sheet says SHALL be chosen once in shared code and resolved by each UI: its title and confirm word (propose or edit), the line before the field (what proposing shows, or that saving clears the votes), the field, the invalid name note, Share to feed and its detail, why only Public is offered, the share text's label and its too-long note. When the invalid note shows and when sharing can be changed SHALL be decided there too.

#### Scenario: An unchanged name
- **WHEN** an athlete types the place's current name in either UI
- **THEN** the invalid name note shows and the name can't be sent

#### Scenario: Public only
- **WHEN** the session's grant predates posts in spaces and Share to feed is on
- **THEN** both UIs offer Public alone and say that signing in again lifts it

### Requirement: The feed reads the same in both UIs
What the feed says SHALL be chosen once in shared code and resolved by each UI: its title and scopes, the Add menu's entries (Record only where recording isn't a button of its own and the device can record), the refused and uploading rows with the imports' and the server's pauses, and the empty message with whether it offers Clear filters. Counts SHALL be grouped as `ui/number-format` says.

#### Scenario: Uploading
- **WHEN** one activity, 3,000 imported ones and 3 other changes are uploading
- **THEN** both UIs read "Uploading 1 activity and 3,000 imported · 3 other changes"

#### Scenario: A device that can't record
- **WHEN** the feed is empty on a device without GPS recording
- **THEN** both UIs say to add one manually, and neither Add menu offers Record

### Requirement: An import card reads the same in both UIs
An import card's months, count, sports and distance, and which routes it draws, SHALL be chosen once in shared code and resolved by each UI.

#### Scenario: Bob's import
- **WHEN** both UIs show Bob's import of 312 activities from June 2019 to May 2026
- **THEN** each reads "Imported 312 activities" and "Jun 2019 – May 2026", with at most four routes

### Requirement: The Search tab reads the same in both UIs
The Search tab's title for each mode, the modes' names, the prompt shown while the query is blank and the message when nothing matched
(naming the trimmed query) SHALL be chosen once in shared code and resolved by each UI.

#### Scenario: A blank query
- **WHEN** the Search tab is in Athletes mode with nothing typed
- **THEN** both UIs show the prompt and no empty message

#### Scenario: No match
- **WHEN** the query is "  zzz " and nothing matched
- **THEN** both UIs read No athletes match “zzz”.

### Requirement: The left-elsewhere choice reads the same in both UIs
What the screen after leaving on another device says SHALL be chosen once in shared code and resolved by each UI: the intro naming the
handle, and a line for each kind of work this device holds (the changes counted, an import saving, an export not saved, a recording),
only for those it holds. Counts SHALL be grouped as `ui/number-format` says.

#### Scenario: Everything pending
- **WHEN** bob's device holds 1,200 changes, an import saving, an export and a recording
- **THEN** both UIs list "1,200 changes are pending." and the import, export and recording lines, in that order

### Requirement: A route map shows the same marks in every UI
What a route map marks SHALL be decided once in shared code and drawn by each UI: the line's pieces between its breaks, smoothed, and where the start, end, stop and resume markers go, with the start and end at the ends others see when given and none where hidden. The live maps, their line previews and the feed's map images SHALL ask it.

#### Scenario: Owner's markers
- **WHEN** the owner's map draws their untrimmed track with the ends others see given
- **THEN** every UI marks the start and end at those ends, not at the track's own

### Requirement: A route map frames the same in every UI
The camera that frames a route north-up in a map of a given size, with a margin, any space a caller draws over the map's top kept clear, and never closer than the closest framing, SHALL be decided once in shared code, and the feed's map images and the native iOS map SHALL ask it.

#### Scenario: Same frame
- **WHEN** a route is framed in a 360 by 240 point map by the native iOS map and by a feed map image of that size
- **THEN** both centre it on the same point at the same zoom

### Requirement: The dive site page reads the same in both UIs
What the dive site page says SHALL be chosen once in shared code and resolved by each UI: the radius in the viewer's units, the creator, the credit and licence, the status label and help with whether Open the original shows, the Regulars card, the conditions' lines in the viewer's units, and the dives' heading and empty message.

#### Scenario: Imperial conditions
- **WHEN** a viewer in imperial units opens a site whose median water temperature is 27 °C
- **THEN** both UIs read "Water 80.6 °F" with the range in °F

### Requirement: A place's names read the same in both UIs
What a place's names section says SHALL be chosen once in shared code for every place with names: "originally", who proposed a name and its totals, the vote labels and what a vote tap sends, why voting is unavailable, and each history entry with its date and proposer.

#### Scenario: Totals
- **WHEN** a proposal has 1,200 votes for and 3 against
- **THEN** both UIs read its totals with the count grouped

### Requirement: A segment's credit reads the same in both UIs
What a segment's data credit says SHALL be chosen once in shared code: OpenStreetMap's linked credit, an agency's line with its name when
given, and the licence line when the record names one.

#### Scenario: Agency credit
- **WHEN** a segment's line came from an agency named "USFS" under "PD"
- **THEN** both UIs read "From USFS trail data" and "Data licence: PD"

### Requirement: Segment browse reads the same in both UIs
What segment browse lists and says SHALL be chosen once in shared code: each row's length, type and distance away, which message the list
shows instead of runs (locating, nowhere, zoom in, the web prompt), the capped and none-here notes, the no-match message and whether
Search everywhere is offered, and the sport and scope choices' labels.

#### Scenario: Too wide
- **WHEN** the browse map shows more than segments are searched over and no name is typed
- **THEN** both UIs ask the athlete to zoom in

### Requirement: A map of many segments behaves the same in every UI
How a map of many segments draws and answers SHALL be decided once in shared code: the lines in drawing order (lifts, then runs, then the
selected run), which run a tap selects (the nearest within the tap's reach, never a lift), and what the map reports it shows.

#### Scenario: Tap between two runs
- **WHEN** a tap lands 5 m from one run and 9 m from another, within its 15 m reach
- **THEN** the nearer run is selected

### Requirement: Accept invite reads the same in both UIs
What Accept invite says SHALL be chosen once in shared code: the intro naming the inviter, the overlap warning, the gone message, and
Accept's label while accepting.

#### Scenario: Accepting
- **WHEN** alice taps Accept
- **THEN** both UIs label the button "Accepting…" until it finishes

### Requirement: A chart's axis and labels are chosen once
What a line chart shows SHALL be chosen once in shared code and drawn by each UI: the y axis's range and round step, the x labels' places and text, each label's text in the viewer's units, the readout for each point and the description read to assistive technology.

#### Scenario: Distance labels
- **WHEN** a segment 1.2 mi long is charted for a viewer in imperial units
- **THEN** both UIs label its distance axis 0, 0.25, 0.5, 0.75 and 1 mi

### Requirement: Band runs are chosen once
Where a chart is tinted by bands, the stops SHALL be chosen once in shared code: a stop for the first point's band, then one halfway between each pair of points whose bands differ, a run shorter than three points joining the run before it.

#### Scenario: A short flat stretch
- **WHEN** two points of the flattest band sit between runs of a steeper band
- **THEN** both UIs tint them with the steeper band, with no stop between

### Requirement: The segment page reads the same in both UIs
What the segment page says SHALL be chosen once in shared code and resolved by each UI: the star's label, the length and type line, the gain, loss and grade line, the high, low and maximum grade line, the data credit and licence, the Mayor card's lines, and the efforts' heading and empty message.

#### Scenario: Imperial elevation
- **WHEN** a viewer in imperial units opens a 1.6 km segment with gain 84 m and loss 12 m
- **THEN** both UIs read its gain and loss in feet and an average grade of 4.5%

### Requirement: An effort row reads the same in both UIs
What an effort row says SHALL be chosen once in shared code for both the segment page and the activity page: its date or segment name, its time, PR, its rank's ordinal, why it isn't ranked and e-bike.

#### Scenario: Third place
- **WHEN** an effort carries rank 3
- **THEN** both UIs read "3rd" beside its time

### Requirement: A figure reads the same in both UIs
What a figure on the activity page says SHALL be chosen once in shared code and resolved by each UI: its label in full and its value with its unit in the viewer's units, a stats group's name, an avg/max row's name, a card's title, and a stream's title and unit.

#### Scenario: Imperial distance
- **WHEN** a viewer in imperial units opens a ride of 12.6 km
- **THEN** both UIs read its Distance figure as 7.83 mi

#### Scenario: A dive's gases
- **WHEN** a dive switched from air to EAN50
- **THEN** both UIs read its gas figure as "Air → EAN50"

### Requirement: A card's lines, zones and laps read the same in both UIs
What a metric card's charts, time in zones and laps table say SHALL be chosen once in shared code: each line's title where the card's doesn't name it, its average and maximum line, its description, each zone's label, name, range and time, and each lap's cells and spoken row.

#### Scenario: Power zones
- **WHEN** a ride spent 12 minutes in power zone 2, 150–200 W
- **THEN** both UIs read that zone's range as 150–200 W and its time as 12:00

### Requirement: The activity page reads the same in both UIs
What the activity page says beyond its figures SHALL be chosen once in shared code: its title, the like and likers buttons, Together with and its buttons, the owner's and others' menus, the delete and split confirmations, the export sheet, the notices after a zone, segment or dive site is added, the dive site line, and the combine suggestion's words.

#### Scenario: Liked
- **WHEN** a viewer has liked an activity with 3 likes
- **THEN** both UIs read its like button as liked, with 3

### Requirement: Threshold history's text and charts are chosen in shared code
What threshold history says (a group's heading, help and summary, a threshold's label, values and change, a value's date and source, Show all, the remove label, the chart's description and its first date) and each threshold's chart (its steps, dots, labels and the run on to today) SHALL be chosen in shared code that both UIs ask.

#### Scenario: A faster pace
- **WHEN** a Run's threshold pace rose from 3.0 to 3.2 m/s since March 2 for an athlete who reads pace per mile
- **THEN** both UIs say it got faster by the same number of seconds per mile since Mar 2

### Requirement: The type search's match rule is shared
Whether an activity type matches a type picker's query SHALL be decided by shared code that both UIs ask, given the type's and its sport's labels as each UI reads them.

#### Scenario: A token
- **WHEN** the athlete searches "sup" in either UI's type picker
- **THEN** Stand Up Paddle is listed

### Requirement: The structure tables read the same in both UIs
What the legs, lengths and sets tables say SHALL be chosen once in shared code: their headers, each row's cells (a sport's name, a stroke's or exercise's name, a time, a distance, a pace or speed, a weight), a rest's or transition's row, and each row's spoken sentence.

#### Scenario: An unknown exercise
- **WHEN** a set's exercise is a token the app has no name for, "single_arm_cable_row"
- **THEN** both UIs read it as "Single arm cable row"

### Requirement: A card's extra charts read the same in both UIs
What the power curve, the gears chart, a dive's marks and a card's source caption say SHALL be chosen once in shared code: their titles, axis labels, readouts and descriptions, each mark's label, and the caption's devices in time order.

#### Scenario: The power curve's labels
- **WHEN** a ride's power curve includes 5 seconds, 1 minute and 20 minutes
- **THEN** both UIs label those durations "5s", "1m" and "20m"

### Requirement: The combination sheet reads the same in both UIs
What the owner's combination sheet says SHALL be chosen once in shared code: its title, the members heading, each member's name and remove button, the removal confirmation, the data sources heading, each kind's name, where it comes from or that it's left out, and its switch's spoken label.

#### Scenario: A kind's source
- **WHEN** a combined ride's power comes from an Edge 840
- **THEN** both UIs read its row as Power, from Edge 840

### Requirement: The activity forms' text is chosen in shared code
What the activity forms say that follows a rule (the gear field's summary, an item's line, a collection's line and the left-out count; the type picker's groups for a query and the sub-sport choices with their labels; manual entry's unit labels; the core-charts warning; the dive site form's radius, its reasons no site can be made and its public note) SHALL be chosen in shared code that both UIs ask.

#### Scenario: A retired item
- **WHEN** alice's retired Bike, a road bike she shows to Followers, is offered on the gear sheet
- **THEN** both UIs give it the same line naming its kind, Followers and Retired

#### Scenario: The radius in imperial
- **WHEN** an athlete who reads feet sets a 100 m radius
- **THEN** both UIs show it in feet the same way

### Requirement: The import screen's text is chosen in shared code
What the import screen says that follows a rule (default titles, reading's progress, threshold changes, duplicate warnings and banners, a batch row's lines, the batch's summary, filters, selected count and Save, the comparison, the saved and after-import panels, time zone names and their search, and the new devices' lines and choices) SHALL be chosen in shared code that both UIs ask, as SHALL whether Save is offered and the default titles saving sends.

#### Scenario: An untitled morning ride
- **WHEN** a batch row of a ride started at 07:02 has no title
- **THEN** both UIs show it as Morning Ride and save it with that title

#### Scenario: Searching zones
- **WHEN** the athlete types "utc+8" in the zone search
- **THEN** both UIs offer the zones eight hours ahead of UTC
