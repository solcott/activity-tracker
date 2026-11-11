# Spec Delta

## ADDED Requirements

### Requirement: The gear page reads the same in both UIs
The gear page's words SHALL be chosen by shared code that both UIs ask: the item's kind line, brand and model, visibility and Retired notes, which totals apply to its type and how each reads, its starting usage, its breakdown rows, each component's line and mount dates, the mount sheet's title and each candidate's line. Which sections show, whether Edit, the collections and component management are offered, whether mounting can be used, which error shows by Components, and the unmount date's first and initial day SHALL be decided there too.

#### Scenario: Skis
- **WHEN** both UIs show skis with no distance and 41,000 m of vertical
- **THEN** both list Vertical and no Distance among the totals

#### Scenario: One activity type
- **WHEN** an item's activities are all of one type
- **THEN** neither UI shows the By activity type breakdown

#### Scenario: A past component
- **WHEN** Chain #3 was mounted from 1 March 2025 to 1 July 2026
- **THEN** both UIs read its dates the same way in the viewer's time zone
