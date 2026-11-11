# Spec Delta

## ADDED Requirements

### Requirement: Blocking from a native profile
On iOS someone else's profile SHALL offer Block in its navigation bar's menu, and Unblock there and in place of Follow on a profile the viewer blocks, as the shared profile does. Block SHALL ask for the shared confirmation first, saying the account won't be told; Unblock SHALL NOT. Where the viewer's only block is on Bluesky, the profile SHALL offer neither and SHALL show the shared note instead. A failed block or unblock SHALL show above the header.

#### Scenario: Block after confirming
- **WHEN** alice chooses Block in bob's profile menu on iOS and confirms
- **THEN** the block is written, and the profile shows Unblock and no activities

#### Scenario: Blocked on Bluesky
- **WHEN** alice's only block of dave is an `app.bsky.graph.block`
- **THEN** dave's profile on iOS offers no Block or Unblock and shows that the block can only be removed on Bluesky
