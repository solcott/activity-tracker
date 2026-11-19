# Spec Delta

## Purpose

Gives the AppView's admins a segments page for finding segments and seeing their derived state, pins, proposals and importer runs, and gives operators audited actions to rerun a segment's passes, rematch it and pin or unpin its status or name, without revealing any athlete's private data.

## ADDED Requirements

### Requirement: Admins search segments
The admin site SHALL have a Segments page where any admin finds indexed segments by AT-URI or record key, by name, by status, by sport group and kind, and by importer region, alone or combined. Results SHALL be paged, at most 50 a page, and each SHALL show the segment's URI, author DID, sport group, kind, status and served name.

#### Scenario: By URI
- **WHEN** an admin searches the AT-URI of an indexed segment
- **THEN** the results hold that segment alone

#### Scenario: By name and status
- **WHEN** an admin searches "wiggly" with status `proposed`
- **THEN** the results hold the proposed segments whose current or original name contains "wiggly", ignoring case

#### Scenario: By region
- **WHEN** an admin searches region `us-vermont`
- **THEN** the results hold the segments the importer published from candidates staged for `us-vermont`

#### Scenario: Nothing found
- **WHEN** no indexed segment matches the search
- **THEN** the page says no segment was found

### Requirement: Names shown only where the AppView serves them
The admin site SHALL show a segment's original or current name only while the AppView would serve the segment to a signed-out viewer. For a segment it wouldn't, it SHALL show the URI, status and that it is withheld, and a name search SHALL NOT match it. A pinned free-text name, being operator data, SHALL always be shown on its segment's page.

#### Scenario: Taken-down segment
- **WHEN** a segment carries `!takedown` and an admin looks it up by URI
- **THEN** its page shows its URI and status and says its name is withheld

#### Scenario: Name search skips it
- **WHEN** an admin searches a word only that taken-down segment's name holds
- **THEN** no segment is found

### Requirement: The segment page shows derived and pinned state
A segment's page SHALL show its URI, author DID, sport group, kind, distance, first-indexed and last-indexed times, original and current names, status, the segment it duplicates, its segment group, and the matcher job kinds queued for it. It SHALL show each of its status and name pins: the value, whether it is in effect, the operator who pinned it, when, and its note.

#### Scenario: Pinned archived
- **WHEN** an admin opens a segment an operator pinned `archived` with a note
- **THEN** the page shows status `archived` and the pin with its operator, time, note and that it is in effect

#### Scenario: Dormant duplicate pin
- **WHEN** a segment's `duplicate` pin names a segment that isn't indexed
- **THEN** the page shows the pin as not in effect, and the status as derived

### Requirement: The segment page counts efforts only
A segment's page SHALL show how many efforts it has and how many distinct athletes they come from, over every visibility, and how many athletes make it active. It SHALL NOT show any effort, its time, date or activity, any athlete who has one, any track, nor any count split by visibility, athlete or date.

#### Scenario: Private efforts counted
- **WHEN** a proposed segment has one public effort and one Only me effort by different athletes
- **THEN** its page shows 2 efforts from 2 athletes and the threshold, and nothing else about either effort

### Requirement: The segment page shows elevation as served
A segment's page SHALL show its served elevation and where it comes from, its terrain gain, loss and maximum grade or that terrain hasn't covered it, and how many athletes' efforts carry elevation. It SHALL show effort-derived gain and loss only once they come from as many athletes as the AppView requires before serving them.

#### Scenario: One athlete's climb
- **WHEN** a segment's efforts with altitude come from one athlete and the threshold is three
- **THEN** its page shows 1 athlete with effort elevation and no effort-derived gain or loss

### Requirement: Admins see a segment's name proposals with totals
A segment's page SHALL list its name proposals with each one's URI, proposer DID, state, opening and closing times, and up and down totals: the counted totals for an open proposal, the final ones for a closed proposal. It SHALL show a proposal's name only while the AppView would list it to a signed-out viewer. It SHALL NOT show who voted or how.

#### Scenario: Open proposal
- **WHEN** an admin opens a segment with an open proposal that has 4 up and 1 down counted votes
- **THEN** the page lists it as open with 4 up and 1 down, and no voter

#### Scenario: Hidden proposal
- **WHEN** a proposal carries `!hide`
- **THEN** the page lists its URI, state and totals and says its name is withheld

### Requirement: Admins see importer runs by region
The Segments page SHALL list, for each region with staged candidates, each import revision staged there with its candidate count, how many of them are ridden and not yet published, how many are published, and when it last staged one. It SHALL NOT show how many riders any candidate has, who rode it or when. Without a configured segment importer it SHALL say so.

