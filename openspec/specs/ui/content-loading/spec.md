# ui/content-loading Specification

## Purpose

How a screen shows content it loads from the network or its cache: what the athlete sees before anything has loaded, while it refreshes, when loading fails, and when they come back to it, so that loaded content is never thrown away to show a spinner or an error.

## Requirements

### Requirement: Progress before the first answer
Until a screen's content has loaded for the first time, the feed and the segment page SHALL show that it is loading. They SHALL NOT show an empty state while a fetch is still in flight, even when nothing is cached for it yet.

#### Scenario: First visit to a feed filter
- **WHEN** an athlete selects a feed filter they have never used, with nothing cached for it, and the fetch is still in flight
- **THEN** the feed shows that it is loading, not that no activities match

#### Scenario: Opening a segment
- **WHEN** an athlete opens a segment page and the segment hasn't loaded yet
- **THEN** the page shows that it is loading

### Requirement: An empty answer is shown as empty
Once a load has answered, the feed SHALL show its empty state when the answer has no activities, and SHALL NOT keep showing that it is loading.

#### Scenario: Filter with no matches
- **WHEN** an athlete selects a filter the server answers with no activities
- **THEN** the feed shows that no activities match, with no loading indicator

### Requirement: First-load failure offers retry
When a screen's first load fails, the feed and the segment page SHALL show why it failed and offer to try again. Trying again SHALL show that it is loading until the new attempt answers.

#### Scenario: Segment fails to load
- **WHEN** an athlete opens a segment page while the AppView is unreachable
- **THEN** the page says it couldn't load the segment and why, and offers to try again

#### Scenario: Retry succeeds
- **WHEN** the athlete tries again and the segment loads
- **THEN** the page shows that it is loading, then the segment, and no error

### Requirement: Content stays on screen while it refreshes
When the feed or the segment page refreshes content it already shows, it SHALL keep showing that content with an indication that it is refreshing. It SHALL NOT replace the content with a full-screen loading state.

#### Scenario: Pull to refresh the feed
- **WHEN** an athlete pulls to refresh a feed that shows activities
- **THEN** the activities stay listed under a refresh indicator until the refresh answers

#### Scenario: Cached feed on launch
- **WHEN** an athlete opens the app with a feed cached from an earlier session
- **THEN** the cached activities show at once, with a refresh indicator while the feed is fetched

### Requirement: A failed refresh keeps the content
When refreshing content already on screen fails, the feed and the segment page SHALL keep showing that content, report the failure and why, and offer to try again.

#### Scenario: Feed refresh while offline
- **WHEN** an athlete pulls to refresh the feed while offline
- **THEN** the activities already listed stay, and the feed says it couldn't refresh because the device is offline

### Requirement: Loaded content survives coming back
When an athlete leaves the feed or the segment page for a screen opened from it and then comes back, the page SHALL show the content it last showed at once, without a full-screen loading state.

#### Scenario: Back to a segment
- **WHEN** an athlete opens an activity from a segment page's efforts and goes back
- **THEN** the segment page shows the segment at once, not a loading state

### Requirement: Load errors say why
A load error on the feed or the segment page SHALL say what failed and, where it is known, why: no connection, the server failing, or a response the app couldn't read. It SHALL NOT show raw technical text such as status codes or exception messages.

#### Scenario: Offline
- **WHEN** the feed fails to load because the device has no connection
- **THEN** the feed says it couldn't load the feed because the device is offline

#### Scenario: Server error
- **WHEN** a segment page fails to load because the AppView answered with a server error
- **THEN** the page says it couldn't load the segment because of a problem on the server, with no status code or error identifier

### Requirement: Supplementary content fails quietly
The segment page SHALL show the segment when the viewer's own efforts on it fail to load, without them and without an error.

#### Scenario: Efforts unavailable
- **WHEN** a segment loads but the viewer's efforts on it fail to load
- **THEN** the page shows the segment with no efforts listed and no error
