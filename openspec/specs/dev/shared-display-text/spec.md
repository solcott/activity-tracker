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
