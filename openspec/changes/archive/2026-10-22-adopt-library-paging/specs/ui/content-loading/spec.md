# Spec Delta

## MODIFIED Requirements

### Requirement: Progress before the first answer
Until a screen's content has loaded for the first time, the feed, the segment page, the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker), the form screens (edit profile, default zones, a sport's zones, edit activity and privacy zones) and the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) SHALL show that it is loading. They SHALL NOT show an empty state while a fetch is still in flight, even when nothing is cached for it yet.

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

### Requirement: An empty answer is shown as empty
Once a load has answered, the feed, the profile's activities, follow requests, the combine picker and the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) SHALL show their empty state when the answer has nothing to list, and SHALL NOT keep showing that they are loading.

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

### Requirement: First-load failure offers retry
When a screen's first load fails, the feed, the segment page, the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker), the form screens (edit profile, default zones, a sport's zones, edit activity and privacy zones) and the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) SHALL show why it failed and offer to try again. Trying again SHALL show that it is loading until the new attempt answers.

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

### Requirement: Content stays on screen while it refreshes
When the feed, the segment page, one of the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) or one of the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) refreshes content it already shows, it SHALL keep showing that content with an indication that it is refreshing. It SHALL NOT replace the content with a full-screen loading state.

#### Scenario: Pull to refresh the feed
- **WHEN** an athlete pulls to refresh a feed that shows activities
- **THEN** the activities stay listed under a refresh indicator until the refresh answers

#### Scenario: Cached feed on launch
- **WHEN** an athlete opens the app with a feed cached from an earlier session
- **THEN** the cached activities show at once, with a refresh indicator while the feed is fetched

#### Scenario: Back to training zones from an editor
- **WHEN** an athlete edits a sport's zones from the training zones screen and goes back
- **THEN** the zones they last saw stay on screen under a refresh indicator while they reload

#### Scenario: Activity page reloads after a data-source choice
- **WHEN** the owner of a combined activity chooses another member as the source of its heart rate, and the page reloads for its new figures
- **THEN** the activity stays on screen under a refresh indicator until the reload answers

#### Scenario: Comments reload with the activity
- **WHEN** an activity's page that lists two pages of comments reloads
- **THEN** the comments listed stay on screen until the first page of the reload answers and replaces them

### Requirement: A failed refresh keeps the content
When refreshing content already on screen fails, the feed, the segment page, the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) and the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) SHALL keep showing that content, report the failure and why, and offer to try again.

#### Scenario: Feed refresh while offline
- **WHEN** an athlete pulls to refresh the feed while offline
- **THEN** the activities already listed stay, and the feed says it couldn't refresh because the device is offline

#### Scenario: Profile refresh while offline
- **WHEN** a profile that shows its header and activities refreshes while the device is offline
- **THEN** the header and activities stay, and the profile says it couldn't load because the device is offline

#### Scenario: Activity page refresh while offline
- **WHEN** an activity's page that shows the activity reloads while the device is offline
- **THEN** the activity, its charts and its segment efforts stay, and the page says it couldn't load the activity because the device is offline, and offers to try again

#### Scenario: Comments reload while offline
- **WHEN** an activity's page that lists comments reloads while the device is offline
- **THEN** the comments listed stay, and the page says it couldn't load the comments because the device is offline, and offers to try again

### Requirement: Loaded content survives coming back
When an athlete leaves the feed, the segment page, one of the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) or one of the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) for a screen opened from it and then comes back, the page SHALL show the content it last showed at once, without a full-screen loading state.

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

### Requirement: Load errors say why
A load error on the feed, the segment page, one of the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker), one of the form screens (edit profile, default zones, a sport's zones, edit activity and privacy zones) or one of the paged lists (blocked accounts, muted accounts, followers, following, athlete search and an activity's comments) SHALL say what failed and, where it is known, why: no connection, the server failing, or a response the app couldn't read. It SHALL NOT show raw technical text such as status codes or exception messages.

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

### Requirement: Action failures leave loaded content in place
On the activity and profile screens, the form screens and the blocked, muted and followers lists, a failed action (following, blocking or muting, unblocking or unmuting from a list, removing a follower, answering a follow request, saving the training visibility, removing a threshold value, combining activities, liking, commenting, hiding or deleting a comment, deleting, ungrouping or splitting an activity, choosing a data source, adding a privacy zone, exporting, saving the profile, the default zones, a sport's zones or an activity's edits, changing or removing a privacy zone) SHALL report its own error and keep the loaded content on screen. It SHALL NOT be shown as a failed load or offer the load's retry.

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

## ADDED Requirements

### Requirement: A failed later page keeps the earlier pages
When a paged list (blocked accounts, muted accounts, followers, following, athlete search, an activity's comments, or a thread's replies) fails to load a page after its first, it SHALL keep every item already listed, show below them that the rest couldn't be loaded and why, and offer to try again. Trying again SHALL load only the page that failed.

#### Scenario: Third page fails
- **WHEN** an athlete has two pages of a following list and the third fails because the device is offline
- **THEN** both pages stay listed at their scroll position, and below them the screen says it couldn't load more because the device is offline and offers to try again

#### Scenario: Trying again after a failed page
- **WHEN** the athlete then tries again and the third page loads
- **THEN** the third page is listed after the first two, which are not loaded again

#### Scenario: No more pages while a page has failed
- **WHEN** a page has failed and the athlete scrolls to the end again without trying again
- **THEN** no further page is requested

### Requirement: More loading shows below the list
While a paged list loads its next page, it SHALL keep its items and show that more are loading below them. Once the last page is listed, it SHALL load nothing more and show no loading indicator.

#### Scenario: Next page in flight
- **WHEN** an athlete scrolls to the end of a muted accounts list that has more pages
- **THEN** the accounts stay listed and a loading indicator shows below them until the next page is listed

#### Scenario: Last page
- **WHEN** the last page of a blocked accounts list is listed and the athlete scrolls to the end
- **THEN** no page is requested and no loading indicator shows

### Requirement: A new search keeps the last results until it answers
When the athlete changes the search query, athlete search SHALL keep the last query's results on screen with an indication that it is searching until the new query's first page answers, then show only the new results. A blank query SHALL list nothing and search for nothing.

#### Scenario: Refining a query
- **WHEN** results for "ann" are listed and the athlete types "anna"
- **THEN** the "ann" results stay with a searching indicator until the "anna" results replace them, and no "ann" page is added after they do

#### Scenario: Clearing the query
- **WHEN** the athlete clears the search box
- **THEN** no results are listed, nothing shows as searching, and no search is made
