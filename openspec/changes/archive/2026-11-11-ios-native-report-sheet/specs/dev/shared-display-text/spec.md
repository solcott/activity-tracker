# Spec Delta

## ADDED Requirements

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
