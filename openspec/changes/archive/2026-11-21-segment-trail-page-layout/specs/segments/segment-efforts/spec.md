# Spec Delta

## MODIFIED Requirements

### Requirement: The segment page is native on iOS
On iOS a segment's page SHALL be drawn with the system's own list and bar, titled with the segment's current name, with Star in the bar when offered, its line on the native route map, its heading, its figures, its elevation profile, its boards, its Trail details, its names, its efforts and its creator, credit and status, in the shared page's order and words (`segments/segment-page`), its loading and failures as `ui/content-loading` says.

#### Scenario: An active run
- **WHEN** a viewer opens an active ride segment with a Mayor
- **THEN** the page shows the map, the heading, the length in the figure row, the Leaderboard and Mayor cards, the names and the creator

#### Scenario: A lift
- **WHEN** a viewer opens a ski lift segment
- **THEN** the page shows "Ski lift" with the lift's icon, no Leaderboard or Mayor card, no names, and "Your rides" over its efforts

### Requirement: The native segment page opens what the shared page opens
Expanding the native page's map SHALL open the enlarged map, the Leaderboard card the leaderboard, the Mayor card the Mayors board, a trail's name in the heading its Trail page, Open the original the earlier segment, and an effort its activity.

#### Scenario: Opening an effort
- **WHEN** a viewer taps one of their efforts on the native segment page
- **THEN** that effort's activity opens over the page

#### Scenario: Opening the trail
- **WHEN** a viewer taps "Hogback Ridge Trail" in the heading of the native segment page
- **THEN** that trail's native Trail page opens over the page
