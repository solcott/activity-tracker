# Spec Delta

## ADDED Requirements

### Requirement: The profile is native on iOS
On iOS a profile SHALL be drawn with the system's own list and navigation bar, in the shared profile's words: the athlete's name as its title, their avatar and handle, the labels' notices, the bio, the details the viewer may see, and the activities, followers and following counts, the last two opening their lists.

#### Scenario: Opening followers
- **WHEN** alice taps the followers count on bob's profile
- **THEN** bob's Followers list opens, as from the shared profile

### Requirement: A native profile lists activities as native cards
On iOS a profile SHALL list the athlete's activities as native activity cards below the header, loaded on their own as `ui/content-loading` says, with the shared empty message.

#### Scenario: Bob's profile
- **WHEN** alice opens bob's profile and he has two activities
- **THEN** the page is titled "Bob", shows "@bob.test", the counts, Follow, and two activity cards

#### Scenario: No activities
- **WHEN** alice opens the profile of an athlete with no activities she may see
- **THEN** the page reads "No activities yet." under the header

### Requirement: Following from a native profile
On iOS someone else's profile SHALL offer Follow, Following when the viewer follows them, or Requested while the viewer's follow waits for approval, as the shared profile does; Following and Requested SHALL be drawn as the secondary button, and Follow as the primary one. A tap SHALL follow, unfollow or cancel the request, and the button SHALL show progress and be unavailable while that is written.

#### Scenario: Requested
- **WHEN** alice has asked to follow carol, whose policy is `approve`
- **THEN** carol's profile shows Requested, and a tap cancels the request

#### Scenario: A follow in flight
- **WHEN** alice has tapped Follow on bob's profile and the follow is being written
- **THEN** the button shows progress and can't be tapped

### Requirement: The athlete's own entries on a native profile
On iOS the athlete's own profile SHALL offer Edit profile, Starred segments, Activity invites with their number when they have pending invites, and Follow requests with their number when their policy is `approve`, and Settings in the navigation bar, as the shared profile does; each SHALL open its page.

#### Scenario: Requests on the own profile
- **WHEN** alice, with policy `approve` and two undeclined requests, opens her own profile on iOS
- **THEN** she sees Follow requests (2), which opens Follow requests
