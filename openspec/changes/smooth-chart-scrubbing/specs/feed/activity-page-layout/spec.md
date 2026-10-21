# Spec Delta

## ADDED Requirements

### Requirement: Reading a chart's values
Hovering, tapping or dragging across a chart over time on the activity page or the import preview SHALL show a readout for the main line's point nearest the pointer: a guideline down the chart, a dot on the line, and the point's time and value. The readout SHALL stay after a tap or a drag ends. It SHALL hide when the mouse leaves the chart, or when a drag starts mostly vertically, which scrolls the page.

#### Scenario: Drag across heart rate
- **WHEN** a viewer drags a finger sideways across a ride's heart rate chart and stops 1:20:31 into the ride
- **THEN** the readout follows the finger and, once the finger stops, shows "1:20:31 · 114 bpm · 5,026 ft" (time, heart rate and the elevation there) beside a dot on the heart rate line at that time

#### Scenario: Readout stays after release
- **WHEN** the viewer lifts the finger after a tap or a sideways drag
- **THEN** the readout stays where it was until the next touch

#### Scenario: Vertical drag scrolls the page
- **WHEN** a viewer puts a finger on a chart and drags mostly upward
- **THEN** the page scrolls and no readout is shown

#### Scenario: Hover on desktop
- **WHEN** a mouse moves over a chart on desktop or the web and then leaves it
- **THEN** the readout follows the pointer while it is over the chart, and hides when it leaves

#### Scenario: More lines on one chart
- **WHEN** a viewer drags across a dive's tank pressure chart with two tanks
- **THEN** the readout gives the first tank's value, and each tank line with a sample at that time gets a dot

### Requirement: Scrubbing keeps up with the display
Moving a chart's readout SHALL redraw only the readout, not the chart beneath it, so that dragging across a chart keeps up with the display's refresh rate on current phones.

#### Scenario: Drag on a 120 Hz phone
- **WHEN** a viewer on a Pixel 9 Pro XL (release build, 120 Hz) drags across a ride's heart rate chart for 4 seconds
- **THEN** no more than 5% of the frames drawn during the drag are janky

#### Scenario: Chart unchanged while scrubbing
- **WHEN** a viewer drags the readout across a chart with the elevation behind it
- **THEN** the line, its fill, the elevation, the average line and the axes do not move or flicker
