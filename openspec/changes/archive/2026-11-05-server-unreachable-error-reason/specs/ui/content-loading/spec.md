## MODIFIED Requirements

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
- **THEN** the gallery says it couldn't load the activity because the server couldn't be reached, and offers to try again

#### Scenario: Training zones retry
- **WHEN** an athlete's training zones fail to load and they try again once the AppView answers
- **THEN** the screen shows that it is loading, then their zones, and no error

#### Scenario: Activity page offline, then retry
- **WHEN** an athlete opens an activity's page while the device is offline, then tries again once it is back online
- **THEN** the page says it couldn't load the activity because the server couldn't be reached and offers to try again, then shows that it is loading, then the activity, and no error

#### Scenario: Default zones fail, then retry
- **WHEN** an athlete opens their default zones while their profile can't be read, then tries again once it can
- **THEN** the screen says it couldn't load the profile and offers to try again, then shows that it is loading, then the form with their saved thresholds

#### Scenario: Edit activity offline
- **WHEN** an athlete opens edit activity while the device is offline
- **THEN** the screen says it couldn't load the activity because the server couldn't be reached, and offers to try again

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
- **THEN** the screen says it couldn't load the route because the server couldn't be reached and offers to try again, and doesn't say the activity has no route

### Requirement: A failed refresh keeps the content
When refreshing content already on screen fails, the feed, the segment page, the activity and profile screens and the paged lists SHALL keep showing that content, report the failure and why, and offer to try again.

#### Scenario: Feed refresh while offline
- **WHEN** an athlete pulls to refresh the feed while offline
- **THEN** the activities already listed stay, and the feed says it couldn't refresh because the server couldn't be reached

#### Scenario: Profile refresh while offline
- **WHEN** a profile that shows its header and activities refreshes while the device is offline
- **THEN** the header and activities stay, and the profile says it couldn't load because the server couldn't be reached

#### Scenario: Activity page refresh while offline
- **WHEN** an activity's page that shows the activity reloads while the device is offline
- **THEN** the activity, its charts and its segment efforts stay, and the page says it couldn't load the activity because the server couldn't be reached, and offers to try again

#### Scenario: Comments reload while offline
- **WHEN** an activity's page that lists comments reloads while the device is offline
- **THEN** the comments listed stay, and the page says it couldn't load the comments because the server couldn't be reached, and offers to try again

### Requirement: Load errors say why
A load error on the feed, the segment page, one of the activity and profile screens, one of the form screens or one of the paged lists SHALL say what failed and, where it is known, why: the server couldn't be reached (no connection, or the server is down), the server failing, or a response the app couldn't read. It SHALL NOT show raw technical text such as status codes or exception messages.

#### Scenario: Offline
- **WHEN** the feed fails to load because the device has no connection
- **THEN** the feed says it couldn't load the feed because the server couldn't be reached

#### Scenario: Server error
- **WHEN** a segment page fails to load because the AppView answered with a server error
- **THEN** the page says it couldn't load the segment because of a problem on the server, with no status code or error identifier

#### Scenario: Threshold history server error
- **WHEN** threshold history fails to load because the server answered with a server error
- **THEN** the screen says it couldn't load the profile because of a problem on the server, with no status code

#### Scenario: Activity page on the web while offline
- **WHEN** an athlete opens an activity's page in the web app while the browser has no connection
- **THEN** the page says it couldn't load the activity because the server couldn't be reached, not the browser's fetch error text

#### Scenario: Who liked it, offline
- **WHEN** an athlete shows who liked an activity while the device is offline
- **THEN** the list says it couldn't load the likes because the server couldn't be reached, with no exception text

#### Scenario: Privacy zones offline
- **WHEN** an athlete opens their privacy zones while the device is offline and no zones have been read this session
- **THEN** the screen says it couldn't load their privacy zones because the server couldn't be reached