#### Scenario: Run in progress
- **WHEN** a new run for `us-vermont` has staged some batches and isn't finished
- **THEN** `us-vermont` lists both revisions, each with its counts

#### Scenario: No rider counts
- **WHEN** a candidate has riders but fewer than the threshold
- **THEN** no admin page shows that it has any

### Requirement: Admins see pins on missing segments
The Segments page SHALL list the pins whose segment isn't indexed, paged, with the segment URI, the pin's kind and value, who pinned it and when. Operators SHALL be able to unpin each.

#### Scenario: Deleted pinned segment
- **WHEN** a segment pinned `archived` is deleted by its creator
- **THEN** its pin is listed as on a missing segment, and an operator can unpin it

### Requirement: Operators rerun a segment's status pass
An operator SHALL be able to run the status pass for one segment at once, applying exactly the rules of the periodic pass: a proposed segment with enough athletes becomes active, one still proposed past the archive age becomes archived with its duplicates checked again, and a status pin is left alone. The result SHALL say the status before and after.

#### Scenario: Promoted now
- **WHEN** a proposed segment has efforts from 3 athletes with the threshold at 3, and an operator runs its status pass
- **THEN** it is `active`, and the result says `proposed` to `active`

#### Scenario: Pinned
- **WHEN** an operator runs the status pass for a segment pinned `archived`
- **THEN** its status stays `archived`

### Requirement: Operators rerun a segment's naming pass
An operator SHALL be able to run the naming pass for one segment at once, applying exactly the rules of the periodic pass to its open proposals and then recomputing its current name. The result SHALL say which proposals changed state and the current name, as shown under the name rule.

#### Scenario: Adopted now
- **WHEN** an active segment's open proposal meets the adoption rules and an operator runs its naming pass
- **THEN** the proposal is adopted, the others are superseded, and the segment's current name is the proposal's

### Requirement: Operators recompute a segment's elevation
An operator SHALL be able to recompute a segment's elevation: its effort-derived elevation at once, and its terrain elevation queued, when terrain tiles are configured. The result SHALL say whether terrain was queued.

#### Scenario: No terrain tiles
- **WHEN** an operator recomputes elevation on an AppView without terrain tiles
- **THEN** the effort-derived elevation is recomputed and the result says terrain isn't configured

### Requirement: Operators rematch a segment
An operator SHALL be able to queue a live segment to be matched against the usable segment tracks that pass near it, as when it was indexed. Rematching a segment that isn't live SHALL be refused and change nothing.

#### Scenario: Live segment
- **WHEN** an operator rematches an active segment
- **THEN** a match job is queued for it

#### Scenario: Archived segment
- **WHEN** an operator rematches an archived segment
- **THEN** the action is refused as not live and nothing is queued

### Requirement: Operators pin and unpin from the segment page
An operator SHALL be able to pin a segment's status (`active`, `archived`, or `duplicate` of a named segment) or name (free text or one of its proposals), with an optional note, and unpin either, only through the segment-pins rules. A refused pin SHALL change nothing and show why. A `duplicate` pin SHALL be refused unless the operator confirms that the segment's efforts will be removed.

#### Scenario: Pin archived
- **WHEN** an operator pins an active segment `archived` with a note
- **THEN** the segment is `archived`, its page shows the pin with the operator's DID and the note

#### Scenario: Invalid pin
- **WHEN** an operator pins a `run` segment `duplicate` of a `ride` segment
- **THEN** nothing changes and the result says the target is of another sport group or kind

#### Scenario: Duplicate unconfirmed
- **WHEN** an operator submits a `duplicate` pin without confirming
- **THEN** nothing changes and the result asks for the confirmation

#### Scenario: Unpin nothing
- **WHEN** an operator unpins the name of a segment with no name pin
- **THEN** nothing changes and the result says there was no pin

### Requirement: Segment actions are operator-only and audited
Every segment action SHALL be a POST that only an operator with the session's CSRF token may run. Each attempt SHALL be recorded in the audit log with the segment's URI as its target, the action's fields as its parameters, and its outcome: `ok`, the refusal reason for a refused pin, rematch or unpin, or the failure.

#### Scenario: Viewer posts a pin
- **WHEN** a `viewer` posts a status pin for a segment
- **THEN** the AppView answers `403`, the segment is unchanged, and the audit log records the refusal

#### Scenario: Refused pin audited
- **WHEN** an operator's pin is refused as invalid
- **THEN** the audit log records the pin's target and fields with the refusal as its outcome, not `ok`

#### Scenario: Viewer's page
- **WHEN** a `viewer` opens a segment's page
- **THEN** it shows no action controls
