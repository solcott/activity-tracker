# Spec Delta

## MODIFIED Requirements

### Requirement: The post page shows the post and its comments
The post page SHALL show the post's card, with its proposal, votes and like, repost and quote buttons working as they do in the timeline, followed by its social card: the like, repost, quote and comment counts on one row (likes and reposts showing who gave them, quotes opening the quotes), which the card itself then leaves out, and the comments block. It SHALL load in the content-loading pattern. A post that can't be loaded SHALL show its error with a retry, and a `NotFound` post SHALL show that the post isn't available.

#### Scenario: Loads
- **WHEN** bob opens alice's post page
- **THEN** he sees loading, then the card followed by its comments block

#### Scenario: Gone
- **WHEN** alice withdraws her proposal while bob's link to the post page is still around, and bob opens it
- **THEN** the page says the post isn't available

#### Scenario: Offline
- **WHEN** bob opens a post page with no connection
- **THEN** he sees the error with a retry

### Requirement: The post page lists comments as the activity page does
The post page's comments block SHALL work as an activity page's, and the post's Comments screen SHALL work as an activity's:
- threads are listed a page at a time, oldest first, each with its first replies and the rest a page at a time;
- a failed page keeps what's listed;
- a sent comment or reply shows at once;
- deleting, hiding and unhiding show at once and hold on every page;
- Report, Block and Mute are offered on others' comments.

#### Scenario: Next page
- **WHEN** bob scrolls to the end of the first page of threads on a post's Comments screen
- **THEN** the next page is loaded and listed below it

#### Scenario: Reply shows at once
- **WHEN** bob replies to carol's comment on alice's post's Comments screen
- **THEN** his reply is listed at the end of carol's thread at once, and the comment count goes up by one there and on the post page

### Requirement: The post page is native on iOS
On iOS the post page SHALL be drawn natively in the shared page's words and order: the post's native card, a failed action, the counts row with the likers and reposters lists once shown, and the comments block (the preview or "Loading comments…", View all and Add a comment), which opens the native Comments screen. A post the AppView doesn't serve SHALL say it isn't available.

#### Scenario: Opening a post
- **WHEN** alice opens bob's post on iOS
- **THEN** she sees loading, then the native card followed by its comments block, and "View all" opens the native Comments screen

#### Scenario: Gone
- **WHEN** alice opens a post on iOS that the AppView no longer serves her
- **THEN** the page says the post isn't available
