# Spec Delta

## ADDED Requirements

### Requirement: Segment browse is native on iOS
On iOS the Search tab's Segments mode SHALL show segment browse natively under the mode choice: the search box, the This area and
Everywhere choice while a name is searched in an area, the sport chips, the segments on the native map, and the list, beside the map on a
wide window and under it on a phone. The list's rows and messages SHALL read as the shared page's, and what it opens SHALL open on the
Search tab, keeping its query, map and list while the tab's root stays.

#### Scenario: Browsing on an iPad
- **WHEN** alice chooses Segments on an iPad
- **THEN** the list shows beside the native map, each run with its name, length, type and how far away it is

#### Scenario: Opening a segment
- **WHEN** alice taps a run in the native list
- **THEN** its page opens on the Search tab, and back returns to Segments mode with the map and list as she left them

#### Scenario: Nothing matches here
- **WHEN** alice searches "zzz" in This area and nothing matches
- **THEN** the native list says so and offers Search everywhere

### Requirement: The native browse map shares the selection
On iOS a tap on the browse map SHALL select the run nearest the tap within a finger's width, mark it in the list and bring it into view,
and a tap away from every run SHALL clear the choice. Each time the map stops it SHALL report what it shows, and each new place the
browse screen moves it to SHALL move it once.

#### Scenario: Selecting on the map
- **WHEN** alice taps on Ridge Trail's line on the native map
- **THEN** Ridge Trail is highlighted on the map and marked in the list, scrolled into view

## REMOVED Requirements

### Requirement: Native Segments mode shows the shared browse screen
**Reason**: Segment browse is native on iOS, so the native Search tab no longer embeds the shared screen.
**Migration**: Covered by "Segment browse is native on iOS".
