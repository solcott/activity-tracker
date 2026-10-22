# Spec Delta

## MODIFIED Requirements

### Requirement: Progress before the first answer
Until a screen's content has loaded for the first time, the feed, the segment page, the activity and profile screens, the form screens and the paged lists SHALL show that it is loading. They SHALL NOT show an empty state while a fetch is still in flight, even when nothing is cached for it yet.

#### Scenario: First visit to a feed filter
- **WHEN** an athlete selects a feed filter they have never used, with nothing cached for it, and the fetch is still in flight
- **THEN** the feed shows that it is loading, not that no activities match

#### Scenario: Opening a segment
- **WHEN** an athlete opens a segment page and the segment hasn't loaded yet
- **THEN** the page shows that it is loading

#### Scenario: Opening a route map
- **WHEN** an athlete opens an activity's full-screen map and the activity hasn't loaded yet
- **THEN** the screen shows that it is loading, not an empty map

#### Scenario: Opening follow requests
- **WHEN** an athlete opens their follow requests and they haven't loaded yet
- **THEN** the screen shows that it is loading, not that there are no requests

#### Scenario: Opening an activity
- **WHEN** an athlete opens an activity's page and the activity hasn't loaded yet
- **THEN** the page shows that it is loading

#### Scenario: Opening edit profile
- **WHEN** an athlete opens edit profile and their profile hasn't loaded yet
- **THEN** the screen shows that it is loading, not a form with empty fields

#### Scenario: Opening blocked accounts
- **WHEN** an athlete opens their blocked accounts and the first page hasn't loaded yet
- **THEN** the screen shows that it is loading, not that they block nobody

#### Scenario: First search
- **WHEN** an athlete types a name into athlete search and its first page hasn't answered yet
- **THEN** the screen shows that it is searching, not that nobody matched

#### Scenario: Opening starred segments
- **WHEN** an athlete opens their starred segments and the first page hasn't loaded yet
- **THEN** the screen shows that it is loading, not that they starred nothing

#### Scenario: Creating a segment
- **WHEN** an athlete chooses to create a segment from their activity and its route or their privacy zones haven't loaded yet
- **THEN** the screen shows that it is loading, not that the activity has no route

### Requirement: An empty answer is shown as empty
Once a load has answered, the feed, the profile's activities, follow requests, the combine picker and the paged lists SHALL show their empty state when the answer has nothing to list, and SHALL NOT keep showing that they are loading.

#### Scenario: Filter with no matches
- **WHEN** an athlete selects a filter the server answers with no activities
- **THEN** the feed shows that no activities match, with no loading indicator

#### Scenario: No follow requests
- **WHEN** an athlete opens their follow requests and the answer has none
- **THEN** the screen says there are no requests, with no loading indicator

#### Scenario: Nothing to combine
- **WHEN** an athlete opens the combine picker and none of their other activities can be combined with this one
- **THEN** the picker says there is nothing to combine, with no loading indicator

#### Scenario: Nobody matches a search
- **WHEN** an athlete searches for a name the server answers with no athletes
- **THEN** the screen says nobody matched, with no loading indicator

#### Scenario: No muted accounts
- **WHEN** an athlete who mutes nobody opens their muted accounts
- **THEN** the screen says they mute nobody, with no loading indicator

#### Scenario: No starred segments
- **WHEN** an athlete who has starred no segments opens their starred segments
- **THEN** the screen says they have no starred segments, with no loading indicator

#### Scenario: Nobody on a board
- **WHEN** a viewer chooses the people-they-follow board of a segment none of them has ridden
- **THEN** the leaderboard says nobody is on it, with no loading indicator

### Requirement: First-load failure offers retry
When a screen's first load fails, the feed, the segment page, the activity and profile screens, the form screens and the paged lists SHALL show why it failed and offer to try again. Trying again SHALL show that it is loading until the new attempt answers.

#### Scenario: Segment fails to load
- **WHEN** an athlete opens a segment page while the AppView is unreachable
- **THEN** the page says it couldn't load the segment and why, and offers to try again

#### Scenario: Retry succeeds
- **WHEN** the athlete tries again and the segment loads
- **THEN** the page shows that it is loading, then the segment, and no error

