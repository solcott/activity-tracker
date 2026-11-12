# Spec Delta

## ADDED Requirements

### Requirement: A native chart draws a backdrop
A native line chart with a backdrop SHALL draw it as a faint area on its own scale behind the line, filling about the lower three fifths of the chart, as the shared chart draws the elevation behind an effort line.

#### Scenario: Heart rate over a climb
- **WHEN** a native page draws a heart rate chart of a ride with a climb
- **THEN** the climb shows as a faint area behind the heart rate line

### Requirement: A native chart draws more lines
A native line chart with more lines SHALL draw each on the same scale, unfilled, in its own colour, breaking at its own gaps.

#### Scenario: Two tanks
- **WHEN** a native page draws a dive's tank pressure with two tanks
- **THEN** both tanks' lines show on one chart in different colours

### Requirement: A native chart marks moments
A native line chart with marks SHALL draw each as a vertical line at its moment with its label, a warning's in the error colour.

#### Scenario: A dive warning
- **WHEN** a native page draws a dive's depth chart with an ascent rate warning at 40:00
- **THEN** a red line at 40:00 is labelled with the warning's name

### Requirement: A native chart can fill above its line
A native line chart that fills above SHALL fill from its line to the top of the chart instead of to the bottom, as a dive's depth fills up to the surface.

#### Scenario: Depth
- **WHEN** a native page draws a dive's depth
- **THEN** the water between the surface and the depth line is filled

### Requirement: The media gallery is native on iOS
On iOS an activity's media gallery SHALL be full screen on black without the tabs: swiped a photo at a time, a tap zooming the photo for pinch, double tap and pan and a tap again letting it swipe, with Close, a counter over more than one photo, Report on someone else's photo, a labelled photo blurred until revealed, and the owner's taken-down photos as placeholders.

#### Scenario: Swiping
- **WHEN** an athlete on iOS opens the second of three photos and swipes left
- **THEN** the third photo shows and the counter reads 3 of 3

#### Scenario: Zooming
- **WHEN** they tap a photo
- **THEN** it can be pinched and panned, and swiping doesn't change photo until they tap again
