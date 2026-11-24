# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: The detail screen lists comments a page at a time`
- TO: `### Requirement: The Comments screen lists comments a page at a time`

## MODIFIED Requirements

### Requirement: The Comments screen lists comments a page at a time
The Comments screen SHALL show a subject's comment count and its first page of threads as soon as that has loaded. Replies SHALL be shown indented under their thread's top-level comment or placeholder. It SHALL load the next page when the viewer scrolls near the end of the listed threads, until there are no more, showing that more are loading.

#### Scenario: Popular activity
- **WHEN** a viewer opens the Comments screen of an activity with 200 comments in 120 threads
- **THEN** the 30 oldest threads with their first replies are shown, and the count says 200

#### Scenario: Scrolling to the end
- **WHEN** the viewer scrolls near the last listed thread while there are more
- **THEN** the next page is listed after it, once, however many times the viewer reaches the end while it loads

#### Scenario: Every comment loaded
- **WHEN** the viewer has reached the last page
- **THEN** nothing more is loaded, and nothing shows more loading

### Requirement: A failed page of comments keeps what's listed
When a page of a subject's comments fails to load, the Comments screen SHALL keep the comments already listed, show the failure and offer to try again. Trying again SHALL load only the page that failed. On the subject's page, a failed first page SHALL leave out the preview and keep the count and the way to the Comments screen.

#### Scenario: Next page fails
- **WHEN** loading the next page fails
- **THEN** the comments already listed stay, the screen says the rest couldn't be loaded and offers to try again, and trying again lists the next page after them without loading the earlier pages again

#### Scenario: Comments fail but the activity loads
- **WHEN** the activity loads and its first page of comments fails
- **THEN** the activity page shows the activity and its count with no preview, and the Comments screen shows the failure with a way to try again where the comments would be

### Requirement: Comments outlast a reload and coming back
A reload of the Comments screen SHALL keep the comment threads listed until its first page answers, then replace the pages and the extra replies loaded before. Coming back to the Comments screen SHALL show the pages and replies loaded before at once.

#### Scenario: Reload
- **WHEN** the viewer reloads the Comments screen
- **THEN** its first page is loaded again, the threads listed stay until that page answers, and then the pages and the extra replies loaded before are replaced

#### Scenario: Back to the activity
- **WHEN** the viewer goes back from the Comments screen to the activity's page
- **THEN** the page's count and preview show at once, and its first page of comments isn't loaded again

#### Scenario: Back to the comments
- **WHEN** the viewer has loaded a second page of threads and more replies in one thread on the Comments screen, opens a commenter's profile and goes back
- **THEN** both pages and the thread's extra replies show at once, and none of them is loaded again

## ADDED Requirements

### Requirement: The subject's page shows a comments block
An activity's page and a post's page SHALL show one social card near the top: its counts on one row, each an icon and a number read by its total ("14 comments"), likes first, then a post's reposts and quotes, then comments; under them a preview of comments, "View all" at the end while there are more, and an "Add a comment" row. The comment count SHALL show once, in the counts row. The page SHALL NOT list the threads or hold a comment field itself.

#### Scenario: Activity with comments
- **WHEN** a viewer opens an activity with 14 comments
- **THEN** its page shows the comment icon with 14 in the counts row, a preview, "View all" and "Add a comment", and no threaded list

#### Scenario: No comments
- **WHEN** a viewer opens an activity with no comments
- **THEN** its page shows the comment icon with 0 and "Add a comment", with no preview and no "View all"

### Requirement: A comment shows when it was written
Each comment on the Comments screen and in the preview SHALL show, after its author, when it was written on the viewer's clock: "Just now" under a minute ago, how many minutes or hours ago when it was written earlier on the viewer's current day, and otherwise its day and time as an activity's start reads on its page and in the feed ("Yesterday at 6:12 PM", "October 27, 2026 at 11:47 AM").

#### Scenario: Earlier today
- **WHEN** a viewer opens the Comments screen at 3:00 PM and a comment was written at 2:35 PM the same day
- **THEN** it reads "25 minutes ago"

#### Scenario: Late last night
- **WHEN** a viewer opens the Comments screen at 9:00 AM and a comment was written at 11:00 PM the night before
- **THEN** it reads "Yesterday at 11:00 PM", not "10 hours ago"

