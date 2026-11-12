# Spec Delta

## ADDED Requirements

### Requirement: A site's names are native on iOS
On iOS the dive site page SHALL show the site's names in the shared screen's words: Propose a name when the viewer may propose, the open proposals with who proposed each and their totals, votes on others' proposals, Edit and Delete on the viewer's own, the last failure, and the history behind Name history.

#### Scenario: Voting
- **WHEN** a diver taps Vote for on bob's open proposal
- **THEN** the vote is sent and the control shows it chosen, and a second tap takes it back

#### Scenario: Voting unavailable
- **WHEN** a diver's session can't vote
- **THEN** the votes are disabled and the section says why

### Requirement: The native site page opens the native name sheet
Propose and Edit on the native page SHALL open the native place-name sheet, and Delete SHALL ask in the system's alert before deleting.

#### Scenario: Own proposal
- **WHEN** alice opens a site where her proposal is open and taps Delete proposal
- **THEN** the system's alert asks before her proposal is deleted

#### Scenario: Proposing
- **WHEN** an eligible viewer taps Propose a name
- **THEN** the native place-name sheet opens over the page
