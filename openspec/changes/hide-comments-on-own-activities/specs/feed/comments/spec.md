# Spec Delta

## ADDED Requirements

### Requirement: Hide a comment on your own activity
Clients SHALL offer Hide on each listed comment that isn't hidden, on an activity of the viewer's own, when someone else wrote the comment. They SHALL NOT offer it on any other comment. Hiding SHALL NOT ask for confirmation, because it can be undone. Hide SHALL be offered at every visibility.

#### Scenario: Someone else's comment on own activity
- **WHEN** an athlete opens their own Public activity, which has a follower's comment and one of their own
- **THEN** the follower's comment offers Hide, and their own comment offers Delete but not Hide

#### Scenario: Comment on someone else's activity
- **WHEN** a viewer opens another athlete's activity
- **THEN** no comment offers Hide, including the viewer's own

#### Scenario: Non-public activity
- **WHEN** an athlete opens their own Mutuals activity with a mutual's comment
- **THEN** that comment offers Hide

### Requirement: A hidden comment shows at once
Once the athlete hides a comment, the comment SHALL be marked as hidden in their list and the comment count SHALL drop by one, at once and without waiting for the AppView. This SHALL still hold when the screen reloads before the AppView has caught up. Hiding the same comment twice in quick succession SHALL hide it once.

#### Scenario: Hide
- **WHEN** the athlete chooses Hide on a follower's comment
- **THEN** the comment stays listed for them, marked as hidden, offers Unhide instead of Hide, and the count is one lower, before the AppView has indexed the hide

#### Scenario: Reload before the AppView catches up
- **WHEN** the athlete hides a comment and the activity reloads while the AppView still shows the comment as not hidden
- **THEN** the comment is still marked as hidden, and the count leaves it out

#### Scenario: Double tap
- **WHEN** the athlete chooses Hide on the same comment twice in quick succession
- **THEN** the count drops by one, and the comment is hidden once

### Requirement: Unhide a comment
Clients SHALL offer Unhide on each comment the viewer has hidden on their own activity. Unhiding SHALL return the comment to the count, and remove its hidden mark, at once and without waiting for the AppView. This SHALL still hold when the screen reloads before the AppView has caught up.

#### Scenario: Unhide
- **WHEN** the athlete chooses Unhide on a comment they hid
- **THEN** the comment is no longer marked as hidden, offers Hide again, and the count is one higher, before the AppView has indexed the change

#### Scenario: Seen by the audience again
- **WHEN** the athlete unhides a comment and the AppView has indexed the change
- **THEN** every viewer the comment's audience admits sees it listed and counted again

### Requirement: Where a hide is stored
A hide on a comment on a Public activity SHALL be a `feed.commentHide` record in the athlete's repo. A hide on a comment on a Followers, Mutuals or Only-me activity SHALL be a `feed.commentHide` record in the athlete's own `interactions` space, and SHALL NOT be in their repo. The client SHALL choose the place from the activity's visibility as its view shows it. A hide SHALL stay where it was written when the activity's visibility later changes.

#### Scenario: Hide on a Public activity
- **WHEN** an athlete hides a comment on their Public activity
- **THEN** their repo gains a `feed.commentHide` record naming the comment

#### Scenario: Hide on a non-public activity
- **WHEN** an athlete hides a comment on their Followers activity
- **THEN** their repo gains no `feed.commentHide` record, and the hide is a record in their `interactions` space

#### Scenario: Activity moved after the hide
- **WHEN** an athlete makes a Followers activity Public after hiding a comment on it
- **THEN** the hide stays in their `interactions` space, the client writes nothing, and the comment stays hidden

### Requirement: One hide per comment
A comment's hide SHALL have a record key derived from the comment's URI alone, so that hiding a comment again replaces the same record, and unhiding knows which record to delete. The AppView SHALL ignore a `feed.commentHide` record whose key isn't the one derived from the comment it names.

#### Scenario: Hidden twice
- **WHEN** an athlete hides a comment, and the write is retried after a dropped connection
- **THEN** their repo holds one `feed.commentHide` record for that comment

#### Scenario: Key doesn't match
- **WHEN** a `feed.commentHide` record names a comment but its record key isn't the one derived from that comment
- **THEN** the AppView doesn't hide the comment because of it

### Requirement: Unhiding removes the hide wherever it's stored
Unhiding SHALL delete the comment's hide record from the athlete's repo and from their `interactions` space, because the activity may have changed visibility since the hide. A place that doesn't hold the record, or an `interactions` space that doesn't exist, SHALL count as done. The unhide SHALL be kept and retried until it's written, including across going offline and restarting the app. A session whose `space:` scope doesn't cover `feed.commentHide` SHALL delete from the repo only.

