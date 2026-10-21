# moderation/private-subjects Specification

## Purpose

Lets moderators review reported non-public content, which the labeler can't read, through the AppView, only while it's under review, and with every view recorded.

## Requirements

### Requirement: Moderator view of a subject
The AppView SHALL serve `app.activitytracker.moderation.getSubject`, which takes a record URI and returns the record as the AppView indexed it, at any visibility, with links to its photos and its author. It SHALL require service auth from a DID on the app's labeler team with the moderator or admin role, as the labeler reports it.

#### Scenario: Moderator opens a reported Followers activity
- **WHEN** a moderator on the labeler team requests bob's Followers activity that has an open report
- **THEN** the AppView returns the activity and working links to its photos

#### Scenario: Moderator in a block with the author
- **WHEN** a moderator whom bob blocks requests bob's reported Followers activity
- **THEN** the AppView returns it, because moderator access doesn't go through viewer visibility

#### Scenario: Not a moderator
- **WHEN** alice, who isn't on the labeler team, calls `getSubject`
- **THEN** the AppView refuses the request

### Requirement: Only subjects under review
The AppView SHALL answer `getSubject` for a non-public subject only while the app's labeler reports an open report or an open review on that subject or its author. To check, the AppView SHALL use its own read-only (triage) membership of the labeler team. Otherwise it SHALL answer as not found.

#### Scenario: No report
- **WHEN** a moderator requests a Mutuals activity that nobody has reported
- **THEN** the AppView answers not found

#### Scenario: Report resolved
- **WHEN** the report on a Followers activity is resolved and closed
- **THEN** a later `getSubject` for it answers not found

#### Scenario: Labeler unreachable
- **WHEN** the AppView can't reach the labeler to check the subject's status
- **THEN** it refuses the request rather than serve the subject

### Requirement: Photo links for moderators are short-lived
Photo links in a `getSubject` answer SHALL be signed for the moderator. They SHALL stop working within the same limits as viewers' signed photo URLs.

#### Scenario: Link expires
- **WHEN** a moderator's photo link from `getSubject` is more than 30 minutes old
- **THEN** the AppView refuses it

### Requirement: Every moderator view is audit-logged
The AppView SHALL record every `getSubject` call that returns a non-public subject: the moderator's DID, the subject URI, the open report it relied on, and the time. It SHALL keep these records for at least one year. Athletes' content SHALL NOT be copied into the log.

#### Scenario: Audit entry
- **WHEN** a moderator views bob's reported Only-me activity
- **THEN** the audit log holds the moderator's DID, the activity's URI, the report and the time, and none of the activity's content

### Requirement: Moderators open subjects in the app
When the signed-in account is on the labeler team with the moderator or admin role, the desktop and web clients SHALL offer a Moderation entry in Settings. It SHALL take a subject URI, show the subject through `getSubject`, and show the AppView's not-found answer as "not under review". Other accounts SHALL NOT be offered the entry.

#### Scenario: Moderator opens a URI
- **WHEN** a moderator pastes the URI of a reported Followers activity from the labeler's queue
- **THEN** the app shows the activity and its photos

#### Scenario: Athlete without the role
- **WHEN** alice, who isn't on the labeler team, opens Settings
- **THEN** no Moderation entry is offered

### Requirement: Public subjects need no gate
`getSubject` SHALL return a Public subject to any labeler team member without checking for a report, and without an audit entry, because anyone can already read it.

#### Scenario: Public activity
- **WHEN** a moderator requests a Public activity with no report
- **THEN** the AppView returns it
