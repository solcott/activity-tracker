# Spec Delta

## MODIFIED Requirements

### Requirement: The segment screen shows elevation
When a segment's view has elevation, the segment screen SHALL show its gain and its average grade (net height change over its length, as a percentage) as figures in the figure row beside its length (`segments/segment-page`), and its loss with the gain figure, in the viewer's units. When the view has none, the screen SHALL show none of them and no placeholder.

#### Scenario: Segment with elevation
- **WHEN** a viewer using imperial units opens a 1.6 km segment with gain 84 m and loss 12 m
- **THEN** the figure row shows the gain in feet with the loss in feet, and an average grade of 4.5%

#### Scenario: Segment without elevation
- **WHEN** a viewer opens a segment whose view has no elevation
- **THEN** the figure row shows its length alone, with no gain, loss or grade

### Requirement: The segment screen shows the elevation profile
When the segment query returns a profile, the segment screen SHALL draw it in a card under the figure row. The chart's horizontal axis is distance along the segment and its vertical axis is elevation, both in the viewer's units. The area under the line is tinted by local grade. Pressing or dragging across it SHALL show the distance, elevation and grade at that point. Without a profile, the screen SHALL draw no chart, no card and no placeholder.

#### Scenario: Segment with a profile
- **WHEN** a viewer using imperial units opens a segment whose query returned a profile
- **THEN** the screen shows the profile in a card, with distance in miles and elevation in feet

#### Scenario: Scrubbing
- **WHEN** the viewer drags across the profile
- **THEN** a readout shows the distance, elevation and grade at the point under their finger

#### Scenario: Segment without a profile
- **WHEN** a viewer opens a segment whose elevation comes from efforts
- **THEN** the screen shows the gain, loss and grade in the figure row and no profile card

#### Scenario: Ski lift
- **WHEN** a viewer opens a ski lift segment with a profile
- **THEN** the screen shows its profile like any other segment's

### Requirement: The segment screen shows high point, low point and maximum grade
When the segment query returns a profile, the segment screen SHALL show the segment's high point and low point, from the profile's heights, in the profile card's heading, and its maximum grade, when the profile carries one, as a figure in the figure row. Elevation SHALL be in the viewer's units and grade a whole percent. Without a profile, the screen SHALL show none of them.

#### Scenario: Segment with a profile and maximum grade
- **WHEN** a viewer using imperial units opens a segment whose profile runs from 1,616.0 m to 1,835.3 m and has a maximum grade of 162
- **THEN** the profile card's heading shows a high point of 6,021 ft and a low point of 5,302 ft, and the figure row shows a maximum grade of 16%

#### Scenario: Profile without a maximum grade
- **WHEN** a viewer opens a segment whose profile carries no maximum grade
- **THEN** the profile card shows the high and low points, and the figure row has no maximum grade

#### Scenario: Segment without a profile
- **WHEN** a viewer opens a segment whose elevation comes from efforts
- **THEN** the screen shows no high point, low point or maximum grade

### Requirement: The native segment page draws the profile natively
On iOS the native segment page SHALL draw a segment's profile with the native chart, in a card headed by the high and low points: distance and elevation in the viewer's units on the same axis and labels the shared chart uses, tinted by the same five grade bands from the same runs, with the same readout while pressed or dragged. Without a profile it SHALL draw no chart and no card.

#### Scenario: Imperial profile
- **WHEN** a viewer using imperial units opens a segment with a profile on iOS
- **THEN** the native chart shows distance in miles and elevation in feet, at the labels the shared chart shows

#### Scenario: Elevation from efforts
- **WHEN** a viewer opens on iOS a segment whose elevation comes from efforts
- **THEN** the native page shows the gain, loss and grade in the figure row and no profile card
