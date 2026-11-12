# Spec Delta

## ADDED Requirements

### Requirement: The segment page is native on iOS
On iOS a segment's page SHALL be drawn with the system's own list and bar, titled with the segment's current name, with Star in the bar when offered, its line on the native route map, its about lines, its elevation, its credit, its status, its boards and its names, in the shared page's words, its loading and failures as `ui/content-loading` says.

#### Scenario: An active run
- **WHEN** a viewer opens an active ride segment with a Mayor
- **THEN** the page shows the map, the length and type, the creator, the Leaderboard button, the Mayor card and the names

#### Scenario: A lift
- **WHEN** a viewer opens a ski lift segment
- **THEN** the page shows "Ski lift" with the lift's icon, no Leaderboard, Mayor card or names, and "Your rides" over its efforts

### Requirement: The native segment page opens what the shared page opens
Expanding the native page's map SHALL open the enlarged map, the Leaderboard button the leaderboard, the Mayor card the Mayors board, Open the original the earlier segment, and an effort its activity.

#### Scenario: Opening an effort
- **WHEN** a viewer taps one of their efforts on the native segment page
- **THEN** that effort's activity opens over the page

### Requirement: The native segment page lists the viewer's efforts
The native page SHALL list the viewer's own efforts fastest first, each with its date, its time, PR on their fastest, its rank when in the top 10, why it isn't ranked when the shared rules say, and e-bike when it was one, or say there are none.

#### Scenario: No efforts
- **WHEN** the viewer has no efforts on the segment
- **THEN** the native page reads the shared page's empty message under the efforts' heading