### Requirement: The preview shows the first two comments
The preview SHALL show up to the first two top-level comments of the first page of threads, oldest first, each with its author and its text cut to two lines, offering no Reply or menu. It SHALL leave out replies, placeholders, hidden comments and comments whose labels call for a warning or hiding. While the first page of a subject with comments loads, the preview SHALL show "Loading comments…" with a spinner in their place, and no "View all".

#### Scenario: Comments loading
- **WHEN** a viewer opens an activity with 14 comments and its first page hasn't answered yet
- **THEN** the block shows "Loading comments…" with a spinner, then the preview and "View all" once it answers

#### Scenario: Long thread
- **WHEN** an activity's first comment is 600 characters long and has three replies
- **THEN** the preview shows its author and the start of its text in two lines, and none of the replies

#### Scenario: Hidden first comment
- **WHEN** the athlete has hidden the first comment and the second and third are shown
- **THEN** the athlete's preview shows the second and third

#### Scenario: Only just sent
- **WHEN** the viewer has just sent the first comment on an activity and the AppView hasn't listed it yet
- **THEN** the preview shows their comment

### Requirement: The block opens the Comments screen
The comment count, "View all" and the preview SHALL open the subject's Comments screen. "Add a comment" SHALL open it with its field ready to type in.

#### Scenario: View all
- **WHEN** the viewer chooses "View all"
- **THEN** the Comments screen opens for that activity, listing its first page

#### Scenario: Add a comment
- **WHEN** the viewer chooses "Add a comment"
- **THEN** the Comments screen opens with its field focused and the keyboard up

### Requirement: The Comments screen's field stays at its foot
The Comments screen SHALL keep its comment field at the bottom of the screen, above the keyboard while typing, with what it answers and a way to cancel above it while replying, and why the last action failed. Reply on a comment SHALL focus the field.

#### Scenario: Reply while scrolled
- **WHEN** the viewer has scrolled to the tenth thread and chooses Reply on a comment there
- **THEN** the field at the foot says which comment it answers and takes focus, without scrolling the list away

#### Scenario: Send
- **WHEN** the viewer types a comment in the field and sends it
- **THEN** the comment is listed after the loaded comments, and the field is empty and stays at the foot

### Requirement: The count follows changes made on the Comments screen
A subject's page SHALL show its comment count moved by the viewer's sends, deletes, hides and unhides on that subject wherever they were made, each counted once, before and after the AppView catches up.

#### Scenario: Back from commenting
- **WHEN** a viewer opens the Comments screen from an activity showing 14 comments, sends one and goes back
- **THEN** the page shows 15 comments, before the AppView has indexed it

#### Scenario: Back after the AppView caught up
- **WHEN** the page reloads after the AppView has indexed that comment
- **THEN** the page shows 15 comments, not 16

#### Scenario: Delete on the Comments screen
- **WHEN** the viewer deletes their comment on the Comments screen and goes back
- **THEN** the page's count is one lower and the preview no longer shows it

### Requirement: The Comments screen opens by link
The Comments screen SHALL have the path of its subject's page followed by `/comments`, and opening it by link SHALL put the subject's page under it. How the screen was reached, such as with its field focused, SHALL NOT be part of its path.

#### Scenario: Activity comments link
- **WHEN** the Comments screen of `at://did:plc:abc/app.activitytracker.feed.activity/3kx` is written as a link
- **THEN** its path is `/profile/did:plc:abc/activity/3kx/comments`, and opening it shows the Comments screen over that activity's page

#### Scenario: Post comments link
- **WHEN** the Comments screen of a post is written as a link
- **THEN** its path is `/profile/{did}/post/{rkey}/comments`

### Requirement: The Comments screen is native on iOS
On iOS the Comments screen SHALL be drawn natively in the shared screen's words and order: the count, the threads with their replies and actions, the viewer's just-sent comments, the next page as the viewer scrolls, and the field at the foot above the keyboard. The comments block SHALL be drawn natively on the activity and post pages.

#### Scenario: Comments on iOS
- **WHEN** alice chooses "Add a comment" on bob's activity on iOS
- **THEN** the native Comments screen opens with the field focused above the keyboard, and its next page loads as she scrolls
