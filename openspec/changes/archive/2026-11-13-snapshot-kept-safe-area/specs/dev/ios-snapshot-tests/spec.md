## ADDED Requirements

### Requirement: Snapshots draw with a fixed safe area
Every snapshot SHALL be drawn with a safe area fixed before it draws, the same wherever the snapshot library places the view, so that no
draw depends on when the system passes a changed safe area on. A navigation stack SHALL keep the key window's safe area, so that its bar
sits under the status bar and the content below it is laid out for that, as on a device; a component's own content SHALL take none, so
that it is drawn whole in its frame.

#### Scenario: A list under a bar
- **WHEN** a screen with a navigation bar and a list is snapshotted, alone or in a full run
- **THEN** its bar is drawn under the status bar and its list right below the bar, the same every run

#### Scenario: A component in a short frame
- **WHEN** a component with no navigation stack is snapshotted in a frame little taller than itself
- **THEN** it is drawn whole, placed in the frame as it would be with no status bar above it
