# Spec Delta

## ADDED Requirements

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
