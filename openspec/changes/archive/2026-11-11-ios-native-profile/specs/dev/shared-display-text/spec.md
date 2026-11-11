# Spec Delta

## ADDED Requirements

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
