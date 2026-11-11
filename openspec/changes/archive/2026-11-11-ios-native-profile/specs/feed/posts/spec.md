# Spec Delta

## MODIFIED Requirements

### Requirement: Profiles list posts
The profile SHALL offer a switch between Activities, selected first, and Posts. Posts SHALL list the athlete's posts the viewer may see, newest first, from `feed.getAuthorPosts`, with cursor paging. They SHALL load on their own in the content-loading pattern, with an empty state when there are none. The native profile on iOS SHALL list activities only, with no switch, until posts can be drawn natively.

#### Scenario: Viewer outside the audience
- **WHEN** carol, who doesn't follow alice, opens Posts on alice's profile and alice's only post is shared with Followers
- **THEN** carol sees the empty state

#### Scenario: Own profile
- **WHEN** alice opens Posts on her own profile
- **THEN** she sees every post she has shared, whatever its audience

#### Scenario: A profile on iOS
- **WHEN** alice opens bob's profile on iOS
- **THEN** it lists bob's activities and offers no Posts