#### Scenario: Activity moved after the hide
- **WHEN** an athlete unhides a comment on an activity they made Public after hiding the comment while it was Followers
- **THEN** the hide is gone from their `interactions` space, and the delete from their repo, which never held it, succeeds

#### Scenario: Offline
- **WHEN** an athlete unhides a comment while offline, then restarts the app and reconnects
- **THEN** the delete is written once they're back online

### Requirement: Hide refused without the space scope
A client SHALL refuse to hide a comment on a non-public activity, before writing anything, when the session's `space:` scope doesn't cover `feed.commentHide`. It SHALL report the refusal as needing to sign in again, and the comment SHALL stay unhidden. Hiding comments on Public activities SHALL keep working for such a session.

#### Scenario: Session from before hides in spaces
- **WHEN** an athlete whose session's `space:` scope lacks `feed.commentHide` hides a comment on their Followers activity
- **THEN** nothing is written to their repo or any space, the comment isn't marked as hidden, the count doesn't change, and the screen asks them to sign in again

#### Scenario: Same session on a Public activity
- **WHEN** that athlete hides a comment on their Public activity
- **THEN** the hide is written to their repo

### Requirement: Failed hide or unhide
When a hide or unhide fails for any reason other than the missing scope, the comment SHALL return to its previous state, the count SHALL go back with it, and the screen SHALL report the failure.

#### Scenario: Write can't be queued
- **WHEN** the athlete hides a comment and the write can't be queued
- **THEN** the comment is no longer marked as hidden, the count is restored, and an error is shown

### Requirement: Only the athlete's hide counts
The AppView SHALL treat a comment as hidden only while a `feed.commentHide` record naming it exists that was written by the athlete whose activity the comment is on, in their repo or their `interactions` space. A hide by anyone else SHALL change nothing. A hide SHALL count whether the AppView indexes it before or after the comment it names.

#### Scenario: Someone else hides a comment
- **WHEN** a follower writes a `feed.commentHide` naming another follower's comment on alice's activity
- **THEN** the comment is counted and listed for every viewer as before

#### Scenario: Hide indexed first
- **WHEN** the AppView indexes alice's hide of a comment before it indexes the comment itself
- **THEN** once the comment is indexed, it's hidden

#### Scenario: Hide in both places
- **WHEN** alice's hide of a comment is in both her repo and her `interactions` space, and she deletes only the repo copy
- **THEN** the comment is still hidden

### Requirement: Who sees a hidden comment
A hidden comment SHALL NOT be counted for any viewer. It SHALL be listed, marked as hidden, only for the activity's athlete and for the comment's commenter, and only while they'd see it if it weren't hidden. It SHALL NOT be listed for anyone else, signed in or not. A comment that isn't hidden SHALL NOT be marked as hidden.

#### Scenario: Athlete's view
- **WHEN** alice opens her activity with three comments, one of which she hid
- **THEN** all three are listed, the hidden one is marked as hidden, and the count is two

#### Scenario: Commenter's view
- **WHEN** bob opens alice's activity where she hid his comment
- **THEN** his comment is listed for him, marked as hidden, and the count leaves it out

#### Scenario: Another viewer's view
- **WHEN** carol, who may see the activity and the comment's audience, opens it
- **THEN** the hidden comment isn't listed and the count leaves it out

#### Scenario: Signed out
- **WHEN** a signed-out viewer opens a Public activity with a hidden comment
- **THEN** the hidden comment isn't listed or counted

#### Scenario: Commenter can no longer see the activity
- **WHEN** bob's hidden comment is on alice's Followers activity and bob stops following her
- **THEN** the comment isn't listed for bob, who can't see the activity, or for alice, as for any comment whose commenter can no longer see the activity

### Requirement: The commenter can't act on their hidden comment
A commenter SHALL be able to delete their own hidden comment as any other, and SHALL NOT be offered Hide or Unhide on it.

#### Scenario: Commenter deletes a hidden comment
- **WHEN** bob deletes his comment that alice hid
- **THEN** it's deleted as any comment of his would be, and it leaves alice's list

## MODIFIED Requirements

### Requirement: Who sees a comment
A comment SHALL be counted and listed for a viewer only while three things hold: the viewer may see the activity at its current visibility, the commenter may see it at its current visibility, and the viewer's relationship to the activity's athlete admits them to the comment's audience. This SHALL hold wherever the comment is stored. A comment the athlete has hidden SHALL also follow `Who sees a hidden comment`.

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
- **WHEN** a viewer opens an activity whose comments are written for different audiences, some of them hidden, and there are fewer than the most `getActivity` lists
- **THEN** the comment count in its view equals the number of comments listed for that viewer that aren't marked as hidden
