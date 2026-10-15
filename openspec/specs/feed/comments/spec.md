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
A comment written for Public SHALL be a `feed.comment` record in the commenter's repo. A comment written for Followers, Mutuals or Only me SHALL be a `feed.comment` record in the commenter's own `interactions` space, and SHALL NOT be in their repo. A top-level comment is written for the visibility of the activity as its view shows it, and a reply for the audience `A reply's audience is capped by its parent's` gives it. A comment SHALL stay where it was written when the activity's visibility later changes.

#### Scenario: Comment on a non-public activity
- **WHEN** a mutual comments on an athlete's Mutuals activity
- **THEN** the commenter's repo gains no `feed.comment` record, and the comment is a record in the commenter's `interactions` space

#### Scenario: Comment on a Public activity
- **WHEN** a viewer comments on a Public activity
- **THEN** the comment is a `feed.comment` record in the viewer's repo, as before

#### Scenario: Activity moved after the comment
- **WHEN** an athlete changes a Followers activity with a follower's comment to Public
- **THEN** the comment stays in the follower's `interactions` space, and the follower's client writes nothing

#### Scenario: Reply to a non-public comment on a Public activity
- **WHEN** a follower replies to a comment written for Followers on an activity since made Public
- **THEN** their repo gains no `feed.comment` record, and the reply is a record in their `interactions` space

#### Scenario: Reply to a Public comment on a Public activity
- **WHEN** a viewer replies to a Public comment on a Public activity
- **THEN** the reply is a `feed.comment` record in the viewer's repo

### Requirement: Comment refused without the space scope
A client SHALL refuse a comment or reply that isn't written for Public, before writing anything, when the session's `space:` scope doesn't cover `feed.comment`. It SHALL report the refusal as needing to sign in again, and SHALL keep the comment's text, and the comment it replies to, so neither is lost. Comments and replies written for Public SHALL keep working for such a session.

#### Scenario: Session from before comments in spaces
- **WHEN** an athlete whose session's `space:` scope lacks `feed.comment` comments on a Followers activity
- **THEN** nothing is written to their repo or any space, the screen asks them to sign in again, and the text they typed is still in the comment field

#### Scenario: Same session on a Public activity
- **WHEN** that athlete comments on a Public activity
- **THEN** the comment is written to their repo

#### Scenario: Reply to a non-public comment
- **WHEN** that athlete replies to a comment written for Followers on a Public activity
- **THEN** nothing is written, the screen asks them to sign in again, and the field still holds their text as a reply to that comment

### Requirement: A comment records its audience
A comment in an `interactions` space SHALL carry an `audience`: for a top-level comment, the visibility of the activity as the commenter's view showed it when they wrote the comment, and for a reply, the audience `A reply's audience is capped by its parent's` gives it. A comment with no `audience`, or with `audience` Public, SHALL be treated as written for Public. An `audience` value the AppView doesn't recognise SHALL be treated as Only me.

#### Scenario: Audience recorded
- **WHEN** a follower comments on a Followers activity
- **THEN** the comment's record says its audience is Followers

#### Scenario: Repo comment
- **WHEN** a viewer comments on a Public activity
- **THEN** the comment's record has no audience, and it's treated as written for Public

#### Scenario: Unknown audience
- **WHEN** a comment's audience is a value the AppView doesn't know
- **THEN** the comment is listed only for the activity's athlete

#### Scenario: Reply's audience recorded
- **WHEN** a follower replies to a comment written for Mutuals on a Followers activity
- **THEN** the reply's record says its audience is Mutuals

### Requirement: Who sees a comment
A comment SHALL be counted and listed for a viewer only while three things hold: the viewer may see the activity at its current visibility, the commenter may see it at its current visibility, and the viewer's relationship to the activity's athlete admits them to the comment's audience. This SHALL hold wherever the comment is stored. A reply SHALL also follow `A reply's audience is capped by its parent's`. A comment the athlete has hidden SHALL also follow `Who sees a hidden comment`.

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
- **WHEN** a viewer who may not see a Followers activity asks for it or its comments
- **THEN** the answer is `NotFound` and reveals none of its comments

#### Scenario: Count matches the list
- **WHEN** a viewer opens an activity whose comments are written for different audiences, some of them hidden
- **THEN** the comment count in its view equals the number of comments listed for that viewer, across every page, that aren't marked as hidden

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

