# feed/comments Specification

## Purpose

Defines how athletes comment on activities at every visibility: where each comment is stored, the audience it was written for, and which viewers may see it as the activity's visibility changes.

## Requirements

### Requirement: Comments at every visibility
Clients SHALL offer the comment field and show the comment count on every activity the viewer can see, whatever its visibility, including the viewer's own activities. Commenting SHALL work for any athlete who can see the activity, at Public, Followers, Mutuals or Only me.

#### Scenario: Follower comments on a Followers activity
- **WHEN** an effective follower of an athlete opens one of their Followers activities and sends a comment
- **THEN** the comment count goes up by one, and once the AppView has indexed the comment it's listed under the activity for the follower and the athlete

#### Scenario: Comment on own Only-me activity
- **WHEN** an athlete comments on their own Only-me activity
- **THEN** the comment is listed for them, and for no one else

#### Scenario: Activity view
- **WHEN** a viewer sees a Mutuals activity in their feed
- **THEN** its comment count is shown

### Requirement: Where a comment is stored
A comment on a Public activity SHALL be a `feed.comment` record in the commenter's repo. A comment on a Followers, Mutuals or Only-me activity SHALL be a `feed.comment` record in the commenter's own `interactions` space, and SHALL NOT be in their repo. The client SHALL choose the place from the visibility of the activity as its view shows it. A comment SHALL stay where it was written when the activity's visibility later changes.

#### Scenario: Comment on a non-public activity
- **WHEN** a mutual comments on an athlete's Mutuals activity
- **THEN** the commenter's repo gains no `feed.comment` record, and the comment is a record in the commenter's `interactions` space

#### Scenario: Comment on a Public activity
- **WHEN** a viewer comments on a Public activity
- **THEN** the comment is a `feed.comment` record in the viewer's repo, as before

#### Scenario: Activity moved after the comment
- **WHEN** an athlete changes a Followers activity with a follower's comment to Public
- **THEN** the comment stays in the follower's `interactions` space, and the follower's client writes nothing

### Requirement: Comment refused without the space scope
A client SHALL refuse a comment on a non-public activity, before writing anything, when the session's `space:` scope doesn't cover `feed.comment`. It SHALL report the refusal as needing to sign in again, and SHALL keep the comment's text so it isn't lost. Comments on Public activities SHALL keep working for such a session.

#### Scenario: Session from before comments in spaces
- **WHEN** an athlete whose session's `space:` scope lacks `feed.comment` comments on a Followers activity
- **THEN** nothing is written to their repo or any space, the screen asks them to sign in again, and the text they typed is still in the comment field

#### Scenario: Same session on a Public activity
- **WHEN** that athlete comments on a Public activity
- **THEN** the comment is written to their repo

### Requirement: A comment records its audience
A comment in an `interactions` space SHALL carry an `audience`: the visibility of the activity as the commenter's view showed it when they wrote the comment. A comment with no `audience`, or with `audience` Public, SHALL be treated as written for Public. An `audience` value the AppView doesn't recognise SHALL be treated as Only me.

#### Scenario: Audience recorded
- **WHEN** a follower comments on a Followers activity
- **THEN** the comment's record says its audience is Followers

#### Scenario: Repo comment
- **WHEN** a viewer comments on a Public activity
- **THEN** the comment's record has no audience, and it's treated as written for Public

#### Scenario: Unknown audience
- **WHEN** a comment's audience is a value the AppView doesn't know
- **THEN** the comment is listed only for the activity's athlete

### Requirement: Who sees a comment
A comment SHALL be counted and listed for a viewer only while three things hold: the viewer may see the activity at its current visibility, the commenter may see it at its current visibility, and the viewer's relationship to the activity's athlete admits them to the comment's audience. This SHALL hold wherever the comment is stored.

#### Scenario: Widened after comments
- **WHEN** a Followers activity with a follower's comment is changed to Public
- **THEN** the athlete and effective followers still see that comment counted and listed, and other viewers, signed in or not, see neither it nor its count

#### Scenario: Narrowed after comments
- **WHEN** a Public activity with comments from a follower and a non-follower is changed to Followers
- **THEN** an effective follower sees both comments counted and listed, because both commenters wrote for Public, but only while each commenter may still see the activity, so the non-follower's comment is shown to no one

#### Scenario: Commenter unfollows
- **WHEN** a follower who commented on a Followers activity stops following the athlete, and the AppView has indexed the unfollow
- **THEN** that comment is no longer counted or listed for anyone, and it's counted again if they follow again and the follow is effective

#### Scenario: Narrower audience than the tier
- **WHEN** a comment written for Mutuals is on an activity that's now Followers
- **THEN** only the athlete and their mutuals see it

#### Scenario: Public comments on a Public activity
- **WHEN** an activity has always been Public
- **THEN** every viewer, signed in or not, sees all its comments counted and listed, as before

#### Scenario: Viewer who can't see the activity
- **WHEN** a viewer who may not see a Followers activity asks for it
- **THEN** the answer is `NotFound` and reveals none of its comments

#### Scenario: Count matches the list
- **WHEN** a viewer opens an activity whose comments are written for different audiences, and there are fewer than the most `getActivity` lists
- **THEN** the comment count in its view equals the number of comments listed for that viewer

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
