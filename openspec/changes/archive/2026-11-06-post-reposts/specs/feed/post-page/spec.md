# Spec Delta

## ADDED Requirements

### Requirement: The post page lists who reposted it
The post page SHALL show the post's repost count and offer Repost as the card does. Tapping the count SHALL list the athletes who reposted the post that the viewer is shown, from `feed.getReposts`, as the likes list does.

#### Scenario: Reposters
- **WHEN** bob taps the repost count on alice's Followers post
- **THEN** he sees the effective followers of alice who reposted it, and no one else

#### Scenario: Own repost listed at once
- **WHEN** bob reposts alice's post on its page and then taps the repost count
- **THEN** he is listed, even before the AppView has indexed his repost