### Requirement: Comments are listed in pages, oldest first
The AppView SHALL list an activity's comments through a query of their own that takes the activity, a page size and a cursor. It SHALL return threads, each a top-level comment or the placeholder with its first replies, ordered by the oldest comment listed for the viewer in each, up to the page size of threads (30 when not given, at most 100), with a cursor for the next page and none after the last. Each page SHALL apply `Who sees a comment` and `Who sees a hidden comment`.

#### Scenario: First page
- **WHEN** a viewer asks for the comments on an activity with 45 threads they may see, without a cursor or page size
- **THEN** they get the 30 oldest threads, oldest first, and a cursor

#### Scenario: Last page
- **WHEN** the viewer asks again with that cursor
- **THEN** they get the other 15 threads, oldest first, and no cursor

#### Scenario: Page size
- **WHEN** a viewer asks for a page of 10, or of 500
- **THEN** they get at most 10 threads, or at most 100

#### Scenario: Paging while comments arrive
- **WHEN** a new top-level comment is indexed between the viewer's first and second page
- **THEN** no thread from the first page is listed again, none is skipped, and the new one is listed on the page that reaches it

#### Scenario: Reply arrives while paging
- **WHEN** a reply to a thread on the viewer's first page is indexed before they load the second page
- **THEN** the second page doesn't list that thread again

#### Scenario: Placeholder's place
- **WHEN** a thread's top-level comment isn't listed for the viewer
- **THEN** the thread is placed by its oldest reply listed for the viewer

#### Scenario: Same rule as the count
- **WHEN** a viewer pages through an activity's comments written for different audiences, some hidden, some by commenters who can no longer see the activity
- **THEN** each page lists only the comments `Who sees a comment` admits them to, a hidden one only if they're the athlete or its commenter, marked as hidden

#### Scenario: Activity the viewer can't see
- **WHEN** a viewer who may not see a Followers activity asks for its comments
- **THEN** the answer is `NotFound`, as for the activity itself, and reveals none of its comments

#### Scenario: Malformed cursor
- **WHEN** a viewer asks with a cursor the AppView didn't issue
- **THEN** they get the first page

#### Scenario: Client from before replies
- **WHEN** a client from before replies asks for a page of comments
- **THEN** it gets the page's top-level comments that are listed for the viewer, as before, and no replies

### Requirement: Comments looked up by URI
The comments query SHALL also take a list of up to 25 comment URIs instead of a cursor. It SHALL then return just those of them that are on that activity and listed for the viewer, top-level comments and replies alike, oldest first, marked as hidden as a page would mark them, with no cursor. It SHALL leave out any URI it doesn't list, without saying why.

#### Scenario: Comments the viewer may see
- **WHEN** the athlete asks for the comments on their activity with the URIs of a comment they hid and one they didn't
- **THEN** both are returned, the first marked as hidden

#### Scenario: Reply looked up
- **WHEN** a viewer asks with the URI of their reply, which the AppView has indexed
- **THEN** it's returned, naming its thread's top-level comment

#### Scenario: Deleted or unknown comment
- **WHEN** a viewer asks with the URI of a comment whose delete the AppView has indexed, and of one it never indexed
- **THEN** neither is returned, and the answer doesn't say why

#### Scenario: Comment the viewer may not see
- **WHEN** a viewer asks with the URI of a comment written for Mutuals, and isn't one of the athlete's mutuals
- **THEN** it isn't returned

#### Scenario: Comment on another activity
- **WHEN** a viewer asks for one activity's comments with the URI of a comment on another activity
- **THEN** it isn't returned

#### Scenario: Too many URIs
- **WHEN** a viewer asks with 26 URIs
- **THEN** the answer is `InvalidRequest`

### Requirement: The activity view comes without its comments
The query for a single activity SHALL NOT list its comments. It SHALL return the activity with its comment count. For clients that still read comments from it, it SHALL keep answering with an empty comment list.

#### Scenario: Activity with comments
- **WHEN** a viewer asks for an activity with 12 comments they may see
- **THEN** the answer holds the activity, its count is 12, and its comment list is empty

#### Scenario: Older client
- **WHEN** a client from before this change asks for an activity
- **THEN** the answer still has a comment list, which is empty, so the client doesn't fail to read it

