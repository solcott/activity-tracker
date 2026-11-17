# Spec Delta

## MODIFIED Requirements

### Requirement: Requests from outside open their screen
A tap on the recording notification SHALL open Record, and a tap on the export-ready notification SHALL open Settings and its Account page, on the tab then shown. An unfinished recording of the signed-in account SHALL be offered when the app opens. A link the app is opened with, whether an `activitytracker` URL or a Universal Link, SHALL open its page on the tab it belongs to, with its parents under it (`ui/deep-links`).

#### Scenario: Recording notification
- **WHEN** an athlete on the Search tab taps the recording notification
- **THEN** Record opens over the Search tab

#### Scenario: Unfinished recording
- **WHEN** the app opens signed in with a recording of that account still going
- **THEN** Record opens over the feed

#### Scenario: Link to a settings page
- **WHEN** `activitytracker://settings/training-zones/history` is opened while the athlete is on the Search tab
- **THEN** the You tab is chosen, and its stack shows the profile, Settings, Training zones and History, with the system's back button on History

#### Scenario: Link while the app is running
- **WHEN** a link to a segment is opened while the app shows an activity on the Feed tab
- **THEN** the segment's page opens over the activity on the Feed tab
