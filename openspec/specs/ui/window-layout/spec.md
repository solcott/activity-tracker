# ui/window-layout Specification

## Purpose
How the size of a screen's window picks its layout, on every platform alike: by its width, and never the wide layout for a window too short to hold it.

## Requirements

### Requirement: A window's width picks a screen's layout
A screen that lays out differently by size SHALL choose by the width its window gives it, the same way on every platform: compact under 600dp, medium from 600dp to under 840dp, and wide from 840dp. The kind of device SHALL NOT decide it.

#### Scenario: A phone and a narrow desktop window
- **WHEN** the activity page is shown on a 412dp-wide phone and in a desktop window resized to 500dp wide
- **THEN** both show the compact layout

#### Scenario: A tablet upright
- **WHEN** the activity page is shown 800dp wide and 1,200dp tall
- **THEN** it shows the medium layout

#### Scenario: A wide window
- **WHEN** the activity page is shown 1,280dp wide and 800dp tall
- **THEN** it shows the wide layout, with its side pane

### Requirement: A short window is never laid out as a wide one
A window less than 500dp tall SHALL NOT get a screen's wide layout, however wide it is: from 600dp of width it SHALL get the medium layout. This SHALL hold for every screen with a wide layout, among them the activity page, the import preview and Settings beside its page, and SHALL take precedence over a requirement that gives a wide layout from 840dp.

#### Scenario: An activity on a phone on its side
- **WHEN** the activity page is shown on a phone on its side, 874dp wide and 402dp tall
- **THEN** it shows the medium layout: hero and map side by side and cards two to a row, with no side pane

#### Scenario: Settings on an Android phone on its side
- **WHEN** an athlete on an Android phone on its side, 915dp wide and 412dp tall, opens Settings and taps Privacy zones
- **THEN** Privacy zones replaces the list, and back returns to the list

#### Scenario: Importing on a phone on its side
- **WHEN** an athlete previews a ride for import on a phone on its side
- **THEN** the save panel follows the preview, as on a medium window, and isn't beside it

#### Scenario: A tablet on its side
- **WHEN** the activity page is shown on a tablet on its side, 1,180dp wide and 820dp tall
- **THEN** it shows the wide layout

#### Scenario: A short desktop window
- **WHEN** a desktop window showing the activity page's wide layout is resized to 1,000dp wide and 450dp tall
- **THEN** it shows the medium layout, and the wide one again when it is made taller

#### Scenario: Just tall enough
- **WHEN** a window is 840dp wide and 500dp tall
- **THEN** a screen shows its wide layout