### Requirement: The count matches the pages
For any viewer, an activity's comment count SHALL equal the number of comments and replies, not marked as hidden, across every page of its threads and every page of each thread's replies listed for that viewer, while no comment, hide or follow changes in between. A placeholder SHALL NOT be counted.

#### Scenario: Count across pages
- **WHEN** a viewer may see 70 of an activity's comments, written for different audiences, and the athlete hid 5 of them, and the viewer is the athlete
- **THEN** the count is 65, and paging to the end lists 70 comments, 5 of them marked as hidden

#### Scenario: Another viewer
- **WHEN** another viewer, who isn't the athlete or any hidden comment's commenter, opens the same activity
- **THEN** the count equals the number of comments listed across every page

#### Scenario: Replies counted
- **WHEN** an activity has 10 top-level comments and 15 replies a viewer may see, none hidden
- **THEN** the count is 25, and paging through every thread and its replies lists all 25

#### Scenario: Placeholder not counted
- **WHEN** an activity's only listed thread is a placeholder with two replies
- **THEN** the count is 2

### Requirement: The detail screen loads comments as the viewer scrolls
The detail screen SHALL show the activity once it has loaded, and its first page of threads as soon as that has, with the comment count. Replies SHALL be shown indented under their thread's top-level comment or placeholder. It SHALL load the next page when the viewer scrolls near the end of the listed threads, until there are no more, showing that more are loading. A page that fails SHALL keep what's listed, show the failure and offer to try again.

#### Scenario: Popular activity
- **WHEN** a viewer opens an activity with 200 comments in 120 threads
- **THEN** the activity and the 30 oldest threads with their first replies are shown, and the count says 200

#### Scenario: Scrolling to the end
- **WHEN** the viewer scrolls near the last listed thread while there are more
- **THEN** the next page is listed after it, once, however many times the viewer reaches the end while it loads

#### Scenario: Every comment loaded
- **WHEN** the viewer has reached the last page
- **THEN** nothing more is loaded, and nothing shows more loading

#### Scenario: Next page fails
- **WHEN** loading the next page fails
- **THEN** the comments already listed stay, the screen says the rest couldn't be loaded and offers to try again, and trying again lists the next page after them

#### Scenario: Comments fail but the activity loads
- **WHEN** the activity loads and its first page of comments fails
- **THEN** the activity and its count are shown, with the failure and a way to try again where the comments would be

#### Scenario: Reload
- **WHEN** the viewer reloads the screen
- **THEN** the activity and its first page are loaded again, the pages and the extra replies loaded before are replaced

### Requirement: A sent comment shows at once
Once the viewer sends a comment, it SHALL be listed after the comments loaded so far, and a sent reply after the replies shown in its thread, and the count SHALL go up by one, at once and without loading the comments again. It SHALL stay listed, and counted once, when the screen reloads or loads more pages before the AppView lists it, and SHALL be listed once, in its place, once the AppView does.

#### Scenario: Sent comment
- **WHEN** a viewer sends a comment on an activity whose first page is listed and which has more pages
- **THEN** their comment is listed after the loaded comments and the count is one higher, before the AppView has indexed it

#### Scenario: Loading more after sending
- **WHEN** the viewer then loads the next page, and the AppView hasn't indexed the comment yet
- **THEN** the new page is listed before their comment, which stays last

#### Scenario: AppView catches up
- **WHEN** the AppView has indexed the comment and the viewer reloads and pages to the end
- **THEN** their comment is listed once, where the AppView lists it, and counted once

#### Scenario: Reload before the AppView catches up
- **WHEN** the viewer reloads the screen before the AppView has indexed their comment
- **THEN** their comment is still listed and counted

#### Scenario: Delete a sent comment
- **WHEN** the viewer deletes the comment they've just sent, before the AppView has indexed it
- **THEN** it leaves the list and the count drops by one

#### Scenario: Sent reply
- **WHEN** a viewer replies in a thread showing three of its five replies
- **THEN** their reply is shown at the end of that thread, after the three, the count is one higher, and the control still offers the 2 more

#### Scenario: Sent reply after a reload
- **WHEN** the viewer reloads before the AppView has indexed their reply, and its thread is on a later page
- **THEN** the reply isn't shown until that page is loaded, and is then shown at the end of its thread, and it's counted throughout

### Requirement: Own changes hold on every page
Comments the viewer deleted, and comments the athlete hid or unhid, SHALL show as changed on whichever page they're listed. The count SHALL reflect each change once, whether or not the AppView has caught up, including when the change was on a page beyond the first and the screen reloads.

