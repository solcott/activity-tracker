# Spec Delta

## ADDED Requirements

### Requirement: Blocked accounts' posts are hidden
A block, in either direction, SHALL hide each athlete's posts from the other everywhere they are served, at every tier, and SHALL hide a post's text and card from a viewer even when it shares a proposal that viewer can see on the segment.

#### Scenario: Timeline
- **WHEN** bob follows alice, alice shares a proposal publicly, and bob then blocks alice
- **THEN** alice's post leaves bob's timeline

#### Scenario: Blocked by the author
- **WHEN** alice blocks bob and bob opens Posts on alice's profile
- **THEN** bob sees none of her posts
