# Spec Delta

## ADDED Requirements

### Requirement: A native line chart draws the shared chart
On iOS a native page SHALL draw a line chart from the chart the shared UI draws: the same points over the same x range, the line filled beneath, the y axis's range and gridlines at the shared steps with their labels, the x labels at the shared places, and no line or fill across a gap in the samples.

#### Scenario: An elevation profile
- **WHEN** a native page draws a segment's profile whose shared axis runs from 1,400 to 1,600 m in steps of 50
- **THEN** the chart's gridlines and labels are at 1,400, 1,450, 1,500, 1,550 and 1,600 m, with the shared distance labels along the bottom

#### Scenario: A gap
- **WHEN** a native chart's points include one that follows a gap in the samples
- **THEN** the line and its fill break there

### Requirement: A native chart can be tinted by bands
A native line chart given band stops SHALL tint its line and the area beneath it by band across the chart, each band's colour held to the next stop and switching sharply there, in colours that stay distinct from the surface in light and dark appearances.

#### Scenario: A climb that steepens
- **WHEN** a native chart's stops put the flattest band on its first half and the steepest on its second
- **THEN** the left half is drawn in the flattest band's colour and the right half in the steepest's

### Requirement: A native chart reads out the point under the finger
Tapping or dragging across a native chart SHALL mark the nearest point and show the shared readout for it over the chart, staying where the finger left it, as the shared chart does. A drag that is mostly vertical SHALL scroll the page instead and hide the readout.

#### Scenario: Scrubbing a profile
- **WHEN** a viewer drags sideways across a segment's native profile
- **THEN** a readout over the chart shows the distance, elevation and grade at the nearest point, following the finger

#### Scenario: Scrolling past
- **WHEN** a viewer drags up from inside the chart
- **THEN** the page scrolls and no readout shows

### Requirement: A native chart reads as one element to assistive technology
A native chart SHALL be read by VoiceOver as one element with the shared description.

#### Scenario: VoiceOver on a profile
- **WHEN** VoiceOver reaches a segment's native profile
- **THEN** it reads the shared description of its length, start and end elevation
