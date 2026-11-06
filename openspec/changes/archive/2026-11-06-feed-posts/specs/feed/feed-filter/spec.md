# Spec Delta

## ADDED Requirements

### Requirement: Posts in feed scopes
The Following scope with no filter SHALL show posts (`feed/posts`) alongside activities, including the viewer's own. The Mine scope SHALL show activities only. A type or date filter SHALL leave posts out.

#### Scenario: Unfiltered Following
- **WHEN** bob, who follows alice, opens Following with no filter
- **THEN** alice's posts that bob may see appear among the activities

#### Scenario: Type filter
- **WHEN** bob filters Following to rides
- **THEN** no posts appear

#### Scenario: Date filter
- **WHEN** bob filters Following to last week
- **THEN** no posts appear, even ones created last week
