# graph/mutes Specification

## Purpose

Lets an athlete mute an account so that its activities and comments stop appearing for them, without the account knowing and without changing what anyone else sees.

## Requirements

### Requirement: A mute is stored privately
Muting an account SHALL write an `app.activitytracker.graph.mute` record, with the account's DID as `subject` and as its rkey, to the athlete's `interactions` space and nowhere else, creating that space if needed. Muting the same account twice SHALL leave one mute. Unmuting SHALL delete that record, and a missing record or space SHALL count as done.

#### Scenario: Mute written to the interactions space
- **WHEN** alice mutes bob
- **THEN** alice's `interactions` space holds a `graph.mute` record with rkey and `subject` bob's DID, and nothing public records it

#### Scenario: Unmute
- **WHEN** alice unmutes bob and the AppView has indexed the deletion
- **THEN** bob's activities and comments show for alice again

### Requirement: Mute refused without the space scope
A client SHALL refuse to mute or unmute, before writing anything, when the session's `space:` scope doesn't cover `graph.mute`, and SHALL report the refusal as needing to sign in again.

#### Scenario: Session from before mutes existed
- **WHEN** alice's session predates `graph.mute` in the athlete space and she taps Mute
- **THEN** nothing is written and the screen tells her to sign in again

### Requirement: Muted activities leave the muter's timeline
The AppView SHALL leave the muted account's activities out of the muter's timeline. The muter SHALL still be able to open the muted account's profile and see its activities there and by URI, under the usual visibility rules.

#### Scenario: Timeline
- **WHEN** alice follows bob and mutes him
- **THEN** alice's timeline has none of bob's activities

#### Scenario: Profile still reachable
- **WHEN** alice mutes bob and opens his profile
- **THEN** she sees his activities as she could before muting

### Requirement: Muted comments are hidden from the muter
The AppView SHALL NOT list or count, for the muter, the muted account's comments and replies on any activity. A thread whose top-level comment is muted SHALL follow `Unavailable top-level comment`.

#### Scenario: Comment on a third athlete's activity
- **WHEN** alice mutes bob and bob commented on carol's activity
- **THEN** alice isn't shown bob's comment and her count leaves it out, while carol and others see it

### Requirement: A mute changes nothing for anyone else
A mute SHALL NOT change what the muted account or any other viewer sees. It SHALL NOT change follows or tier access, and it SHALL NOT be revealed to anyone but the muter.

#### Scenario: Muted account still sees the muter
- **WHEN** alice mutes bob
- **THEN** bob still sees alice's activities he could see before, can still comment, and nothing tells him he is muted

#### Scenario: Follow still counts
- **WHEN** bob effectively follows alice and alice mutes bob
- **THEN** bob is still admitted to alice's `followers` space

### Requirement: Muting from a profile or comment
Clients SHALL offer Mute on any other athlete's profile and on any comment written by someone else, and Unmute on a profile the viewer mutes. Muting SHALL NOT ask for confirmation, because it can be undone. `getProfile` SHALL report the viewer's mute in `viewer.muting`.

#### Scenario: Mute from a profile
- **WHEN** alice taps Mute on bob's profile
- **THEN** the mute is written and the profile shows Unmute

### Requirement: A mute shows at once
Once the athlete mutes an account, the client SHALL hide its activities from the muter's timeline and its comments from the muter, at once and without waiting for the AppView, including after a reload before the AppView has caught up. A failed write SHALL restore the previous state and report the failure.

#### Scenario: Timeline updates at once
- **WHEN** alice mutes bob from his profile and goes back to her timeline
- **THEN** bob's activities are gone without waiting for the AppView

### Requirement: Muted accounts list
Settings SHALL offer a Muted accounts screen listing every account the athlete mutes, newest first, each with Unmute, from `app.activitytracker.graph.getMutes`, which the AppView SHALL serve only to the authenticated caller about their own mutes.

#### Scenario: Listing
- **WHEN** alice mutes bob and dave
- **THEN** her Muted accounts screen lists both with Unmute
