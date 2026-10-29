# segments/segment-names Specification

## Purpose

Defines community names for segments: who may propose a new name and vote on it, the records that hold proposals and votes, when a proposal is adopted, and how a segment's current name, original name and name history are served and shown.

## Requirements

### Requirement: A name proposal is a public record
A name proposal SHALL be a `feed.segmentNameProposal` record in its proposer's repo, naming the segment by URI and holding a name that's non-empty after trimming and at most 100 graphemes. The AppView SHALL ignore one in any space, one with an invalid name, and one naming a segment it hasn't indexed.

#### Scenario: Proposal written
- **WHEN** alice proposes "Wiggly Woods" for a segment
- **THEN** her repo gains a `feed.segmentNameProposal` naming the segment's URI and "Wiggly Woods"

#### Scenario: Blank name
- **WHEN** a proposal's name is only spaces
- **THEN** the AppView ignores it

#### Scenario: Proposal in a space
- **WHEN** a `feed.segmentNameProposal` appears in an athlete's `interactions` space
- **THEN** the AppView ignores it

### Requirement: Only regulars may propose
The AppView SHALL accept a proposal only if, when it's indexed, the segment is `active` and the proposer has efforts on it on at least a configured number of distinct local dates, 5 by default. Efforts of every visibility SHALL count, but only efforts within the sport group's speed cap. It SHALL ignore any other proposal, and SHALL NOT drop an accepted proposal when the proposer later loses efforts.

#### Scenario: Five days of efforts
- **WHEN** alice has efforts on a segment on 5 different dates, two of them from Only me activities, and proposes a name
- **THEN** the AppView accepts the proposal

#### Scenario: Five efforts in one day
- **WHEN** bob has 5 efforts on a segment, all from one ride, and proposes a name
- **THEN** the AppView ignores the proposal

#### Scenario: Proposed segment
- **WHEN** an eligible athlete proposes a name for a segment whose status is `proposed`
- **THEN** the AppView ignores the proposal

### Requirement: A proposal must offer a new name
The AppView SHALL ignore a proposal whose name, trimmed and compared case-insensitively, equals the segment's current name or the name of another open proposal on the segment. It SHALL accept one that equals an earlier name, the original included.

#### Scenario: Same as the current name
- **WHEN** a segment's current name is "Wiggly Woods" and alice proposes "wiggly woods"
- **THEN** the AppView ignores the proposal

#### Scenario: Back to the original
- **WHEN** a segment's original name is "Trail 214 Connector", its current name is "Wiggly Woods", and alice proposes "Trail 214 Connector"
- **THEN** the AppView accepts it as an ordinary proposal

### Requirement: One open proposal per athlete per segment
The AppView SHALL ignore a proposal from an athlete who already has an open proposal on the same segment. Deleting an open proposal SHALL withdraw it, along with its votes.

#### Scenario: Second proposal
- **WHEN** alice has an open proposal on a segment and writes another for the same segment
- **THEN** the AppView ignores the second one

#### Scenario: Withdrawn
- **WHEN** alice deletes her open proposal
- **THEN** it's no longer served and its votes no longer count, and she may propose again

### Requirement: Editing a proposal's name clears its votes
When a proposer rewrites their open proposal with a different name, the AppView SHALL take the new name, drop every vote cast before the edit, and restart its time open. An edit SHALL pass only the name checks of a new proposal, not eligibility or the cooldown. An edit that fails them SHALL leave the proposal as it was, with its votes. Only open proposals SHALL be editable: the AppView SHALL ignore a rewrite of a closed proposal.

#### Scenario: Edited
- **WHEN** alice's open proposal "Wiggly Woods" has 4 up and 1 down votes, and she edits it to "Wiggly Woods Loop"
- **THEN** it's served as "Wiggly Woods Loop" with no votes, opened at the time of the edit

#### Scenario: Edit to the current name
- **WHEN** alice edits her open proposal to the segment's current name
- **THEN** the AppView keeps the proposal's previous name and its votes

#### Scenario: Rewrite after adoption
- **WHEN** alice's proposal was adopted and a client rewrites its record with another name anyway
- **THEN** the AppView ignores the rewrite, and the segment's name and history don't change

### Requirement: Proposals pause after an adoption
For a configured period after a segment adopts a name, 90 days by default, the AppView SHALL ignore new proposals on that segment.

#### Scenario: During the cooldown
- **WHEN** a segment adopted a name 30 days ago and an eligible athlete proposes another
- **THEN** the AppView ignores the proposal

#### Scenario: After the cooldown
- **WHEN** a segment adopted a name 91 days ago and an eligible athlete proposes another
- **THEN** the AppView accepts the proposal

### Requirement: Votes are private records
A vote SHALL be a `feed.segmentNameVote` record in the voter's `interactions` space, naming the proposal and whether it's up or down. The AppView SHALL ignore a vote in a repo or a tier space. Of a voter's votes on one proposal it SHALL count only the latest, and deleting it SHALL remove the vote. The AppView SHALL never serve who voted or how, except a viewer's own votes to that viewer.

