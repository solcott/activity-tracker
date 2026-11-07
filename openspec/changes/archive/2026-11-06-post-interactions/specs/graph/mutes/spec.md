# Spec Delta

## MODIFIED Requirements

### Requirement: Muted comments are hidden from the muter
The AppView SHALL NOT list or count, for the muter, the muted account's comments and replies on any activity or post. A thread whose top-level comment is muted SHALL follow `Unavailable top-level comment`.

#### Scenario: Comment on a third athlete's activity
- **WHEN** alice mutes bob and bob commented on carol's activity
- **THEN** alice isn't shown bob's comment and her count leaves it out, while carol and others see it

#### Scenario: Comment on a third athlete's post
- **WHEN** alice mutes bob and bob commented on carol's post
- **THEN** alice isn't shown bob's comment on the post and her count leaves it out, while carol and others see it
