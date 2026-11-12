# Spec Delta

## MODIFIED Requirements

### Requirement: A comment's actions are decided once
Which actions a comment offers (Reply, Delete, Hide or Unhide, Mute author, Block author, Report), in what order, and the words of its hidden note and of the delete and block confirmations SHALL be decided by shared code that both UIs ask. What a comment's Report opens (the report sheet for that comment, naming its author) SHALL be built there too, so neither UI builds a report target.

#### Scenario: Someone else's comment on someone else's post
- **WHEN** alice sees carol's comment on bob's post with a labeler configured
- **THEN** both UIs decide Reply, Mute author, Block author and Report, and no Delete or Hide

#### Scenario: A comment's Report
- **WHEN** alice chooses Report on carol's comment in either UI
- **THEN** the report sheet opens for carol's comment, by its URI and CID, naming carol
