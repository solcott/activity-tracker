# feed/posts Specification

## Purpose

Defines posts, a kind of timeline item other than activities: the post record and where each audience stores it, which posts the AppView accepts and serves, the post view with its live proposal, and how posts show in timelines and on profiles. For now, posts come only from shared name proposals.

## Requirements

### Requirement: A post is stored by its audience
A post SHALL be a `feed.post` record with optional text, at most 1000 graphemes, and an embed. A Public post SHALL be in its author's repo, and a Followers or Mutuals post in the author's space for that tier. The AppView SHALL ignore a post in a `self` or `interactions` space.

#### Scenario: Followers post
- **WHEN** alice shares her proposal with Followers
- **THEN** a `feed.post` is written to her `followers` space, and her repo gains no post

#### Scenario: Post in self
- **WHEN** a `feed.post` appears in an athlete's `self` space
- **THEN** the AppView ignores it

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

### Requirement: Profiles list posts
The profile SHALL offer a switch between Activities, selected first, and Posts. Posts SHALL list the athlete's posts the viewer may see, newest first, from `feed.getAuthorPosts`, with cursor paging. They SHALL load on their own in the content-loading pattern, with an empty state when there are none.

#### Scenario: Viewer outside the audience
- **WHEN** carol, who doesn't follow alice, opens Posts on alice's profile and alice's only post is shared with Followers
- **THEN** carol sees the empty state

#### Scenario: Own profile
- **WHEN** alice opens Posts on her own profile
- **THEN** she sees every post she has shared, whatever its audience

### Requirement: Likes on posts
Clients SHALL offer the like action, and show the like count, on every post the viewer can see except their own. A like on a post SHALL be a `feed.like` naming the post. It SHALL be stored in the giver's repo when the post is Public, and in their `interactions` space when the post is Followers or Mutuals. It SHALL stay where it was written when the post's audience changes later.

#### Scenario: Follower likes a Followers post
- **WHEN** bob, alice's effective follower, likes her Followers post
- **THEN** the card shows it liked, with its like count one higher, at once, and a `feed.like` naming the post is written to bob's `interactions` space and not to his repo

#### Scenario: Public post
- **WHEN** bob likes alice's Public post
- **THEN** the like is a `feed.like` in bob's repo

#### Scenario: Own post
- **WHEN** alice sees her own post
- **THEN** its like count is shown, and no like action is offered

#### Scenario: Unlike
- **WHEN** bob unlikes the post
- **THEN** the count goes down and the card isn't marked liked, without the client needing to know where the like was stored

### Requirement: Who sees a like on a post
A like on a post SHALL be counted, listed by `feed.getLikes` and reported as the viewer's like only while both the viewer and the like's giver may see the post at its current tier, and the giver isn't in a block with the viewer. An author SHALL NOT be counted for liking their own post. A viewer who may not see the post SHALL get `NotFound` from `feed.getLikes`.

#### Scenario: Narrowed after likes
- **WHEN** alice moves a Public post that bob and carol liked to Mutuals, and only bob is her mutual
- **THEN** her mutuals see a like count of one, and the likes list shows only bob

#### Scenario: Viewer outside the audience
- **WHEN** carol, who doesn't follow alice, asks `feed.getLikes` for alice's Followers post
- **THEN** the answer is `NotFound`

### Requirement: Comments on posts
Clients SHALL offer the comment field, and show the comment count, on every post the viewer can see, their own included. A comment on a post SHALL be a `feed.comment` naming the post. It SHALL be stored, record its audience, reply, and be deleted as comments on activities are. A top-level comment's audience SHALL be the post's visibility as the commenter's view showed it.

#### Scenario: Comment on a Followers post
- **WHEN** bob, alice's effective follower, comments on her Followers post
- **THEN** a `feed.comment` naming the post, with audience Followers, is written to bob's `interactions` space, and the post's comment count goes up by one at once

#### Scenario: Comment on a Public post
- **WHEN** bob comments on alice's Public post
- **THEN** the comment is a `feed.comment` in bob's repo with no audience

#### Scenario: Reply capped by its parent
- **WHEN** alice's post has since been made Public, and bob replies to carol's comment written for Followers
- **THEN** bob's reply is written for Followers, in his `interactions` space

### Requirement: Who sees a comment on a post
A comment on a post SHALL be counted and listed for a viewer only while several things hold:
- the viewer may see the post;
- the commenter may see it at its current tier;
- the viewer's relationship to the post's author admits them to the comment's audience;
- for a reply, they are also admitted to its thread's top-level comment's audience.

Blocks, mutes and labels SHALL apply as on activities. The comment count SHALL equal the comments listed for the viewer that aren't marked hidden.

#### Scenario: Widened after comments
- **WHEN** alice moves a Followers post with bob's comment on it to Public
- **THEN** alice and her effective followers still see bob's comment, and other viewers see neither it nor its count

#### Scenario: Commenter unfollows
- **WHEN** bob, who commented on alice's Followers post, stops following her, and the AppView has indexed the unfollow
- **THEN** his comment is no longer counted or listed for anyone

#### Scenario: Viewer outside the audience
- **WHEN** carol, who doesn't follow alice, asks `feed.getComments` for alice's Followers post
- **THEN** the answer is `NotFound` and reveals none of its comments

#### Scenario: Count matches the list
- **WHEN** a viewer opens a post whose comments are written for different audiences, some of them hidden
- **THEN** the post's comment count equals the number of comments listed for that viewer, across every page, that aren't marked hidden

### Requirement: Authors hide comments on their posts
A post's author SHALL be able to hide and unhide someone else's comment on it, as an athlete does on their own activity. The hide SHALL be stored where the author's like on that post would be. Only a hide by the post's author SHALL count. A hidden comment SHALL leave the count for everyone, and SHALL be listed, marked hidden, only for the author and the commenter.

#### Scenario: Hide on a Followers post
- **WHEN** alice hides bob's comment on her Followers post
- **THEN** a `feed.commentHide` is written to alice's `interactions` space, the comment count drops by one at once, and only alice and bob see the comment, marked hidden

#### Scenario: Someone else's hide
- **WHEN** carol writes a `feed.commentHide` for bob's comment on alice's post
- **THEN** nothing changes for anyone

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