#### Scenario: Vote written
- **WHEN** bob votes up on a proposal
- **THEN** his `interactions` space gains a `feed.segmentNameVote` naming the proposal, and his repo gains nothing

#### Scenario: Changed vote
- **WHEN** bob votes up on a proposal and later votes down on it
- **THEN** the proposal counts one down vote from bob

#### Scenario: Vote in a repo
- **WHEN** a `feed.segmentNameVote` appears in an athlete's repo
- **THEN** the AppView ignores it

### Requirement: Only riders may vote
A proposal's totals SHALL count a vote only while its voter has at least one effort on the segment, of any visibility. A vote by the proposer on their own proposal SHALL NOT count. A vote on a closed proposal SHALL NOT change its totals.

#### Scenario: Voter without efforts
- **WHEN** carol, who has no efforts on a segment, votes up on a proposal for it
- **THEN** the proposal's totals don't change

#### Scenario: Voter's efforts arrive later
- **WHEN** carol votes before her first activity on the segment is matched, and it's matched later
- **THEN** her vote counts from then on

### Requirement: A proposal is adopted once enough riders agree
The AppView SHALL adopt an open proposal once it has been open at least 7 days, its up votes reach the larger of 5 and 10% (rounded up) of the distinct athletes with efforts on the segment dated in the last 365 days, and at least two-thirds of its counted votes are up. It SHALL check at least hourly, only for `active` segments. Each threshold SHALL be configurable.

#### Scenario: Quiet trail
- **WHEN** a segment has 20 riders in the last year and a proposal open 8 days has 5 up and 1 down
- **THEN** the AppView adopts it

#### Scenario: Busy trail
- **WHEN** a segment has 300 riders in the last year and a proposal open 8 days has 25 up and 2 down
- **THEN** the AppView doesn't adopt it until it has at least 30 up votes

#### Scenario: Too soon
- **WHEN** a proposal open 3 days already has 40 up and no down votes
- **THEN** the AppView doesn't adopt it before it has been open 7 days

### Requirement: Adopting closes the segment's other proposals
When the AppView adopts a proposal, it SHALL close every other open proposal on the segment as superseded. When several qualify in the same check, it SHALL adopt the one with the highest up-minus-down score, and among equal scores the one opened first.

#### Scenario: Two qualify at once
- **WHEN** two proposals on a segment both qualify in one check, one with a score of 12 and one with 9
- **THEN** the one scoring 12 is adopted and the other is closed as superseded

### Requirement: Proposals are rejected or expire
The AppView SHALL close an open proposal as rejected once its up-minus-down score reaches −5, and as expired once it has been open 90 days without being adopted. A closed proposal SHALL take no more votes and SHALL NOT be listed as open. Both values SHALL be configurable.

#### Scenario: Voted down
- **WHEN** a proposal has 2 up and 7 down votes
- **THEN** it's closed as rejected

#### Scenario: Never decided
- **WHEN** a proposal is still open 90 days after it was accepted
- **THEN** it's closed as expired

### Requirement: A segment has a current and an original name
A segment's original name SHALL be its record's name. Its current name SHALL be the name of its latest adopted proposal still in effect, or the original name when there is none. Changing the record's name SHALL change only the original name.

#### Scenario: Nothing adopted
- **WHEN** a segment has no adopted proposal
- **THEN** its current name and original name are both its record's name

#### Scenario: Importer refines a renamed segment
- **WHEN** a segment's current name is "Wiggly Woods" and the importer's `refine` rewrites its record name from "Trail 214" to "Trail 214 Connector"
- **THEN** its current name stays "Wiggly Woods" and its original name becomes "Trail 214 Connector"

### Requirement: Every segment view shows the current name
Wherever the AppView serves a segment, in full or in brief, as in search results, efforts on an activity, leaderboards, Mayors boards and starred segments, it SHALL serve the current name as the segment's name. The full view SHALL also give the original name.

#### Scenario: Effort on an activity
- **WHEN** an activity's effort is on a segment whose current name is "Wiggly Woods"
- **THEN** the effort's segment is named "Wiggly Woods"

### Requirement: A segment's name history is served
`feed.getSegment` SHALL list the segment's adopted names still in effect, newest first, each with its name, the date it was adopted, its final up and down totals and its proposer. It SHALL leave out the proposer for a viewer who has blocked them or been blocked by them, and for a proposer whose proposal record has since been deleted.

#### Scenario: Two renames
- **WHEN** a segment adopted "Wiggly Woods" in May and "Rooty Run" in September
- **THEN** its history lists "Rooty Run" then "Wiggly Woods", each with its date, totals and proposer

#### Scenario: Proposer deletes an adopted proposal
- **WHEN** alice deletes the record of her proposal that was adopted
- **THEN** the segment keeps its name and history entry, without alice as proposer

