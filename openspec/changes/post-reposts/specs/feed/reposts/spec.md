# Spec Delta

## Purpose

Defines reposts: how an athlete passes someone else's post on to their own followers, where the repost is stored, who is shown it, how timelines show it once, and who is listed as having reposted a post.

## ADDED Requirements

### Requirement: Reposting a post
Clients SHALL offer Repost on every post the viewer can see except their own, on the post card and the post page. A repost SHALL be a `feed.repost` naming the post by strongRef. It SHALL be stored in the reposter's repo when the post is Public, and in their `interactions` space when the post is Followers or Mutuals. It SHALL stay where it was written when the post's audience changes later. Undo repost SHALL delete the record.

#### Scenario: Repost a Followers post
- **WHEN** bob, alice's effective follower, reposts her Followers post
- **THEN** the card shows it reposted, with its repost count one higher, at once, and a `feed.repost` naming the post is written to bob's `interactions` space and not to his repo

#### Scenario: Repost a Public post
- **WHEN** bob reposts alice's Public post
- **THEN** the repost is a `feed.repost` in bob's repo

#### Scenario: Own post
- **WHEN** alice sees her own post
- **THEN** its repost count is shown, and no Repost action is offered

#### Scenario: Undo
- **WHEN** bob undoes his repost
- **THEN** the count goes down, the card isn't marked reposted, and the record is deleted wherever it was stored

#### Scenario: Without the space scope
- **WHEN** bob's session predates the scope covering `feed.repost` and he reposts a Followers post
- **THEN** the repost is refused before anything changes, with the same explanation a like gets

### Requirement: Only other athletes' posts are reposted
The AppView SHALL ignore a `feed.repost` whose subject isn't a `feed.post`, and one whose subject's author is the reposter.

#### Scenario: Repost of an activity
- **WHEN** bob's repo holds a `feed.repost` naming one of alice's activities
- **THEN** the AppView indexes nothing from it

#### Scenario: Repost of one's own post
- **WHEN** alice's repo holds a `feed.repost` naming her own post
- **THEN** the AppView indexes nothing from it

### Requirement: Who is shown a repost
A repost SHALL be shown, counted and listed for a viewer only while the viewer may see the post, the reposter may see it at its current tier, and the reposter isn't in a block with the viewer or hidden from them by a label. A repost SHALL never widen a post's audience.

#### Scenario: Outside the audience
- **WHEN** carol follows bob but not alice, and bob reposts alice's Followers post
- **THEN** carol's timeline doesn't show the post, and nothing tells her bob reposted anything

#### Scenario: Narrowed after a repost
- **WHEN** bob and dave reposted alice's Public post, alice moves it to Mutuals, and only bob is her mutual
- **THEN** her mutuals see a repost count of one, and the reposters list shows only bob

#### Scenario: Reposter unfollows
- **WHEN** bob, who reposted alice's Followers post, stops following her, and the AppView has indexed the unfollow
- **THEN** his repost is no longer shown, counted or listed for anyone

### Requirement: Reposts in the timeline
A viewer's timeline SHALL include their own reposts and those of athletes they effectively follow, each shown as the post with "Reposted by" and the reposter. A repost by an athlete the viewer mutes, or of a post whose author they mute, SHALL NOT be in their timeline. Profiles SHALL NOT list reposts.

#### Scenario: Followed reposter
- **WHEN** carol follows bob but not alice, and bob reposts alice's Public post
- **THEN** carol's timeline shows alice's post, with "Reposted by bob", at bob's repost

#### Scenario: Own repost
- **WHEN** bob reposts alice's Public post
- **THEN** bob's timeline shows it as reposted by him

#### Scenario: Muted reposter
- **WHEN** carol mutes bob, and bob reposts alice's Public post
- **THEN** carol's timeline doesn't show it through bob's repost

#### Scenario: Not on profiles
- **WHEN** carol opens Posts on bob's profile after he reposted alice's post
- **THEN** alice's post isn't listed there

### Requirement: Several reposts of one post
When more than one repost of a post is in a viewer's timeline, the item SHALL name the reposter of the latest one and SHALL say how many others in the viewer's timeline reposted it.

#### Scenario: Two reposters
- **WHEN** carol follows bob and dave, and both repost alice's Public post, dave last
- **THEN** carol's timeline shows the post once, "Reposted by dave and 1 other", at dave's repost

#### Scenario: One unfollowed
- **WHEN** carol then unfollows dave
- **THEN** her timeline shows the post as reposted by bob alone, at bob's repost

### Requirement: Who reposted a post
`feed.getReposts` SHALL list, most recent first and each once, the athletes whose reposts of a post the viewer is shown. A viewer who may not see the post, and any subject that isn't a post, SHALL get `NotFound`.

#### Scenario: Reposters
- **WHEN** bob asks `feed.getReposts` for alice's Followers post that carol and dave reposted, and dave no longer follows alice
- **THEN** he gets carol only

#### Scenario: Outside the audience
- **WHEN** a viewer who may not see alice's Followers post asks `feed.getReposts` for it
- **THEN** the answer is `NotFound`
