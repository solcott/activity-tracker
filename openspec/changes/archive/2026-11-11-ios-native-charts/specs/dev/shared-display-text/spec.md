# Spec Delta

## ADDED Requirements

### Requirement: A chart's axis and labels are chosen once
What a line chart shows SHALL be chosen once in shared code and drawn by each UI: the y axis's range and round step, the x labels' places and text, each label's text in the viewer's units, the readout for each point and the description read to assistive technology.

#### Scenario: Distance labels
- **WHEN** a segment 1.2 mi long is charted for a viewer in imperial units
- **THEN** both UIs label its distance axis 0, 0.25, 0.5, 0.75 and 1 mi

### Requirement: Band runs are chosen once
Where a chart is tinted by bands, the stops SHALL be chosen once in shared code: a stop for the first point's band, then one halfway between each pair of points whose bands differ, a run shorter than three points joining the run before it.

#### Scenario: A short flat stretch
- **WHEN** two points of the flattest band sit between runs of a steeper band
- **THEN** both UIs tint them with the steeper band, with no stop between

### Requirement: The segment page reads the same in both UIs
What the segment page says SHALL be chosen once in shared code and resolved by each UI: the star's label, the length and type line, the gain, loss and grade line, the high, low and maximum grade line, the data credit and licence, the Mayor card's lines, and the efforts' heading and empty message.

#### Scenario: Imperial elevation
- **WHEN** a viewer in imperial units opens a 1.6 km segment with gain 84 m and loss 12 m
- **THEN** both UIs read its gain and loss in feet and an average grade of 4.5%

### Requirement: An effort row reads the same in both UIs
What an effort row says SHALL be chosen once in shared code for both the segment page and the activity page: its date or segment name, its time, PR, its rank's ordinal, why it isn't ranked and e-bike.

#### Scenario: Third place
- **WHEN** an effort carries rank 3
- **THEN** both UIs read "3rd" beside its time