### Requirement: Open proposals are served with their totals
`feed.getSegment` SHALL list a segment's open proposals with each one's name, proposer, opening date and up and down totals. For a signed-in viewer it SHALL also give the viewer's own vote on each. It SHALL leave out proposals by athletes the viewer has blocked or muted, or who have blocked the viewer.

#### Scenario: Viewer voted
- **WHEN** bob, who voted down on alice's proposal, opens the segment
- **THEN** he sees alice's proposal with its totals and his own down vote

#### Scenario: Blocked proposer
- **WHEN** bob has blocked alice and opens a segment with her open proposal
- **THEN** her proposal isn't listed

### Requirement: Viewers learn whether they may propose and vote
For a signed-in viewer, `feed.getSegment` SHALL say whether they may propose a name now (the segment is `active`, not in a cooldown, they're a regular, and they have no open proposal on it) and whether they may vote (they have at least one effort on it). It SHALL give this only to that viewer.

#### Scenario: Eligible viewer
- **WHEN** alice, with efforts on 6 dates and no open proposal, opens an active segment outside any cooldown
- **THEN** her view says she may propose and may vote

#### Scenario: Signed out
- **WHEN** a signed-out viewer opens a segment
- **THEN** the view says nothing about proposing or voting

### Requirement: Labels remove a proposal or its name
A proposal whose record or proposer's account carries `!hide` or `!takedown` SHALL NOT be listed, counted or adopted. If it was already adopted, its name SHALL leave the history and stop being in effect, so the segment's current name falls back to the previous adopted name in effect, or the original. Negating the label SHALL restore it.

#### Scenario: Offensive name adopted
- **WHEN** the adopted name "Rooty Run" replaced "Wiggly Woods", and its proposal is then labelled `!takedown`
- **THEN** the segment's current name is "Wiggly Woods" again and "Rooty Run" leaves its history

#### Scenario: Label negated
- **WHEN** that `!takedown` is negated
- **THEN** the segment's current name is "Rooty Run" again

### Requirement: The segment screen shows its names
The segment screen SHALL show the current name and, when it differs, the original name as "originally …". It SHALL show the name history when there is one, and the open proposals with their totals, in the content-loading pattern.

#### Scenario: Renamed segment
- **WHEN** a viewer opens a segment named "Wiggly Woods" whose original name is "Trail 214 Connector"
- **THEN** the screen's title is "Wiggly Woods", with "originally Trail 214 Connector" beneath it

### Requirement: Regulars propose from the segment screen
The segment screen SHALL offer Propose a name only when the view says the viewer may propose. Before submitting, it SHALL tell the athlete that proposing shows others, under their name, that they ride the segment regularly. It SHALL refuse a blank or over-long name, and write the proposal through the outbox.

#### Scenario: Not eligible
- **WHEN** a viewer whose view doesn't allow proposing opens a segment
- **THEN** the screen offers no Propose action

#### Scenario: Proposing
- **WHEN** alice chooses Propose a name, reads the notice, enters "Wiggly Woods" and submits
- **THEN** the proposal is written to her repo through the outbox, and is kept and retried until written

### Requirement: Proposers edit or delete from the segment screen
The segment screen SHALL let a proposer edit the name of their own open proposal, or delete it, and SHALL offer no edit for a closed one. Before an edit is saved, it SHALL warn that editing clears every vote, and before deleting, it SHALL ask for confirmation. It SHALL refuse an edit to a blank, over-long or unchanged name, show the change at once, and write it through the outbox.

#### Scenario: Editing
- **WHEN** alice edits her open proposal, accepts the warning that votes will be cleared, and saves "Wiggly Woods Loop"
- **THEN** the proposal shows "Wiggly Woods Loop" with no votes at once, and the rewrite goes to her repo through the outbox

#### Scenario: Deleting
- **WHEN** alice deletes her open proposal and confirms
- **THEN** the proposal disappears from the screen at once, the delete is written through the outbox, and Propose is offered again if she's still eligible

#### Scenario: Someone else's proposal
- **WHEN** bob views alice's open proposal
- **THEN** the screen offers him no edit or delete

#### Scenario: Own adopted name
- **WHEN** alice views the name history entry for her adopted proposal
- **THEN** the screen offers her no edit

### Requirement: Riders vote from the segment screen
When the view says the viewer may vote, the segment screen SHALL let them vote up or down on each open proposal, change their vote, or take it back. Votes SHALL be written to their `interactions` space through the outbox and shown at once. When their PDS doesn't support Spaces, voting SHALL be unavailable with an explanation.

#### Scenario: Up vote
- **WHEN** bob taps up on alice's proposal
- **THEN** the proposal shows his up vote and its up total one higher at once, and the vote is written to his `interactions` space

#### Scenario: PDS without Spaces
- **WHEN** a rider whose PDS doesn't support Spaces opens a segment with open proposals
- **THEN** the vote controls are disabled and say voting needs a server that supports private data
