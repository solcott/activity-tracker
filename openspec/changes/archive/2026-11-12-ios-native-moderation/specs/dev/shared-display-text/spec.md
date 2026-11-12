# Spec Delta

## ADDED Requirements

### Requirement: The Moderation page's text is chosen in shared code
What the Moderation page says that follows a rule (who may see the subject, the comment author's name with its fallback to their
handle) and whether Open is offered SHALL be chosen in shared code that both UIs ask.

#### Scenario: An author without a display name
- **WHEN** a moderator opens a comment by an athlete who has no display name
- **THEN** both UIs name the author by their `@handle`

#### Scenario: Opening
- **WHEN** a subject is opening
- **THEN** both UIs hold Open until it answers
