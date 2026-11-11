# Spec Delta

## ADDED Requirements

### Requirement: The post page is native on iOS
On iOS the post page SHALL be drawn natively in the shared page's words and order: the post's native card, a failed action, Show likes and Show reposts with their lists, Show quotes, and the comments (the count, the threads with their replies, the viewer's just-sent comments, the next page as the viewer scrolls, and the field). A post the AppView doesn't serve SHALL say it isn't available.

#### Scenario: Opening a post
- **WHEN** alice opens bob's post on iOS
- **THEN** she sees loading, then the native card followed by its comments, and its comments' next page loads as she scrolls

#### Scenario: Gone
- **WHEN** alice opens a post on iOS that the AppView no longer serves her
- **THEN** the page says the post isn't available

### Requirement: The quotes list is native on iOS
On iOS a post's quotes SHALL be listed natively, as native post cards, newest first, a page at a time, in the content-loading pattern, with the empty message when there are none and the unavailable message once the quoted post is gone for the viewer.

#### Scenario: Quotes on iOS
- **WHEN** alice taps Show quotes on a post with two quotes she may see, on iOS
- **THEN** she sees both as native cards, newest first
