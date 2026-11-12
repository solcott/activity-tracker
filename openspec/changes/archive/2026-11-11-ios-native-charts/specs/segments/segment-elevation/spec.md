# Spec Delta

## ADDED Requirements

### Requirement: The native segment page draws the profile natively
On iOS the native segment page SHALL draw a segment's profile with the native chart: distance and elevation in the viewer's units on the same axis and labels the shared chart uses, tinted by the same five grade bands from the same runs, with the same readout while pressed or dragged, under the high, low and maximum grade line. Without a profile it SHALL draw no chart.

#### Scenario: Imperial profile
- **WHEN** a viewer using imperial units opens a segment with a profile on iOS
- **THEN** the native chart shows distance in miles and elevation in feet, at the labels the shared chart shows

#### Scenario: Elevation from efforts
- **WHEN** a viewer opens on iOS a segment whose elevation comes from efforts
- **THEN** the native page shows the gain, loss and grade line and no chart
