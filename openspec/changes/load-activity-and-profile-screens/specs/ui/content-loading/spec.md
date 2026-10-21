# Spec Delta

## MODIFIED Requirements

### Requirement: Progress before the first answer
Until a screen's content has loaded for the first time, the feed, the segment page and the activity and profile screens (route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) SHALL show that it is loading. They SHALL NOT show an empty state while a fetch is still in flight, even when nothing is cached for it yet.

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

### Requirement: An empty answer is shown as empty
Once a load has answered, the feed, the profile's activities, follow requests and the combine picker SHALL show their empty state when the answer has nothing to list, and SHALL NOT keep showing that they are loading.

#### Scenario: Filter with no matches
- **WHEN** an athlete selects a filter the server answers with no activities
- **THEN** the feed shows that no activities match, with no loading indicator

#### Scenario: No follow requests
- **WHEN** an athlete opens their follow requests and the answer has none
- **THEN** the screen says there are no requests, with no loading indicator

#### Scenario: Nothing to combine
- **WHEN** an athlete opens the combine picker and none of their other activities can be combined with this one
- **THEN** the picker says there is nothing to combine, with no loading indicator

### Requirement: First-load failure offers retry
When a screen's first load fails, the feed, the segment page and the activity and profile screens (route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) SHALL show why it failed and offer to try again. Trying again SHALL show that it is loading until the new attempt answers.

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

### Requirement: Content stays on screen while it refreshes
When the feed, the segment page or one of the activity and profile screens (route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) refreshes content it already shows, it SHALL keep showing that content with an indication that it is refreshing. It SHALL NOT replace the content with a full-screen loading state.

#### Scenario: Pull to refresh the feed
- **WHEN** an athlete pulls to refresh a feed that shows activities
- **THEN** the activities stay listed under a refresh indicator until the refresh answers

#### Scenario: Cached feed on launch
- **WHEN** an athlete opens the app with a feed cached from an earlier session
- **THEN** the cached activities show at once, with a refresh indicator while the feed is fetched

#### Scenario: Back to training zones from an editor
- **WHEN** an athlete edits a sport's zones from the training zones screen and goes back
- **THEN** the zones they last saw stay on screen under a refresh indicator while they reload

### Requirement: A failed refresh keeps the content
When refreshing content already on screen fails, the feed, the segment page and the activity and profile screens (route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) SHALL keep showing that content, report the failure and why, and offer to try again.

#### Scenario: Feed refresh while offline
- **WHEN** an athlete pulls to refresh the feed while offline
- **THEN** the activities already listed stay, and the feed says it couldn't refresh because the device is offline

#### Scenario: Profile refresh while offline
- **WHEN** a profile that shows its header and activities refreshes while the device is offline
- **THEN** the header and activities stay, and the profile says it couldn't load because the device is offline

### Requirement: Loaded content survives coming back
When an athlete leaves the feed, the segment page or one of the activity and profile screens (route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) for a screen opened from it and then comes back, the page SHALL show the content it last showed at once, without a full-screen loading state.

#### Scenario: Back to a segment
- **WHEN** an athlete opens an activity from a segment page's efforts and goes back
- **THEN** the segment page shows the segment at once, not a loading state

#### Scenario: Back to a profile
- **WHEN** an athlete opens an activity from a profile and goes back
- **THEN** the profile shows its header and activities at once, not a loading state

### Requirement: Load errors say why
A load error on the feed, the segment page or one of the activity and profile screens (route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) SHALL say what failed and, where it is known, why: no connection, the server failing, or a response the app couldn't read. It SHALL NOT show raw technical text such as status codes or exception messages.

#### Scenario: Offline
- **WHEN** the feed fails to load because the device has no connection
- **THEN** the feed says it couldn't load the feed because the device is offline

#### Scenario: Server error
- **WHEN** a segment page fails to load because the AppView answered with a server error
- **THEN** the page says it couldn't load the segment because of a problem on the server, with no status code or error identifier

#### Scenario: Threshold history server error
- **WHEN** threshold history fails to load because the server answered with a server error
- **THEN** the screen says it couldn't load the profile because of a problem on the server, with no status code

### Requirement: Supplementary content fails quietly
The segment page SHALL show the segment when the viewer's own efforts on it fail to load, without them and without an error. The athlete's own profile SHALL show without the count of their follow requests when that count fails to load, without an error.

#### Scenario: Efforts unavailable
- **WHEN** a segment loads but the viewer's efforts on it fail to load
- **THEN** the page shows the segment with no efforts listed and no error

#### Scenario: Follow-request count unavailable
- **WHEN** an athlete who approves followers opens their own profile and their follow requests fail to load
- **THEN** the profile shows without a follow-request count and with no error

## ADDED Requirements

### Requirement: A profile shows before its activities
A profile SHALL show its header as soon as the header loads, without waiting for the athlete's activities. The activities SHALL show their own loading below the header, and a failure loading only them SHALL show why with an offer to try again, below the header, without hiding it.

#### Scenario: Activities still loading
- **WHEN** a profile's header has loaded and its activities have not, with none cached
- **THEN** the profile shows the header, and shows that the activities are loading

#### Scenario: Activities fail to load
- **WHEN** a profile's header loads but its activities fail to load because the device is offline
- **THEN** the profile shows the header, and below it says it couldn't load the activities because the device is offline, and offers to try again

### Requirement: Action failures leave loaded content in place
On the activity and profile screens, a failed action (following, blocking or muting, answering a follow request, saving the training visibility, removing a threshold value, combining activities) SHALL report its own error and keep the loaded content on screen. It SHALL NOT be shown as a failed load or offer the load's retry.

#### Scenario: Follow fails offline
- **WHEN** an athlete taps Follow on a loaded profile while the device is offline
- **THEN** the profile stays on screen, and it says it couldn't update the follow because the device is offline

### Requirement: Answered follow requests stay answered
A follow request the athlete approved or declined on the follow-requests screen SHALL NOT show again as unanswered when the list reloads, even before the server has caught up with the answer.

#### Scenario: Approve, then come back
- **WHEN** an athlete approves a request, opens another athlete's profile from the list and goes back
- **THEN** the approved request is not listed among the requests
