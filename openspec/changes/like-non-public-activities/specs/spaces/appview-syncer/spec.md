# Spec Delta

## MODIFIED Requirements

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity` and `feed.activityStreams` records. From `interactions` spaces it SHALL index only `feed.like` records. It SHALL NOT store `actor.privateDetails`, `graph.followDecline` or `actor.activityDefaults` records from any space.

#### Scenario: Private details ignored
- **WHEN** a synced space's ops include an `actor.privateDetails` write
- **THEN** the AppView stores nothing from it

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it

## ADDED Requirements

### Requirement: Interactions spaces synced
The AppView SHALL accept `notifySpace` for the caller's own `interactions` space, and SHALL join, register for and sync it as it does a tier space, including notifications, the periodic reconcile and failure containment.

#### Scenario: Athlete announces their interactions space
- **WHEN** an athlete's client creates their `interactions` space and calls `notifySpace` for it
- **THEN** the AppView obtains a credential for it, registers for notifications, and indexes the likes in it

#### Scenario: Someone else's interactions space
- **WHEN** a caller announces an `interactions` space whose authority isn't the caller
- **THEN** the AppView rejects the request and joins nothing

### Requirement: Likes indexed from interactions spaces
A `feed.like` written to an athlete's `interactions` space SHALL be indexed as that athlete's like on its subject, and deleting it SHALL remove only that like. Deleting the space SHALL remove every like indexed from it and no others.

#### Scenario: Like appears
- **WHEN** bob puts a `feed.like` for alice's Followers activity in his `interactions` space, and bob is alice's effective follower
- **THEN** after the notification, the activity's like count includes bob for alice's effective followers

#### Scenario: Like deleted
- **WHEN** bob deletes that like from his `interactions` space
- **THEN** after the notification, it's no longer counted or listed

#### Scenario: Interactions space deleted
- **WHEN** bob deletes his `interactions` space
- **THEN** the AppView stops syncing it and no longer counts the likes indexed from it, while bob's repo likes on Public activities stay counted

#### Scenario: Duplicate notification
- **WHEN** the same notification for bob's like is delivered twice
- **THEN** the like is counted once
