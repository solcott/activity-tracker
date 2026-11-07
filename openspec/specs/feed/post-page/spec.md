# feed/post-page Specification

## Purpose

Defines the post page: the one place a post is shown with everything around it, including its live proposal card, who liked it and its comment threads, and how a viewer reaches it.

## Requirements

### Requirement: A post is served on its own
`feed.getPost` SHALL return one post's view, by URI, under the same rules as every other read of posts. A post the viewer may not see, one that isn't indexed, one whose proposal may not be shown to the viewer, and a quote the viewer isn't shown SHALL each be `NotFound`, with no way to tell them apart.

#### Scenario: Follower opens a Followers post
- **WHEN** bob, alice's effective follower, asks for her Followers post
- **THEN** he gets its view, with its proposal as it stands now and its counts

#### Scenario: Outside the audience
- **WHEN** carol, who doesn't follow alice, asks for that post
- **THEN** the answer is `NotFound`

#### Scenario: Proposal withdrawn
- **WHEN** alice deletes the proposal her post shares
- **THEN** asking for the post is `NotFound`

#### Scenario: Quote outside the quoted post's audience
- **WHEN** carol follows bob but not alice, and asks for bob's quote of alice's Followers post
- **THEN** the answer is `NotFound`

### Requirement: The post page shows the post and its comments
The post page SHALL show the post's card, with its proposal, votes, counts and like button working as they do in the timeline, followed by its comment field and its comment threads. It SHALL load in the content-loading pattern. A post that can't be loaded SHALL show its error with a retry, and a `NotFound` post SHALL show that the post isn't available.

#### Scenario: Loads
- **WHEN** bob opens alice's post page
- **THEN** he sees loading, then the card followed by its comments

#### Scenario: Gone
- **WHEN** alice withdraws her proposal while bob's link to the post page is still around, and bob opens it
- **THEN** the page says the post isn't available

#### Scenario: Offline
- **WHEN** bob opens a post page with no connection
- **THEN** he sees the error with a retry

### Requirement: The post page lists comments as the activity page does
The post page's comments SHALL work as on an activity's detail screen:
- threads are listed a page at a time, oldest first, each with its first replies and the rest a page at a time;
- a failed page keeps what's listed;
- a sent comment or reply shows at once;
- deleting, hiding and unhiding show at once and hold on every page;
- Report, Block and Mute are offered on others' comments.

#### Scenario: Next page
- **WHEN** bob scrolls to the end of a post's first page of threads
- **THEN** the next page is loaded and listed below it

#### Scenario: Reply shows at once
- **WHEN** bob replies to carol's comment on alice's post
- **THEN** his reply is listed at the end of carol's thread at once, and the comment count goes up by one

### Requirement: The post page lists who liked it
The post page SHALL show the post's like count. Tapping it SHALL list the athletes who liked the post that the viewer is shown, from `feed.getLikes`, as for an activity.

#### Scenario: Likers
- **WHEN** bob taps the like count on alice's Followers post
- **THEN** he sees the effective followers of alice who liked it, and no one else

### Requirement: The post page lists who reposted it
The post page SHALL show the post's repost count and offer Repost as the card does. Tapping the count SHALL list the athletes who reposted the post that the viewer is shown, from `feed.getReposts`, as the likes list does.

#### Scenario: Reposters
- **WHEN** bob taps the repost count on alice's Followers post
- **THEN** he sees the effective followers of alice who reposted it, and no one else

#### Scenario: Own repost listed at once
- **WHEN** bob reposts alice's post on its page and then taps the repost count
- **THEN** he is listed, even before the AppView has indexed his repost

### Requirement: The post page lists quotes
The post page SHALL show the post's quote count and offer Quote as the card does. Tapping the count SHALL list the quotes of the post the viewer is shown, from `feed.getQuotes`, as post cards, newest first, a page at a time, in the content-loading pattern, with an empty state when there are none.

#### Scenario: Quotes
- **WHEN** bob taps the quote count on alice's Followers post
- **THEN** he sees the quotes of it he may see, as cards, newest first

#### Scenario: Own quote listed at once
- **WHEN** bob quotes alice's post from its page and then taps the quote count
- **THEN** his quote is listed first, even before the AppView has indexed it
