# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: No kudos or comments on non-public activities`
- TO: `### Requirement: No likes or comments on non-public activities`

## MODIFIED Requirements

### Requirement: No likes or comments on non-public activities
Clients SHALL NOT offer likes or comments on an activity that isn't Public. The AppView SHALL NOT count, list or serve likes or comments whose subject isn't Public, including those given while it was Public.

#### Scenario: Followers activity in the feed
- **WHEN** a follower sees an athlete's Followers activity
- **THEN** no like or comment action is shown, and no like or comment counts are shown

#### Scenario: Activity made private after kudos
- **WHEN** an activity with likes is changed from Public to Followers
- **THEN** its view shows no likes, and `getLikes` for it returns none

#### Scenario: Made public again
- **WHEN** that activity is changed back to Public
- **THEN** the likes given while it was Public are shown again
