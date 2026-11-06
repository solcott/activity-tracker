# Spec Delta

## Purpose

Defines posts, a kind of timeline item other than activities: the post record and where each audience stores it, which posts the AppView accepts and serves, the post view with its live proposal, and how posts show in timelines and on profiles. For now, posts come only from shared name proposals.

## ADDED Requirements

### Requirement: A post is stored by its audience
A post SHALL be a `feed.post` record with optional text, at most 1000 graphemes, and an embed. A Public post SHALL be in its author's repo, and a Followers or Mutuals post in the author's space for that tier. The AppView SHALL ignore a post in a `self` or `interactions` space.

#### Scenario: Followers post
- **WHEN** alice shares her proposal with Followers
- **THEN** a `feed.post` is written to her `followers` space, and her repo gains no post

#### Scenario: Post in self
- **WHEN** a `feed.post` appears in an athlete's `self` space
- **THEN** the AppView ignores it

### Requirement: A post embeds its author's own proposal
For now, the AppView SHALL accept a post only if its embed names a `feed.segmentNameProposal` in the post author's repo with the same record key as the post. It SHALL ignore any other post, including one with no embed or an unknown embed.

#### Scenario: Someone else's proposal
- **WHEN** bob writes a post embedding alice's proposal
- **THEN** the AppView ignores it

#### Scenario: No embed
- **WHEN** an athlete writes a `feed.post` with only text
- **THEN** the AppView ignores it

#### Scenario: Different record key
- **WHEN** alice writes a post embedding her proposal under a different record key from the proposal's
- **THEN** the AppView ignores it

### Requirement: Clients offer no post composer
Clients SHALL write a post only by sharing a name proposal, and SHALL offer no other way to write one.

#### Scenario: Feed has no compose action
- **WHEN** an athlete opens the feed or their profile
- **THEN** no action offers to write a post

### Requirement: A post is served only with its proposal
The AppView SHALL serve a post only while the proposal it embeds is indexed and may be shown to the viewer. A post that arrives before its proposal SHALL be served once the proposal is accepted, and one whose proposal is ignored or deleted SHALL NOT be served.

#### Scenario: Proposal ignored
- **WHEN** bob, who isn't a regular, writes a proposal and a post sharing it
- **THEN** the AppView ignores the proposal and serves the post to no one

#### Scenario: Post arrives first
- **WHEN** alice's post is indexed a minute before her proposal is accepted
- **THEN** the post is served from when the proposal is accepted

#### Scenario: Proposal withdrawn
- **WHEN** alice deletes her proposal but her post's delete hasn't arrived yet
- **THEN** the post is no longer served

### Requirement: Posts are served by tier
A post SHALL be served only to viewers its tier admits, as an activity of that visibility is. The author SHALL always see their own posts.

#### Scenario: Not a follower
- **WHEN** alice shares a proposal with Followers and carol, who doesn't follow her, reads alice's posts
- **THEN** carol doesn't see that post

#### Scenario: Mutuals
- **WHEN** alice shares with Mutuals and bob follows alice but alice doesn't follow bob
- **THEN** bob doesn't see the post

### Requirement: Changing a post's audience moves it
When an athlete changes a post's audience, the client SHALL write the post at the same record key in the new place and delete it from the old one. While both copies exist, the AppView SHALL serve only the narrowest.

#### Scenario: Public to Followers
- **WHEN** alice changes her Public post to Followers
- **THEN** the post leaves her repo, is written to her `followers` space, and from then on only her followers see it

### Requirement: A post's proposal is shown live
A post's view SHALL include its proposal's current name, its status (open, adopted, rejected, expired or superseded), and its up and down totals, which are final once it closes. It SHALL also give the current name of the segment or dive site it names, and the viewer's own vote when they have one. The view SHALL NOT say who else voted.

#### Scenario: Edited proposal
- **WHEN** alice edits her shared proposal from "Wiggly Woods" to "Wiggly Woods Loop"
- **THEN** her post shows "Wiggly Woods Loop" with no votes

#### Scenario: Adopted
- **WHEN** alice's shared proposal is adopted
- **THEN** her post shows it as adopted with its final totals

### Requirement: Riders vote from a post
When the view says the viewer may vote (the proposal is open and they have an effort on the segment, or a visit to the site), the post card SHALL offer up and down votes. They SHALL be written, changed and taken back as votes on the segment screen are, and shown at once.

#### Scenario: Vote on a card
- **WHEN** bob, a rider of the segment, taps up on alice's post in his timeline
- **THEN** the card shows his up vote and its up total one higher at once, and a `feed.segmentNameVote` is written to his `interactions` space

#### Scenario: Not a rider
- **WHEN** carol, who has no efforts on the segment, sees alice's post
- **THEN** the card offers her no vote

#### Scenario: Closed proposal
- **WHEN** a post's proposal has been rejected
- **THEN** its card offers no vote

### Requirement: Posts are in the timeline
`feed.getTimeline` SHALL include posts by the athletes it includes activities from, interleaved with activities and import cards by feed position, as `postView` items of its open union. Clients that don't know the item SHALL skip it.

#### Scenario: Follower's timeline
- **WHEN** bob follows alice and alice shares a proposal with Followers
- **THEN** bob's timeline lists her post at its feed position

#### Scenario: Older client
- **WHEN** a client from before this change reads a timeline holding a post
- **THEN** it skips the post and shows the other items

### Requirement: A post card opens its segment
The post card SHALL show the author, the text, the segment or site with its current name, and the proposal's name, status and totals. Tapping it SHALL open the segment or site screen.

#### Scenario: Open from the card
- **WHEN** bob taps alice's post about a segment
- **THEN** the segment screen opens on that segment

### Requirement: Profiles list posts
The profile SHALL offer a switch between Activities, selected first, and Posts. Posts SHALL list the athlete's posts the viewer may see, newest first, from `feed.getAuthorPosts`, with cursor paging. They SHALL load on their own in the content-loading pattern, with an empty state when there are none.

#### Scenario: Viewer outside the audience
- **WHEN** carol, who doesn't follow alice, opens Posts on alice's profile and alice's only post is shared with Followers
- **THEN** carol sees the empty state

#### Scenario: Own profile
- **WHEN** alice opens Posts on her own profile
- **THEN** she sees every post she has shared, whatever its audience
