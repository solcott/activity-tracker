# Spec Delta

## ADDED Requirements

### Requirement: Comment hides indexed from interactions spaces
A `feed.commentHide` written to an athlete's `interactions` space SHALL be indexed as that athlete's hide of the comment it names. Deleting it SHALL remove only that copy of the hide. Deleting the space SHALL remove every hide indexed from it and no others.

#### Scenario: Hide appears
- **WHEN** alice puts a `feed.commentHide` for bob's comment on her Followers activity in her `interactions` space
- **THEN** after the notification, the comment is no longer counted for anyone, and is listed only for alice and bob, marked as hidden

#### Scenario: Hide deleted
- **WHEN** alice deletes that hide from her `interactions` space
- **THEN** after the notification, the comment is counted and listed as before

#### Scenario: Delete from the other place
- **WHEN** alice deletes the same rkey from her repo, where it isn't
- **THEN** the hide indexed from her `interactions` space stays, and the comment stays hidden

#### Scenario: Interactions space deleted
- **WHEN** alice deletes her `interactions` space
- **THEN** the comments she hid only from that space are no longer hidden, while her hides in her repo stay

#### Scenario: Backfill
- **WHEN** the AppView first syncs an `interactions` space that already holds hides
- **THEN** it indexes those hides along with the space's likes and comments

## MODIFIED Requirements

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity` and `feed.activityStreams` records. From `interactions` spaces it SHALL index only `feed.like`, `feed.comment` and `feed.commentHide` records. It SHALL NOT store `actor.privateDetails`, `graph.followDecline` or `actor.activityDefaults` records from any space.

#### Scenario: Private details ignored
- **WHEN** a synced space's ops include an `actor.privateDetails` write
- **THEN** the AppView stores nothing from it

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Comment in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.comment` write
- **THEN** the AppView indexes no comment from it

#### Scenario: Comment hide in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.commentHide` write
- **THEN** the AppView indexes no hide from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it
