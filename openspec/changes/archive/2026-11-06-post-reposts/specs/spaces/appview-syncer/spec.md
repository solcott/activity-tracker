# Spec Delta

## MODIFIED Requirements

### Requirement: Only interactions are indexed from interactions spaces
From `interactions` spaces the AppView SHALL index only `feed.like`, `feed.comment`, `feed.commentHide`, `feed.repost`, `graph.block` and `graph.mute` records.

#### Scenario: Zones in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes no zones from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it

#### Scenario: Repost indexed from an interactions space
- **WHEN** a synced `interactions` space's ops include a `feed.repost` of a Followers post
- **THEN** the AppView indexes it as the space's authority's repost, and deleting the record, or the space being reported deleted, removes it

#### Scenario: Block indexed from an interactions space
- **WHEN** a synced `interactions` space's ops include a `graph.block` write whose rkey is its `subject`
- **THEN** the AppView indexes a block of that subject by the space's authority, and deleting the record removes only that block

#### Scenario: Mute indexed from an interactions space
- **WHEN** a synced `interactions` space's ops include a `graph.mute` write whose rkey is its `subject`
- **THEN** the AppView indexes a mute of that subject by the space's authority, and deleting the record removes only that mute

#### Scenario: Block or mute under another rkey ignored
- **WHEN** a `graph.block` or `graph.mute` in an `interactions` space has an rkey other than its `subject`
- **THEN** the AppView indexes nothing from it

#### Scenario: Interactions space deleted
- **WHEN** an athlete's `interactions` space is reported deleted
- **THEN** every block and mute indexed from it is removed, and their Bluesky blocks still apply