#### Scenario: Delete on a later page
- **WHEN** a viewer deletes their comment from the third page, and the screen reloads before the AppView has indexed the delete
- **THEN** the count leaves it out, and paging to the third page doesn't list it

#### Scenario: AppView caught up before the reload
- **WHEN** the viewer deletes their comment from the third page, and the AppView has indexed the delete before the screen reloads
- **THEN** the count is the AppView's, not one lower again

#### Scenario: Hide on a later page
- **WHEN** an athlete hides a comment on the second page of their activity's comments, and the screen reloads before the AppView has indexed the hide
- **THEN** the count leaves it out, and the comment is marked as hidden when the second page is listed

### Requirement: Reply to a comment
Clients SHALL offer Reply on each listed comment and reply that the AppView has listed, on any activity the viewer may comment on. A reply SHALL be a comment on the same activity that names the comment it answers and the top-level comment of its thread. Clients SHALL NOT offer Reply on a hidden comment, on the unavailable placeholder, or on a sent comment the AppView hasn't listed yet.

#### Scenario: Reply to a top-level comment
- **WHEN** a viewer chooses Reply on bob's comment, types a reply and sends it
- **THEN** the reply is a comment on the same activity that names bob's comment as both the comment it answers and its thread's top-level comment

#### Scenario: Reply to a reply
- **WHEN** a viewer chooses Reply on carol's reply in bob's thread and sends a reply
- **THEN** the new reply names carol's reply as the comment it answers and bob's comment as its thread's top-level comment

#### Scenario: Sent comment not yet listed
- **WHEN** a viewer has just sent a comment and the AppView hasn't listed it yet
- **THEN** it offers no Reply until a page or reload lists it from the AppView

#### Scenario: Hidden comment
- **WHEN** the athlete or the commenter sees a comment marked as hidden
- **THEN** it offers no Reply

#### Scenario: Cancel a reply
- **WHEN** the viewer chooses Reply on a comment and then cancels it
- **THEN** the comment field goes back to writing a top-level comment and keeps the text typed so far

### Requirement: Threads are one level deep
Every reply SHALL be listed in the thread of the top-level comment its record names, after that comment, however deep the chain of replies it answers. A reply SHALL NOT be listed as a top-level comment, and replies SHALL NOT be nested under replies.

#### Scenario: Chain of replies
- **WHEN** carol replies to bob's comment, and dan replies to carol's reply
- **THEN** bob's thread lists carol's reply and then dan's, both at the same indent under bob's comment

#### Scenario: Reply is not top-level
- **WHEN** a viewer pages through the comments on an activity with replies
- **THEN** no reply is listed as a thread of its own

### Requirement: A reply's audience is capped by its parent's
A client SHALL set a reply's audience to the narrower of the activity's visibility as the viewer's view shows it and the audience of the comment it answers, where Public is widest, then Followers, then Mutuals, then Only me. The AppView SHALL list a reply only to viewers admitted to its own audience and, while its thread's top-level comment is indexed, to that comment's audience too.

#### Scenario: Reply to a Followers comment on a now-Public activity
- **WHEN** a follower replies to a comment written for Followers on an activity the athlete has since made Public
- **THEN** the reply's audience is Followers, and a viewer who isn't an effective follower doesn't see it

#### Scenario: Reply to a Public comment on a Mutuals activity
- **WHEN** a mutual replies to a Public comment on an activity that's now Mutuals
- **THEN** the reply's audience is Mutuals

#### Scenario: Wider reply from another client
- **WHEN** another client writes a reply with audience Public in the thread of a comment written for Mutuals
- **THEN** only viewers among the athlete's mutuals, and the athlete, see the reply

### Requirement: Replies are comments
A reply SHALL be counted, hidden, unhidden and deleted as any comment is, under the same rules and offers, and SHALL follow `Who sees a comment` and `Who sees a hidden comment`. Deleting or hiding a comment SHALL NOT delete or hide the replies to it.

#### Scenario: Delete own reply
- **WHEN** a viewer deletes their reply
- **THEN** it leaves its thread and the count drops by one at once, as for any comment

#### Scenario: Athlete hides a reply
- **WHEN** an athlete hides someone's reply on their activity
- **THEN** the reply is marked as hidden for the athlete and the replier, isn't listed for anyone else, and isn't counted

