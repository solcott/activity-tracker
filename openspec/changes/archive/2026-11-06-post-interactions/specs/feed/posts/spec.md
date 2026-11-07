# Spec Delta

## ADDED Requirements

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
A post's view SHALL include its like count and its comment count as the viewer is shown them, and, for a signed-in viewer, the URI of their like when they have one. A client that doesn't know the fields SHALL still show the post.

#### Scenario: Liked post in the timeline
- **WHEN** bob, who liked alice's post, reads his timeline
- **THEN** the post's view gives its like count, its comment count and bob's like

#### Scenario: Signed out
- **WHEN** a signed-out viewer reads alice's Public post on her profile
- **THEN** its counts are given, with no viewer like

### Requirement: Interactions go with their post
Once a post isn't served to anyone, its likes and comments SHALL NOT be counted or listed anywhere. This covers a deleted post, a withdrawn proposal and a post taken down. If the post is written again at the same URI and served, they SHALL be counted again under the usual rules.

#### Scenario: Proposal withdrawn
- **WHEN** alice withdraws a proposal whose post had comments
- **THEN** `feed.getComments` for the post is `NotFound` for everyone

## MODIFIED Requirements

### Requirement: A post card opens its segment
The post card SHALL show:
- the author and the text;
- the segment or site, with its current name;
- the proposal's name, status and totals;
- the post's like and comment counts, with a like button.

Tapping the segment or site SHALL open that segment or site screen, and tapping the author SHALL open their profile. Tapping anywhere else on the card SHALL open the post page.

#### Scenario: Open from the card
- **WHEN** bob taps the segment named on alice's post card
- **THEN** the segment screen opens on that segment

#### Scenario: Open the post page
- **WHEN** bob taps alice's post card anywhere but the segment, the author, a vote or the like button
- **THEN** the post page opens on that post

#### Scenario: Counts on the card
- **WHEN** alice's post has two likes and three comments bob is shown
- **THEN** its card in bob's timeline shows 2 likes and 3 comments
