# Spec Delta

## ADDED Requirements

### Requirement: The enlarged map is native on iOS
On iOS the enlarged route map SHALL be drawn with the system's map filling the page under the system's bar, titled with the activity's title or type, panned and zoomed north up with Recenter, showing the route as the detail page's map shows it, its loading and failures as `ui/content-loading` says.

#### Scenario: Opening the map
- **WHEN** an athlete on iOS taps the expand button on an activity's map
- **THEN** the native enlarged map opens with the activity's route framed and its title in the bar

#### Scenario: No route
- **WHEN** the activity has no route the viewer may see
- **THEN** the enlarged map shows no line