#### Scenario: Delete a comment with replies
- **WHEN** bob deletes his comment that has two replies from others
- **THEN** both replies are still counted, and listed for the viewers who may see them

### Requirement: Unavailable top-level comment
When a thread's top-level comment isn't listed for the viewer but at least one of its replies is, the thread SHALL be listed with a placeholder in the top-level comment's place, followed by those replies. The placeholder SHALL reveal nothing about the comment: not its author, text, time, audience, or why it isn't shown. A thread none of whose replies are listed SHALL NOT be listed at all.

#### Scenario: Deleted top-level comment
- **WHEN** bob deletes his comment, which has carol's reply, and the AppView has indexed the delete
- **THEN** viewers who may see carol's reply see a placeholder followed by her reply, and the count includes her reply but not bob's comment

#### Scenario: Hidden top-level comment
- **WHEN** the athlete hides bob's comment, which has carol's reply
- **THEN** the athlete and bob see bob's comment marked as hidden with carol's reply under it, and other viewers see the placeholder with carol's reply under it

#### Scenario: Top-level commenter can no longer see the activity
- **WHEN** bob, who commented on a Followers activity, stops following the athlete, and carol replied to his comment
- **THEN** effective followers see the placeholder with carol's reply under it

#### Scenario: No replies listed
- **WHEN** bob's deleted comment had only replies the viewer may not see
- **THEN** no placeholder and no part of that thread is listed for the viewer

#### Scenario: Placeholder reveals nothing
- **WHEN** a viewer is listed a placeholder
- **THEN** the AppView's answer for it holds only the thread's identity and its listed replies, the same whatever the reason the top-level comment isn't shown

#### Scenario: Placeholder offers nothing
- **WHEN** the viewer sees a placeholder
- **THEN** it offers no Reply, Delete, Hide or Unhide

### Requirement: A thread comes with its first replies
Each thread in a page of comments SHALL come with its oldest replies listed for the viewer, up to three, and the number of its replies listed for the viewer. When it has more, the screen SHALL show a control to view the rest, saying how many more there are.

#### Scenario: Thread with five replies
- **WHEN** a viewer opens an activity whose oldest comment has five replies they may see
- **THEN** that comment is shown with its three oldest replies under it, and a control to view 2 more replies

#### Scenario: Thread with two replies
- **WHEN** a comment has two replies the viewer may see
- **THEN** both are shown under it, and there's no control to view more

#### Scenario: Replies the viewer may not see
- **WHEN** a comment has four replies and one of them was written for Mutuals and the viewer isn't a mutual
- **THEN** its reply count for the viewer is 3, and all three are shown

### Requirement: The rest of a thread's replies are listed in pages
The comments query SHALL take a thread's top-level comment instead of a cursor of threads, and SHALL then list that thread's replies listed for the viewer, oldest first, a page at a time with a cursor, applying `Who sees a comment` and `Who sees a hidden comment`. The screen SHALL list the replies it loads after the ones shown, once each, until there are no more.

#### Scenario: View more replies
- **WHEN** the viewer chooses to view the 2 more replies of a thread showing three
- **THEN** the fourth and fifth replies are shown after the third, and the control goes away

#### Scenario: Many replies
- **WHEN** a thread has 80 replies the viewer may see and they keep choosing to view more
- **THEN** each choice lists the next page of replies after those shown, until all 80 are listed

#### Scenario: View more fails
- **WHEN** loading more replies fails
- **THEN** the replies already shown stay, and the control offers to try again

#### Scenario: Activity the viewer can't see
- **WHEN** a viewer who may not see the activity asks for a thread's replies
- **THEN** the answer is `NotFound`, and reveals none of the replies

### Requirement: A malformed reply is skipped
The AppView SHALL NOT index a comment whose reply names, as the comment it answers or as its thread's top-level comment, anything that isn't a comment record. A reply whose thread's top-level comment is on another activity SHALL be listed only under a placeholder on the activity it names as its subject.

#### Scenario: Reply naming a like
- **WHEN** a comment record's reply names a `feed.like` as its parent
- **THEN** the comment isn't counted or listed anywhere

#### Scenario: Top-level comment on another activity
- **WHEN** a reply on activity A names a comment on activity B as its thread's top-level comment
- **THEN** on activity A it's listed under a placeholder, and it's never listed on activity B
