# ui/content-loading Specification

## Purpose

How a screen shows content it loads from the network or its cache: what the athlete sees before anything has loaded, while it refreshes, when loading fails, and when they come back to it, so that loaded content is never thrown away to show a spinner or an error.

The requirements name these groups of screens:
- **The activity and profile screens:** the activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker.
- **The form screens:** edit profile, default zones, a sport's zones, edit activity, privacy zones and create segment.
- **The paged lists:** blocked accounts, muted accounts, followers, following, athlete search, an activity's comments, starred segments and a segment's leaderboard.

Every screen that loads content is the feed, the segment page, or in one of these groups.

## Requirements

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

### Requirement: Content stays on screen while it refreshes
When the feed, the segment page, one of the activity and profile screens or one of the paged lists refreshes content it already shows, it SHALL keep showing that content with an indication that it is refreshing. It SHALL NOT replace the content with a full-screen loading state.

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
When refreshing content already on screen fails, the feed, the segment page, the activity and profile screens and the paged lists SHALL keep showing that content, report the failure and why, and offer to try again.

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

### Requirement: Supplementary content fails quietly
The segment page SHALL show the segment when the viewer's own efforts on it fail to load, without them and without an error. The athlete's own profile SHALL show without the count of their follow requests when that count fails to load, without an error. The activity page SHALL show the activity without its charts, segment efforts, combine suggestion, the owner's full route or the original file to download when any of those fails to load, without an error for it.

#### Scenario: Efforts unavailable
- **WHEN** a segment loads but the viewer's efforts on it fail to load
- **THEN** the page shows the segment with no efforts listed and no error

#### Scenario: Follow-request count unavailable
- **WHEN** an athlete who approves followers opens their own profile and their follow requests fail to load
- **THEN** the profile shows without a follow-request count and with no error

#### Scenario: Charts unavailable
- **WHEN** an activity loads but its streams fail to load
- **THEN** the page shows the activity's figures without charts and with no error

#### Scenario: Segment efforts on an activity unavailable
- **WHEN** an activity loads but its segment efforts fail to load
- **THEN** the page shows the activity with no segments section and no error

#### Scenario: Owner's track unavailable
- **WHEN** an athlete opens their own activity and its full track can't be read
- **THEN** the map shows the route as others see it, and the page shows no error

### Requirement: A profile shows before its activities
A profile SHALL show its header as soon as the header loads, without waiting for the athlete's activities. The activities SHALL show their own loading below the header, and a failure loading only them SHALL show why with an offer to try again, below the header, without hiding it.

#### Scenario: Activities still loading
- **WHEN** a profile's header has loaded and its activities have not, with none cached
- **THEN** the profile shows the header, and shows that the activities are loading

#### Scenario: Activities fail to load
- **WHEN** a profile's header loads but its activities fail to load because the device is offline
- **THEN** the profile shows the header, and below it says it couldn't load the activities because the device is offline, and offers to try again

### Requirement: Action failures leave loaded content in place
On the activity and profile screens, the form screens and the blocked, muted, followers and starred segments lists, a failed action SHALL report its own error and keep the loaded content on screen. Actions include following, blocking or muting, liking, commenting, answering a follow request, combining, splitting, exporting or deleting an activity, and saving a form; any other action on these screens behaves the same. A failed action SHALL NOT be shown as a failed load or offer the load's retry.

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

### Requirement: Answered follow requests stay answered
A follow request the athlete approved or declined on the follow-requests screen SHALL NOT show again as unanswered when the list reloads, even before the server has caught up with the answer.

#### Scenario: Approve, then come back
- **WHEN** an athlete approves a request, opens another athlete's profile from the list and goes back
- **THEN** the approved request is not listed among the requests

### Requirement: A just-written activity waits to be indexed
When the athlete opens an activity they wrote moments ago on this device (one just saved, or combined, or edited), and the AppView doesn't serve it yet, the activity page SHALL keep showing that it is loading and ask again every 1.5 seconds, up to 10 times. It SHALL NOT show an error while it waits. Only after the last try SHALL it show the failure with an offer to try again.

#### Scenario: Indexed on a later try
- **WHEN** an athlete saves an activity and its page opens before the AppView has indexed it, and the AppView serves it on the third try
- **THEN** the page shows that it is loading throughout, then the activity, and never shows an error

#### Scenario: Never indexed
- **WHEN** a just-saved activity is still not served after the tenth try
- **THEN** the page says it couldn't load the activity, and offers to try again

#### Scenario: Offline while waiting
- **WHEN** an athlete opens an activity they just saved while the device is offline
- **THEN** the page says at once that it couldn't load the activity because the device is offline, without waiting through the tries

#### Scenario: Someone else's missing activity
- **WHEN** an athlete opens an activity they didn't write on this device, and the AppView doesn't serve it
- **THEN** the page says at once that it couldn't load the activity, and offers to try again

### Requirement: A loaded form keeps what the athlete typed
Once a form screen has loaded, a later load of the same content SHALL NOT replace the form's fields or what the athlete has typed into them. A later load that fails SHALL NOT hide the form or stop it from being saved.

#### Scenario: Rotate while editing an activity
- **WHEN** an athlete changes an activity's title on edit activity and the device rotates
- **THEN** the screen shows the title they typed, not the saved one

#### Scenario: Retry after loading
- **WHEN** an athlete has typed a new threshold on their default zones and the screen loads the profile again
- **THEN** the threshold they typed stays in its field

#### Scenario: Later load fails
- **WHEN** a loaded edit profile form loads the profile again and that load fails
- **THEN** the form stays with what the athlete typed, and saving it still works

### Requirement: A failed later page keeps the earlier pages
When a paged list, or a thread's replies, fails to load a page after its first, it SHALL keep every item already listed, show below them that the rest couldn't be loaded and why, and offer to try again. Trying again SHALL load only the page that failed.

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
### Requirement: A new filter lists only its own items
When the athlete changes a filter on a paged list, the list SHALL drop the previous filter's items and show that it is loading until the new filter's first page answers. It SHALL NOT show the previous filter's items, or anything derived from them, under the new filter.

#### Scenario: Changing a leaderboard's period
- **WHEN** a viewer looking at the all-time leaderboard chooses this year, and the this-year board hasn't answered yet
- **THEN** no all-time entries are listed and the screen shows that it is loading, then the this-year entries

#### Scenario: The viewer's own place follows the filter
- **WHEN** a viewer ranked 140th all-time, and not on the this-year board, chooses this year
- **THEN** their all-time entry is no longer shown, and no own entry shows once the this-year board answers
