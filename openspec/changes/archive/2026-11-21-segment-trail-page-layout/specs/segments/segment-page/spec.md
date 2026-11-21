# Spec Delta

## Purpose

The Segment page as a whole: what it shows and in what order, so a visitor reads the segment's name, its figures and who owns it before the details, on every platform alike.

## ADDED Requirements

### Requirement: The Segment page shows its parts in one order
The Segment page SHALL show, top to bottom: the map, the heading, the sport and direction choices, the figure row, the elevation profile, the boards row, Trail details, the names, the viewer's efforts, and a footer with the creator, the data credit and the status. A part with nothing to show SHALL be left out without a placeholder. The page's bar SHALL keep its title and Star.

#### Scenario: Active ride segment
- **WHEN** a viewer opens an active ride segment with a profile, a Mayor and trail details
- **THEN** the page shows the map, then the heading, the choices, the figures, the profile, the Leaderboard and Mayor cards, Trail details, the names and the efforts, in that order, with the creator, credit and status last

#### Scenario: Segment with nothing optional
- **WHEN** a viewer opens a proposed segment with no elevation, no profile, no trail details and no segment group
- **THEN** the page shows the map, the heading, the length in the figure row, the names, the efforts and the footer, with no empty cards

### Requirement: The heading names the segment and its trails
The heading SHALL show the segment's kind and status in a line, such as "Ride · Active" or "Ski lift · Proposed", then its current name, then its original name when a community name replaced it. It SHALL then show "on" and the name of each trail or road the segment lies on, each opening that trail's page. A trail whose name hasn't loaded or failed to SHALL be left out.

#### Scenario: Segment on one trail
- **WHEN** a viewer opens Hogback Climb, which lies on Hogback Ridge Trail
- **THEN** the heading reads "Ride · Active", "Hogback Climb" and "on Hogback Ridge Trail", and tapping the trail's name opens its Trail page

#### Scenario: Athlete's segment on no trail
- **WHEN** a viewer opens a segment that lies on no trail or road
- **THEN** the heading shows no "on" line

#### Scenario: Renamed segment
- **WHEN** a viewer opens a segment whose community name replaced its original name
- **THEN** the heading shows the current name and, under it, the original

### Requirement: The figure row
The page SHALL show the segment's length, gain, average grade and maximum grade side by side as figures, each with its label, in the viewer's units, and the loss with the gain. A figure the segment lacks SHALL be left out, and the rest SHALL share the row.

#### Scenario: All four figures
- **WHEN** a viewer using metric units opens a 2.4 km segment with gain 212 m, average grade 8% and maximum grade 14%
- **THEN** the row reads 2.4 km Distance, 212 m Gain, 8% Avg grade and 14% Max grade

#### Scenario: No elevation
- **WHEN** a viewer opens a segment whose view has no elevation
- **THEN** the row shows the length alone

### Requirement: The boards row
On an `active` segment that isn't a lift, the page SHALL show the Leaderboard card and the Mayor card side by side in one row, wrapping to one under the other when the page is too narrow for both. When only one of them has anything to show, it SHALL fill the row. On any other segment the row SHALL be absent.

#### Scenario: Both cards
- **WHEN** a viewer opens an active run whose leaderboard and Mayors board have both loaded
- **THEN** the Leaderboard and Mayor cards show side by side

#### Scenario: Mayor failed to load
- **WHEN** the Mayors board fails to load on an active segment and the leaderboard loads
- **THEN** the Leaderboard card shows alone across the row

#### Scenario: Lift
- **WHEN** a viewer opens a ski lift segment
- **THEN** the page shows no boards row

### Requirement: The native Segment page keeps the same order
On iOS the native Segment page SHALL show the same parts in the same order and words as the shared page, with the system's own bar, list and controls.

#### Scenario: Active segment on iOS
- **WHEN** a viewer opens an active ride segment with a Mayor on iOS
- **THEN** the native page shows the map, heading, choices, figures, profile, Leaderboard and Mayor cards, Trail details, names and efforts, in the shared page's order
