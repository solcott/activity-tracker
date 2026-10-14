# Spec Delta

## REMOVED Requirements

### Requirement: No likes or comments on non-public activities
**Reason**: It was a temporary restriction until likes on non-public activities had a private place to live. Likes are now allowed at every visibility; comments stay limited to Public activities until the comments change.
**Migration**: Replaced by "Likes at every visibility", "Where a like is stored", "Who sees a like" and "No comments on non-public activities". Likes given while an activity was Public stay indexed and are shown under the new rules, with no data migration.

## ADDED Requirements

### Requirement: Likes at every visibility
Clients SHALL offer the like action and show the like count on every activity the viewer can see, whatever its visibility, except the viewer's own activities. Liking SHALL keep working for an athlete who can see the activity, at Public, Followers, Mutuals or Only me.

#### Scenario: Follower likes a Followers activity
- **WHEN** an effective follower of an athlete opens one of their Followers activities
- **THEN** the like action and like count are shown, and liking it increments the count and marks it liked for the follower

#### Scenario: Own activity
- **WHEN** an athlete views their own Mutuals activity
- **THEN** the like count is shown, and no like action is offered

#### Scenario: Unlike
- **WHEN** a follower who liked a Followers activity unlikes it
- **THEN** the count goes down and the activity is no longer marked liked for them, without the client needing to know where the like was stored

### Requirement: Where a like is stored
A like on a Public activity SHALL be a `feed.like` record in the giver's repo. A like on a Followers, Mutuals or Only-me activity SHALL be a `feed.like` record in the giver's own `interactions` space, and SHALL NOT be in their repo. A like SHALL stay where it was written when the activity's visibility later changes.

#### Scenario: Like on a non-public activity
- **WHEN** a mutual likes an athlete's Mutuals activity
- **THEN** the giver's repo gains no `feed.like` record, and the like is a record in the giver's `interactions` space

#### Scenario: Like on a Public activity
- **WHEN** a viewer likes a Public activity
- **THEN** the like is a `feed.like` record in the viewer's repo, as before

#### Scenario: Activity moved after the like
- **WHEN** an athlete changes a liked Followers activity to Public
- **THEN** the giver's like stays in their `interactions` space, and the giver's client writes nothing

#### Scenario: Session without the space scope
- **WHEN** an athlete whose session lacks the `space:` scope likes a Followers activity
- **THEN** the like is refused with an error that identifies the missing scope, nothing is written to their repo, and they can still like Public activities

### Requirement: Who sees a like
A like SHALL be counted, listed by `getLikes`, and reported as `viewer.like` for a viewer only while both the viewer and the like's giver may see the activity at its current visibility, wherever the like is stored. Likes SHALL follow the activity when it is narrowed and when it is widened. An athlete SHALL NOT be counted for liking their own activity.

#### Scenario: Narrowed after likes
- **WHEN** a Public activity with likes from a follower and a non-follower is changed to Followers
- **THEN** an effective follower sees the follower's like counted and listed, and not the non-follower's

#### Scenario: Widened after likes
- **WHEN** a Followers activity liked by a follower is changed to Public
- **THEN** any viewer, signed in or not, sees that like counted and listed by `getLikes`

#### Scenario: Giver unfollows
- **WHEN** a follower who liked a Followers activity stops following the athlete, and the AppView has indexed the unfollow
- **THEN** that like is no longer counted or listed for anyone, and it's counted again if they follow again and the follow is effective

#### Scenario: Like from someone who can't see the activity
- **WHEN** a like names a Mutuals activity whose athlete doesn't follow the giver back
- **THEN** it isn't counted or listed for anyone

#### Scenario: Follow indexed after the like
- **WHEN** a new follower's like on a Followers activity reaches the AppView before their follow does
- **THEN** the like is counted once the follow is indexed and effective

#### Scenario: Viewer who can't see the activity
- **WHEN** a viewer who may not see a Followers activity asks `getLikes` for it
- **THEN** the answer reveals no likes

### Requirement: No comments on non-public activities
Clients SHALL NOT offer comments on an activity that isn't Public. The AppView SHALL NOT count, list or serve comments whose subject isn't Public, including those given while it was Public.

#### Scenario: Followers activity in the feed
- **WHEN** a follower sees an athlete's Followers activity
- **THEN** no comment action and no comment count are shown

#### Scenario: Activity made private after comments
- **WHEN** an activity with comments is changed from Public to Followers
- **THEN** its view shows no comments

#### Scenario: Made public again
- **WHEN** that activity is changed back to Public
- **THEN** the comments given while it was Public are shown again
