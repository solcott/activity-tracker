# Spec Delta

## MODIFIED Requirements

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity` and `feed.activityStreams` records. From `interactions` spaces it SHALL index only `feed.like` and `feed.comment` records. It SHALL NOT store `actor.privateDetails`, `graph.followDecline` or `actor.activityDefaults` records from any space.

#### Scenario: Private details ignored
- **WHEN** a synced space's ops include an `actor.privateDetails` write
- **THEN** the AppView stores nothing from it

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Comment in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.comment` write
- **THEN** the AppView indexes no comment from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it

## ADDED Requirements

### Requirement: Comments indexed from interactions spaces
A `feed.comment` written to an athlete's `interactions` space SHALL be indexed as that athlete's comment on its subject, with the audience it records. Deleting it SHALL remove only that comment. Deleting the space SHALL remove every comment indexed from it and no others.

#### Scenario: Comment appears
- **WHEN** bob puts a `feed.comment` written for Followers on alice's Followers activity in his `interactions` space, and bob is alice's effective follower
- **THEN** after the notification, the comment is counted and listed for alice and her effective followers

#### Scenario: Comment deleted
- **WHEN** bob deletes that comment from his `interactions` space
- **THEN** after the notification, it's no longer counted or listed

#### Scenario: Delete from the other place
- **WHEN** bob deletes the same rkey from his repo, where it isn't
- **THEN** the comment indexed from his `interactions` space stays

#### Scenario: Interactions space deleted
- **WHEN** bob deletes his `interactions` space
- **THEN** the AppView no longer counts or lists the comments indexed from it, while bob's repo comments on Public activities stay

#### Scenario: Backfill
- **WHEN** the AppView first syncs an `interactions` space that already holds comments
- **THEN** it indexes those comments along with the space's likes

#### Scenario: Duplicate notification
- **WHEN** the same notification for bob's comment is delivered twice
- **THEN** the comment is listed once
