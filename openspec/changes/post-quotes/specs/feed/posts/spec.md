# Spec Delta

## MODIFIED Requirements

### Requirement: A post embeds its author's own proposal
The AppView SHALL accept a post only if its embed either names a `feed.segmentNameProposal` in the post author's repo with the same record key as the post, or quotes another post as feed/quote-posts allows. It SHALL ignore any other post, including one with no embed or an unknown embed.

#### Scenario: Someone else's proposal
- **WHEN** bob writes a post embedding alice's proposal
- **THEN** the AppView ignores it

#### Scenario: No embed
- **WHEN** an athlete writes a `feed.post` with only text
- **THEN** the AppView ignores it

#### Scenario: Different record key
- **WHEN** alice writes a post embedding her proposal under a different record key from the proposal's
- **THEN** the AppView ignores it

#### Scenario: Quote
- **WHEN** bob writes a post with text quoting alice's post
- **THEN** the AppView accepts it as a quote

### Requirement: Clients offer no post composer
Clients SHALL write a post only by sharing a name proposal or by quoting a post, and SHALL offer no other way to write one.

#### Scenario: Feed has no compose action
- **WHEN** an athlete opens the feed or their profile
- **THEN** no action offers to write a post

### Requirement: A post is served only with its proposal
The AppView SHALL serve a post that shares a proposal only while that proposal is indexed and may be shown to the viewer. A post that arrives before its proposal SHALL be served once the proposal is accepted, and one whose proposal is ignored or deleted SHALL NOT be served. A quote SHALL be served as feed/quote-posts says, with no proposal of its own.

#### Scenario: Proposal ignored
- **WHEN** bob, who isn't a regular, writes a proposal and a post sharing it
- **THEN** the AppView ignores the proposal and serves the post to no one

#### Scenario: Post arrives first
- **WHEN** alice's post is indexed a minute before her proposal is accepted
- **THEN** the post is served from when the proposal is accepted

#### Scenario: Proposal withdrawn
- **WHEN** alice deletes her proposal but her post's delete hasn't arrived yet
- **THEN** the post is no longer served

### Requirement: A post card opens its segment
The post card SHALL show:
- the author and the text;
- for a post sharing a proposal, the segment or site, with its current name, and the proposal's name, status and totals;
- for a quote, the quoted post as feed/quote-posts says;
- the post's like, comment, repost and quote counts, with like, repost and quote buttons;
- "Reposted by" and the reposter, when it's in the timeline through a repost.

Tapping the segment or site SHALL open that segment or site screen, tapping the author or the reposter SHALL open their profile, and tapping the quoted post SHALL open it. Tapping anywhere else on the card SHALL open the post page.

#### Scenario: Open from the card
- **WHEN** bob taps the segment named on alice's post card
- **THEN** the segment screen opens on that segment

#### Scenario: Open the post page
- **WHEN** bob taps alice's post card anywhere but the segment, the author, the reposter, the quoted post, a vote, or the like, repost or quote button
- **THEN** the post page opens on that post

#### Scenario: Counts on the card
- **WHEN** alice's post has two likes, three comments, one repost and one quote bob is shown
- **THEN** its card in bob's timeline shows 2 likes, 3 comments, 1 repost and 1 quote

#### Scenario: Open the reposter
- **WHEN** carol taps "Reposted by bob" on a card in her timeline
- **THEN** bob's profile opens

### Requirement: A post's view carries its counts
A post's view SHALL include its like, comment, repost and quote counts as the viewer is shown them, and, for a signed-in viewer, the URIs of their like and their repost when they have them. A client that doesn't know the fields SHALL still show the post.

#### Scenario: Liked post in the timeline
- **WHEN** bob, who liked alice's post, reads his timeline
- **THEN** the post's view gives its like count, its comment count and bob's like

#### Scenario: Reposted post
- **WHEN** bob, who reposted alice's post, opens it
- **THEN** the post's view gives its repost count and bob's repost

#### Scenario: Quoted post
- **WHEN** carol, who may see bob's quote of alice's post, opens alice's post
- **THEN** the post's view gives a quote count of one

#### Scenario: Signed out
- **WHEN** a signed-out viewer reads alice's Public post on her profile
- **THEN** its counts are given, with no viewer like or repost

### Requirement: Interactions go with their post
Once a post isn't served to anyone, its likes, comments and reposts SHALL NOT be shown, counted or listed anywhere. This covers a deleted post, a withdrawn proposal and a post taken down. If the post is written again at the same URI and served, they SHALL be counted again under the usual rules. Quotes of it SHALL stay, showing "Post unavailable" in its place.

#### Scenario: Proposal withdrawn
- **WHEN** alice withdraws a proposal whose post had comments
- **THEN** `feed.getComments` for the post is `NotFound` for everyone

#### Scenario: Reposted post deleted
- **WHEN** alice deletes a post that bob reposted
- **THEN** it leaves the timelines of bob's followers, and `feed.getReposts` for it is `NotFound`

#### Scenario: Quoted post deleted
- **WHEN** alice deletes a post that bob quoted
- **THEN** bob's quote stays in his followers' timelines, showing "Post unavailable" in place of alice's post
