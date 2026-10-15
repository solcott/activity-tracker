# Spec Delta

## ADDED Requirements

### Requirement: Comments are listed in pages, oldest first
The AppView SHALL list an activity's comments through a query of their own that takes the activity, a page size and a cursor. It SHALL return them oldest first, up to the page size (30 when not given, at most 100), with a cursor for the next page, and no cursor after the last one. Each page SHALL apply `Who sees a comment` and `Who sees a hidden comment`.

#### Scenario: First page
- **WHEN** a viewer asks for the comments on an activity with 45 comments they may see, without a cursor or page size
- **THEN** they get the 30 oldest, oldest first, and a cursor

#### Scenario: Last page
- **WHEN** the viewer asks again with that cursor
- **THEN** they get the other 15, oldest first, and no cursor

#### Scenario: Page size
- **WHEN** a viewer asks for a page of 10, or of 500
- **THEN** they get at most 10, or at most 100

#### Scenario: Paging while comments arrive
- **WHEN** a new comment is indexed between the viewer's first and second page
- **THEN** no comment from the first page is listed again, none is skipped, and the new one is listed on the page that reaches it

#### Scenario: Same rule as the count
- **WHEN** a viewer pages through an activity's comments written for different audiences, some hidden, some by commenters who can no longer see the activity
- **THEN** each page lists only the comments `Who sees a comment` admits them to, a hidden one only if they're the athlete or its commenter, marked as hidden

#### Scenario: Activity the viewer can't see
- **WHEN** a viewer who may not see a Followers activity asks for its comments
- **THEN** the answer is `NotFound`, as for the activity itself, and reveals none of its comments

#### Scenario: Malformed cursor
- **WHEN** a viewer asks with a cursor the AppView didn't issue
- **THEN** they get the first page

### Requirement: Comments looked up by URI
The comments query SHALL also take a list of up to 25 comment URIs instead of a cursor. It SHALL then return just those of them that are on that activity and listed for the viewer, oldest first, marked as hidden as a page would mark them, with no cursor. It SHALL leave out any URI it doesn't list, without saying why.

#### Scenario: Comments the viewer may see
- **WHEN** the athlete asks for the comments on their activity with the URIs of a comment they hid and one they didn't
- **THEN** both are returned, the first marked as hidden

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
For any viewer, an activity's comment count SHALL equal the number of comments, not marked as hidden, across every page of its comments listed for that viewer, while no comment, hide or follow changes in between.

#### Scenario: Count across pages
- **WHEN** a viewer may see 70 of an activity's comments, written for different audiences, and the athlete hid 5 of them, and the viewer is the athlete
- **THEN** the count is 65, and paging to the end lists 70 comments, 5 of them marked as hidden

#### Scenario: Another viewer
- **WHEN** another viewer, who isn't the athlete or any hidden comment's commenter, opens the same activity
- **THEN** the count equals the number of comments listed across every page

### Requirement: The detail screen loads comments as the viewer scrolls
The detail screen SHALL show the activity once it has loaded, and its first page of comments as soon as that has, with the comment count. It SHALL load the next page when the viewer scrolls near the end of the listed comments, until there are no more, showing that more are loading. A page that fails SHALL keep what's listed, show the failure and offer to try again.

#### Scenario: Popular activity
- **WHEN** a viewer opens an activity with 200 comments
- **THEN** the activity and the 30 oldest comments are shown, and the count says 200

#### Scenario: Scrolling to the end
- **WHEN** the viewer scrolls near the last listed comment while there are more
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
- **THEN** the activity and its first page are loaded again, and the pages loaded before are replaced

### Requirement: A sent comment shows at once
Once the viewer sends a comment, it SHALL be listed after the comments loaded so far, and the count SHALL go up by one, at once and without loading the comments again. It SHALL stay listed, and counted once, when the screen reloads or loads more pages before the AppView lists it, and SHALL be listed once, in its place, once the AppView does.

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
- **WHEN** a viewer who may not see a Followers activity asks for it or its comments
- **THEN** the answer is `NotFound` and reveals none of its comments

#### Scenario: Count matches the list
- **WHEN** a viewer opens an activity whose comments are written for different audiences, some of them hidden
- **THEN** the comment count in its view equals the number of comments listed for that viewer, across every page, that aren't marked as hidden