#### Scenario: Media gallery offline
- **WHEN** an athlete opens an activity's photos while the device has no connection
- **THEN** the gallery says it couldn't load the activity because the device is offline, and offers to try again

#### Scenario: Training zones retry
- **WHEN** an athlete's training zones fail to load and they try again once the AppView answers
- **THEN** the screen shows that it is loading, then their zones, and no error

#### Scenario: Activity page offline, then retry
- **WHEN** an athlete opens an activity's page while the device is offline, then tries again once it is back online
- **THEN** the page says it couldn't load the activity because the device is offline and offers to try again, then shows that it is loading, then the activity, and no error

#### Scenario: Default zones fail, then retry
- **WHEN** an athlete opens their default zones while their profile can't be read, then tries again once it can
- **THEN** the screen says it couldn't load the profile and offers to try again, then shows that it is loading, then the form with their saved thresholds

#### Scenario: Edit activity offline
- **WHEN** an athlete opens edit activity while the device is offline
- **THEN** the screen says it couldn't load the activity because the device is offline, and offers to try again

#### Scenario: Privacy zones can't be read
- **WHEN** an athlete opens their privacy zones and the zones can't be read this session
- **THEN** the screen says it couldn't load their privacy zones and why, offers to try again, and no zone can be added, changed or removed

#### Scenario: Followers fail, then retry
- **WHEN** an athlete opens a followers list while the AppView is unreachable, then tries again once it answers
- **THEN** the screen says it couldn't load the followers and why and offers to try again, then shows that it is loading, then the first page of followers, and no error

#### Scenario: Leaderboard fails, then retry
- **WHEN** a viewer opens a segment's leaderboard while the AppView is unreachable, then tries again once it answers
- **THEN** the screen says it couldn't load the leaderboard and why and offers to try again, then shows that it is loading, then the board, and no error

#### Scenario: Route can't be read for a new segment
- **WHEN** an athlete chooses to create a segment from their activity while the device is offline and its track can't be read
- **THEN** the screen says it couldn't load the route because the device is offline and offers to try again, and doesn't say the activity has no route

### Requirement: Loaded content survives coming back
When an athlete leaves the feed, the segment page, one of the activity and profile screens, one of the paged lists or the create segment screen for a screen opened from it and then comes back, the page SHALL show the content it last showed at once, without a full-screen loading state.

#### Scenario: Back to a segment
- **WHEN** an athlete opens an activity from a segment page's efforts and goes back
- **THEN** the segment page shows the segment at once, not a loading state

#### Scenario: Back to a profile
- **WHEN** an athlete opens an activity from a profile and goes back
- **THEN** the profile shows its header and activities at once, not a loading state

#### Scenario: Back to an activity
- **WHEN** an athlete opens a segment from an activity's efforts, or its photos or its author's profile, and goes back
- **THEN** the activity's page shows the activity, its charts and its segment efforts at once, not a loading state

#### Scenario: Back to a followers list
- **WHEN** an athlete has scrolled through three pages of a followers list, opens a follower's profile and goes back
- **THEN** the list shows all three pages at once, without loading any of them again

#### Scenario: Back to search results
- **WHEN** an athlete opens an athlete's profile from search results and goes back
- **THEN** the query and its results show at once, not a loading state

#### Scenario: Back to an activity's comments
- **WHEN** an athlete has loaded a second page of an activity's comments and more replies in one thread, opens a commenter's profile and goes back
- **THEN** both pages of comments and the thread's extra replies show at once

#### Scenario: Back to a leaderboard
- **WHEN** a viewer has scrolled through two pages of a this-year leaderboard, opens an entry's activity and goes back
- **THEN** the this-year board shows both pages at once, without loading either again

#### Scenario: Back from a duplicate
- **WHEN** an athlete creating a segment is shown an existing segment their stretch duplicates, opens it and goes back
- **THEN** the screen shows the route, the stretch they chose and the name they typed at once, not a loading state

