## MODIFIED Requirements

### Requirement: The harness chooses look, theme, units and size
The harness SHALL let a test or preview choose:
- the platform look (Android, desktop or web)
- light or dark theme
- the unit system
- the window width class (compact, medium or expanded)

The UI SHALL render with the choices made. The shared UI SHALL offer no other platform look: the iOS app draws its own screens, so
the harness SHALL NOT offer an iOS look of the shared UI.

#### Scenario: Dark theme
- **WHEN** a screen renders in the harness with dark theme chosen, on a machine set to light mode
- **THEN** it uses the app's dark colors

#### Scenario: Imperial units
- **WHEN** the activity page renders with imperial units chosen
- **THEN** distances show in miles and elevation in feet

#### Scenario: Expanded width
- **WHEN** a screen that lays out by window width renders at the expanded width
- **THEN** it shows its expanded layout

#### Scenario: No iOS look
- **WHEN** a test or preview chooses a platform look for a shared screen
- **THEN** the choices are Android, desktop and web, and each renders as that platform's app does