#### Scenario: Search offline
- **WHEN** an athlete searches while the device is offline
- **THEN** the screen says it couldn't search because the server couldn't be reached, with no exception text

#### Scenario: Starred segments offline
- **WHEN** an athlete opens their starred segments while the device is offline
- **THEN** the screen says it couldn't load their starred segments because the server couldn't be reached, with no exception text

### Requirement: A profile shows before its activities
A profile SHALL show its header as soon as the header loads, without waiting for the athlete's activities. The activities SHALL show their own loading below the header, and a failure loading only them SHALL show why with an offer to try again, below the header, without hiding it.

#### Scenario: Activities still loading
- **WHEN** a profile's header has loaded and its activities have not, with none cached
- **THEN** the profile shows the header, and shows that the activities are loading

#### Scenario: Activities fail to load
- **WHEN** a profile's header loads but its activities fail to load because the device is offline
- **THEN** the profile shows the header, and below it says it couldn't load the activities because the server couldn't be reached, and offers to try again

### Requirement: Action failures leave loaded content in place
On the activity and profile screens, the form screens and the blocked, muted, followers and starred segments lists, a failed action SHALL report its own error and keep the loaded content on screen. Actions include following, blocking or muting, liking, commenting, answering a follow request, combining, splitting, exporting or deleting an activity, and saving a form; any other action on these screens behaves the same. A failed action SHALL NOT be shown as a failed load or offer the load's retry.

#### Scenario: Follow fails offline
- **WHEN** an athlete taps Follow on a loaded profile while the device is offline
- **THEN** the profile stays on screen, and it says it couldn't update the follow because the server couldn't be reached

#### Scenario: Ungroup fails
- **WHEN** the owner ungroups an activity from its one recording and the write fails
- **THEN** the activity stays on screen, and the page says it couldn't ungroup the activity, with no load retry

#### Scenario: Profile save fails offline
- **WHEN** an athlete edits their profile and saves while the device is offline
- **THEN** the form stays with what they typed, and the screen says it couldn't save the profile because the server couldn't be reached, with no load retry

#### Scenario: Unblock fails
- **WHEN** an athlete taps Unblock in their blocked accounts and the write fails
- **THEN** the account stays listed, the screen says it couldn't update the block, and scrolling to the end still loads the next page

#### Scenario: Unstar fails
- **WHEN** an athlete unstars a segment in their starred segments and the write can't be queued
- **THEN** the segment stays listed, the screen says it couldn't update the star, and scrolling to the end still loads the next page

#### Scenario: Publishing a segment fails offline
- **WHEN** an athlete publishes a new segment and the duplicate check fails because the device is offline
- **THEN** the route, the stretch they chose and the name they typed stay on screen, and the screen says it couldn't check the segment because the server couldn't be reached, with no load retry

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
- **THEN** the page says at once that it couldn't load the activity because the server couldn't be reached, without waiting through the tries

#### Scenario: Someone else's missing activity
- **WHEN** an athlete opens an activity they didn't write on this device, and the AppView doesn't serve it
- **THEN** the page says at once that it couldn't load the activity, and offers to try again

### Requirement: A failed later page keeps the earlier pages
When a paged list, or a thread's replies, fails to load a page after its first, it SHALL keep every item already listed, show below them that the rest couldn't be loaded and why, and offer to try again. Trying again SHALL load only the page that failed.

#### Scenario: Third page fails
- **WHEN** an athlete has two pages of a following list and the third fails because the device is offline
- **THEN** both pages stay listed at their scroll position, and below them the screen says it couldn't load more because the server couldn't be reached and offers to try again

#### Scenario: Trying again after a failed page
- **WHEN** the athlete then tries again and the third page loads
- **THEN** the third page is listed after the first two, which are not loaded again

#### Scenario: No more pages while a page has failed
- **WHEN** a page has failed and the athlete scrolls to the end again without trying again
- **THEN** no further page is requested
