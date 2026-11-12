# Spec Delta

## ADDED Requirements

### Requirement: The import screen's text is chosen in shared code
What the import screen says that follows a rule (default titles, reading's progress, threshold changes, duplicate warnings and banners, a batch row's lines, the batch's summary, filters, selected count and Save, the comparison, the saved and after-import panels, time zone names and their search, and the new devices' lines and choices) SHALL be chosen in shared code that both UIs ask, as SHALL whether Save is offered and the default titles saving sends.

#### Scenario: An untitled morning ride
- **WHEN** a batch row of a ride started at 07:02 has no title
- **THEN** both UIs show it as Morning Ride and save it with that title

#### Scenario: Searching zones
- **WHEN** the athlete types "utc+8" in the zone search
- **THEN** both UIs offer the zones eight hours ahead of UTC
