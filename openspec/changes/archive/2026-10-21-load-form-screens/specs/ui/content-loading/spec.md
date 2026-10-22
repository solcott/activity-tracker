# Spec Delta

## MODIFIED Requirements

### Requirement: Progress before the first answer
Until a screen's content has loaded for the first time, the feed, the segment page, the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) and the form screens (edit profile, default zones, a sport's zones, edit activity and privacy zones) SHALL show that it is loading. They SHALL NOT show an empty state while a fetch is still in flight, even when nothing is cached for it yet.

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

### Requirement: First-load failure offers retry
When a screen's first load fails, the feed, the segment page, the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) and the form screens (edit profile, default zones, a sport's zones, edit activity and privacy zones) SHALL show why it failed and offer to try again. Trying again SHALL show that it is loading until the new attempt answers.

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

### Requirement: Load errors say why
A load error on the feed, the segment page, one of the activity and profile screens (activity page, route map, media gallery, profile, follow requests, training zones, threshold history and combine picker) or one of the form screens (edit profile, default zones, a sport's zones, edit activity and privacy zones) SHALL say what failed and, where it is known, why: no connection, the server failing, or a response the app couldn't read. It SHALL NOT show raw technical text such as status codes or exception messages.

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

### Requirement: Action failures leave loaded content in place
On the activity and profile screens and the form screens, a failed action (following, blocking or muting, answering a follow request, saving the training visibility, removing a threshold value, combining activities, liking, commenting, hiding or deleting a comment, deleting, ungrouping or splitting an activity, choosing a data source, adding a privacy zone, exporting, saving the profile, the default zones, a sport's zones or an activity's edits, changing or removing a privacy zone) SHALL report its own error and keep the loaded content on screen. It SHALL NOT be shown as a failed load or offer the load's retry.

#### Scenario: Follow fails offline
- **WHEN** an athlete taps Follow on a loaded profile while the device is offline
- **THEN** the profile stays on screen, and it says it couldn't update the follow because the device is offline

#### Scenario: Ungroup fails
- **WHEN** the owner ungroups an activity from its one recording and the write fails
- **THEN** the activity stays on screen, and the page says it couldn't ungroup the activity, with no load retry

#### Scenario: Profile save fails offline
- **WHEN** an athlete edits their profile and saves while the device is offline
- **THEN** the form stays with what they typed, and the screen says it couldn't save the profile because the device is offline, with no load retry

## ADDED Requirements

### Requirement: A loaded form keeps what the athlete typed
Once a form screen (edit profile, default zones, a sport's zones, edit activity or privacy zones) has loaded, a later load of the same content SHALL NOT replace the form's fields or what the athlete has typed into them. A later load that fails SHALL NOT hide the form or stop it from being saved.

#### Scenario: Rotate while editing an activity
- **WHEN** an athlete changes an activity's title on edit activity and the device rotates
- **THEN** the screen shows the title they typed, not the saved one

#### Scenario: Retry after loading
- **WHEN** an athlete has typed a new threshold on their default zones and the screen loads the profile again
- **THEN** the threshold they typed stays in its field

#### Scenario: Later load fails
- **WHEN** a loaded edit profile form loads the profile again and that load fails
- **THEN** the form stays with what the athlete typed, and saving it still works
