# Spec Delta

## MODIFIED Requirements

### Requirement: Regulars propose from the segment screen
The segment screen SHALL offer Propose a name only when the view says the viewer may propose. Before submitting, it SHALL tell the athlete that proposing shows others, under their name, that they ride the segment regularly. It SHALL refuse a blank or over-long name, and write the proposal through the outbox. The sheet SHALL also offer Share to feed, off by default: with it on, the athlete picks Public, Followers or Mutuals and may add text, and the client writes a post sharing the proposal (`feed/posts`) through the outbox after it.

#### Scenario: Not eligible
- **WHEN** a viewer whose view doesn't allow proposing opens a segment
- **THEN** the screen offers no Propose action

#### Scenario: Proposing
- **WHEN** alice chooses Propose a name, reads the notice, enters "Wiggly Woods" and submits
- **THEN** the proposal is written to her repo through the outbox, and is kept and retried until written

#### Scenario: Proposing and sharing
- **WHEN** alice proposes "Wiggly Woods", turns on Share to feed, picks Followers, writes "Everyone already calls it this" and submits
- **THEN** the proposal is written to her repo and a post sharing it, with that text, to her `followers` space, both through the outbox

#### Scenario: Not shared
- **WHEN** alice proposes a name and leaves Share to feed off
- **THEN** no post is written

#### Scenario: Over-long text
- **WHEN** alice's share text is over 1000 graphemes
- **THEN** the sheet doesn't let her submit

#### Scenario: PDS without Spaces
- **WHEN** an athlete whose PDS doesn't support Spaces turns on Share to feed
- **THEN** only Public is offered, with an explanation that Followers and Mutuals need a server that supports private data

### Requirement: Proposers edit or delete from the segment screen
The segment screen SHALL let a proposer edit the name of their own open proposal, or delete it, and SHALL offer no edit for a closed one. Before an edit is saved, it SHALL warn that editing clears every vote, and before deleting, it SHALL ask for confirmation. It SHALL refuse an edit to a blank, over-long or unchanged name, show the change at once, and write it through the outbox. The edit sheet SHALL show the proposal's sharing and let the proposer turn it on or off and change its audience or text, which an otherwise unchanged name doesn't block. Deleting a proposal SHALL also delete its post.

#### Scenario: Editing
- **WHEN** alice edits her open proposal, accepts the warning that votes will be cleared, and saves "Wiggly Woods Loop"
- **THEN** the proposal shows "Wiggly Woods Loop" with no votes at once, and the rewrite goes to her repo through the outbox

#### Scenario: Deleting
- **WHEN** alice deletes her open proposal and confirms
- **THEN** the proposal disappears from the screen at once, the delete is written through the outbox, and Propose is offered again if she's still eligible

#### Scenario: Deleting a shared proposal
- **WHEN** alice deletes her open proposal that she shared with Followers, and confirms
- **THEN** the post's delete is written through the outbox along with the proposal's, and the post leaves her profile and her followers' timelines

#### Scenario: Sharing changed without renaming
- **WHEN** alice opens the edit sheet for her Public shared proposal, changes only its audience to Mutuals and saves
- **THEN** the proposal isn't rewritten and keeps its votes, and the post moves to her `mutuals` space

#### Scenario: Unsharing
- **WHEN** alice turns Share to feed off for her shared proposal and saves
- **THEN** the post's delete is written through the outbox, and the proposal stays open with its votes

#### Scenario: Someone else's proposal
- **WHEN** bob views alice's open proposal
- **THEN** the screen offers him no edit or delete

#### Scenario: Own adopted name
- **WHEN** alice views the name history entry for her adopted proposal
- **THEN** the screen offers her no edit
