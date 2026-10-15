# Spec Delta

## ADDED Requirements

### Requirement: Delete your own comment
Clients SHALL offer Delete on each listed comment the viewer wrote, and on no other comment, whatever the activity's visibility and whoever's activity it is. Deleting SHALL ask for confirmation first. Once confirmed, the comment SHALL leave the list and the comment count SHALL drop by one at once, without waiting for the AppView, and SHALL stay gone when the screen reloads before the AppView has caught up.

#### Scenario: Own comment
- **WHEN** a follower opens a Followers activity they commented on
- **THEN** their comment offers Delete, and the other comments listed don't

#### Scenario: Comment on own activity
- **WHEN** an athlete opens their own activity, which has their comment and a follower's
- **THEN** only their own comment offers Delete

#### Scenario: Confirmed delete
- **WHEN** the viewer chooses Delete on their comment and confirms
- **THEN** the comment is no longer listed and the count is one lower, before the AppView has indexed the delete

#### Scenario: Cancelled delete
- **WHEN** the viewer chooses Delete on their comment and cancels
- **THEN** nothing is written, and the comment is still listed and counted

#### Scenario: Reload before the AppView catches up
- **WHEN** the viewer deletes their comment and the activity reloads while the AppView still lists it
- **THEN** the comment is still not listed, and the count leaves it out

#### Scenario: Double tap
- **WHEN** the viewer confirms the delete of the same comment twice in quick succession
- **THEN** it is deleted once and the count drops by one

### Requirement: A deleted comment is removed wherever it's stored
Deleting a comment SHALL remove its record from the commenter's repo and from their `interactions` space, because the client can't tell which holds it. A place that doesn't hold the record, or an `interactions` space that doesn't exist, SHALL count as done. The delete SHALL be kept and retried until it's written, including across going offline and restarting the app. A session whose `space:` scope doesn't cover `feed.comment` SHALL delete from the repo only.

#### Scenario: Comment on a non-public activity
- **WHEN** a mutual deletes their comment on a Mutuals activity
- **THEN** the record is gone from their `interactions` space, and the delete from their repo, which never held it, succeeds

#### Scenario: Comment on a Public activity
- **WHEN** a viewer deletes their comment on a Public activity, and they have no `interactions` space
- **THEN** the record is gone from their repo, and nothing waits on the space

#### Scenario: Activity moved after the comment
- **WHEN** a follower deletes their comment on a Followers activity that has since been made Public
- **THEN** the record is gone from their `interactions` space, where it was written

#### Scenario: Offline
- **WHEN** a viewer deletes their comment while offline, then restarts the app and reconnects
- **THEN** the delete is written once they're back online, and the comment stays out of the list meanwhile

#### Scenario: Session without the space scope
- **WHEN** an athlete whose session's `space:` scope lacks `feed.comment` deletes their comment
- **THEN** the delete goes to their repo only, and nothing is held up waiting on a space write the PDS would refuse

### Requirement: The AppView drops a deleted comment
Once the AppView has indexed the deletion of a comment's record from the place that held it, it SHALL no longer count or list that comment for any viewer. A delete from a place that didn't hold the comment SHALL change nothing.

#### Scenario: Deleted from the interactions space
- **WHEN** a follower's comment on a Followers activity is deleted from their `interactions` space and the AppView has synced the space
- **THEN** neither the athlete nor any follower sees it listed or counted

#### Scenario: Deleted from the repo
- **WHEN** a comment on a Public activity is deleted from the commenter's repo and the AppView has indexed the delete
- **THEN** no viewer, signed in or not, sees it listed or counted

#### Scenario: Delete from both places
- **WHEN** the deletes of a comment from the commenter's repo and their `interactions` space both reach the AppView, in either order
- **THEN** the comment is gone, and nothing else changes

#### Scenario: Delete from the other place only
- **WHEN** the AppView indexes a delete of a comment's URI from the repo while the comment is stored in the commenter's `interactions` space
- **THEN** the comment is still counted and listed as before
