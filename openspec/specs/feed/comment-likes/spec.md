# feed/comment-likes Specification

## Purpose

Athletes like comments on activities and posts. Each like is stored where the comment's narrower tier puts it, and the AppView counts it only while its giver may still see the comment.

## Requirements

### Requirement: An athlete can like a comment
A viewer SHALL be able to like and unlike any listed comment or reply on an activity or a post that isn't their own and isn't hidden, from the Comments screen. A like on a comment SHALL be a `feed.like` record naming the comment, stored in the giver's repo when the comment's effective tier (the narrower of its subject's visibility and its audience) is public, and in their interactions space otherwise. Liking SHALL show at once, before the AppView indexes it.

#### Scenario: Like a comment
- **WHEN** a viewer likes a public comment with 2 likes
- **THEN** its row shows it liked with 3 at once, and a like naming the comment is written to the viewer's repo

#### Scenario: Comment on a followers activity
- **WHEN** a viewer likes a comment on an activity whose visibility is Followers
- **THEN** the like is written to the viewer's interactions space, not their repo

#### Scenario: Own comment
- **WHEN** a viewer looks at their own comment
- **THEN** its row shows its like count, with no button to like it

### Requirement: A comment says how many athletes liked it
Every comment the AppView lists SHALL carry its like count and, for a signed-in viewer, their own like. A like SHALL count only while its giver may see the comment: the subject at its current visibility, and the comment's audience and its thread root's audience admitting the giver, judged against the subject's author, with no block between the giver and the commenter or the viewer. Each giver SHALL count once.

#### Scenario: Giver who can no longer see it
- **WHEN** an athlete liked a comment on a Followers activity and then stops following its author
- **THEN** the comment's count no longer includes their like

#### Scenario: Blocked giver
- **WHEN** the viewer has blocked an athlete who liked a comment
- **THEN** the viewer's count of that comment leaves the like out

#### Scenario: Duplicate likes
- **WHEN** a giver's repo holds two likes naming the same comment
- **THEN** the comment's count includes them once
