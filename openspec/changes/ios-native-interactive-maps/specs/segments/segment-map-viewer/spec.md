# Spec Delta

## ADDED Requirements

### Requirement: The enlarged segment map is native on iOS
On iOS the enlarged segment map SHALL be drawn natively under the system's navigation bar titled with the segment's current name: the
segment's line on the native route map filling the page, panned and zoomed north-up with Recenter, its data credit over the map's
top-leading corner with the line framed below it, and the shared load failure with Retry and refresh status.

#### Scenario: Opening it
- **WHEN** alice opens the enlarged map of an OpenStreetMap segment on iOS
- **THEN** the native page shows the line filling the page under the segment's name, with "© OpenStreetMap contributors" and its licence
  over the top-leading corner

#### Scenario: Recentering
- **WHEN** alice pans the native enlarged map away and taps Recenter
- **THEN** the whole segment is framed again below its credit