### Requirement: Load errors say why
A load error on the feed, the segment page, one of the activity and profile screens, one of the form screens or one of the paged lists SHALL say what failed and, where it is known, why: no connection, the server failing, or a response the app couldn't read. It SHALL NOT show raw technical text such as status codes or exception messages.

#### Scenario: Offline
- **WHEN** the feed fails to load because the device has no connection
- **THEN** the feed says it couldn't load the feed because the device is offline

#### Scenario: Server error
- **WHEN** a segment page fails to load because the AppView answered with a server error
- **THEN** the page says it couldn't load the segment because of a problem on the server, with no status code or error identifier

#### Scenario: Threshold history server error
- **WHEN** threshold history fails to load because the server answered with a server error
- **THEN** the screen says it couldn't load the profile because of a problem on the server, with no status code

#### Scenario: Activity page on the web while offline
- **WHEN** an athlete opens an activity's page in the web app while the browser has no connection
- **THEN** the page says it couldn't load the activity because the device is offline, not the browser's fetch error text

#### Scenario: Who liked it, offline
- **WHEN** an athlete shows who liked an activity while the device is offline
- **THEN** the list says it couldn't load the likes because the device is offline, with no exception text

#### Scenario: Privacy zones offline
- **WHEN** an athlete opens their privacy zones while the device is offline and no zones have been read this session
- **THEN** the screen says it couldn't load their privacy zones because the device is offline

#### Scenario: Search offline
- **WHEN** an athlete searches while the device is offline
- **THEN** the screen says it couldn't search because the device is offline, with no exception text

#### Scenario: Starred segments offline
- **WHEN** an athlete opens their starred segments while the device is offline
- **THEN** the screen says it couldn't load their starred segments because the device is offline, with no exception text

### Requirement: Action failures leave loaded content in place
On the activity and profile screens, the form screens and the blocked, muted, followers and starred segments lists, a failed action SHALL report its own error and keep the loaded content on screen. Actions include following, blocking or muting, liking, commenting, answering a follow request, combining, splitting, exporting or deleting an activity, unstarring a segment, and saving a form; any other action on these screens behaves the same. A failed action SHALL NOT be shown as a failed load or offer the load's retry.

#### Scenario: Follow fails offline
- **WHEN** an athlete taps Follow on a loaded profile while the device is offline
- **THEN** the profile stays on screen, and it says it couldn't update the follow because the device is offline

#### Scenario: Ungroup fails
- **WHEN** the owner ungroups an activity from its one recording and the write fails
- **THEN** the activity stays on screen, and the page says it couldn't ungroup the activity, with no load retry

#### Scenario: Profile save fails offline
- **WHEN** an athlete edits their profile and saves while the device is offline
- **THEN** the form stays with what they typed, and the screen says it couldn't save the profile because the device is offline, with no load retry

#### Scenario: Unblock fails
- **WHEN** an athlete taps Unblock in their blocked accounts and the write fails
- **THEN** the account stays listed, the screen says it couldn't update the block, and scrolling to the end still loads the next page

#### Scenario: Unstar fails
- **WHEN** an athlete unstars a segment in their starred segments and the write can't be queued
- **THEN** the segment stays listed, the screen says it couldn't update the star, and scrolling to the end still loads the next page

#### Scenario: Publishing a segment fails offline
- **WHEN** an athlete publishes a new segment and the duplicate check fails because the device is offline
- **THEN** the route, the stretch they chose and the name they typed stay on screen, and the screen says it couldn't check the segment because the device is offline, with no load retry

## ADDED Requirements

### Requirement: A new filter lists only its own items
When the athlete changes a filter on a paged list, the list SHALL drop the previous filter's items and show that it is loading until the new filter's first page answers. It SHALL NOT show the previous filter's items, or anything derived from them, under the new filter.

#### Scenario: Changing a leaderboard's period
- **WHEN** a viewer looking at the all-time leaderboard chooses this year, and the this-year board hasn't answered yet
- **THEN** no all-time entries are listed and the screen shows that it is loading, then the this-year entries

#### Scenario: The viewer's own place follows the filter
- **WHEN** a viewer ranked 140th all-time, and not on the this-year board, chooses this year
- **THEN** their all-time entry is no longer shown, and no own entry shows once the this-year board answers
