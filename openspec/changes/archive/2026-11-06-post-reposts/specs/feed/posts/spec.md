# Spec Delta

## MODIFIED Requirements

### Requirement: A post card opens its segment
The post card SHALL show:
- the author and the text;
- the segment or site, with its current name;
- the proposal's name, status and totals;
- the post's like, comment and repost counts, with like and repost buttons;
- "Reposted by" and the reposter, when it's in the timeline through a repost.

Tapping the segment or site SHALL open that segment or site screen, and tapping the author or the reposter SHALL open their profile. Tapping anywhere else on the card SHALL open the post page.

#### Scenario: Open from the card
- **WHEN** bob taps the segment named on alice's post card
- **THEN** the segment screen opens on that segment

#### Scenario: Open the post page
- **WHEN** bob taps alice's post card anywhere but the segment, the author, the reposter, a vote, the like button or the repost button
- **THEN** the post page opens on that post

#### Scenario: Counts on the card
- **WHEN** alice's post has two likes, three comments and one repost bob is shown
- **THEN** its card in bob's timeline shows 2 likes, 3 comments and 1 repost

#### Scenario: Open the reposter
- **WHEN** carol taps "Reposted by bob" on a card in her timeline
- **THEN** bob's profile opens

### Requirement: A post's view carries its counts
A post's view SHALL include its like, comment and repost counts as the viewer is shown them, and, for a signed-in viewer, the URIs of their like and their repost when they have them. A client that doesn't know the fields SHALL still show the post.

#### Scenario: Liked post in the timeline
- **WHEN** bob, who liked alice's post, reads his timeline
- **THEN** the post's view gives its like count, its comment count and bob's like

#### Scenario: Reposted post
- **WHEN** bob, who reposted alice's post, opens it
- **THEN** the post's view gives its repost count and bob's repost

#### Scenario: Signed out
- **WHEN** a signed-out viewer reads alice's Public post on her profile
- **THEN** its counts are given, with no viewer like or repost

### Requirement: Interactions go with their post
Once a post isn't served to anyone, its likes, comments and reposts SHALL NOT be shown, counted or listed anywhere. This covers a deleted post, a withdrawn proposal and a post taken down. If the post is written again at the same URI and served, they SHALL be counted again under the usual rules.

#### Scenario: Proposal withdrawn
- **WHEN** alice withdraws a proposal whose post had comments
- **THEN** `feed.getComments` for the post is `NotFound` for everyone

#### Scenario: Reposted post deleted
- **WHEN** alice deletes a post that bob reposted
- **THEN** it leaves the timelines of bob's followers, and `feed.getReposts` for it is `NotFound`
