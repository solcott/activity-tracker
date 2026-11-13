## MODIFIED Requirements

### Requirement: A chart's axis and labels are chosen once
What a line chart shows SHALL be chosen once in shared code and drawn by each UI: the y axis's range and round step, the x labels' places and text, each label's text in the viewer's units, the readout for each point and the description read to assistive technology. This SHALL hold for the activity page's card charts (each line, the power curve and the gears) and the segment profile in every UI. No UI SHALL choose a time step, write a value or a duration itself, or write a stand-in label of its own while a label's text is loading.

#### Scenario: Distance labels
- **WHEN** a segment 1.2 mi long is charted for a viewer in imperial units
- **THEN** both UIs label its distance axis 0, 0.25, 0.5, 0.75 and 1 mi

#### Scenario: An hour's time labels
- **WHEN** a one-hour ride's heart rate chart is shown
- **THEN** every UI labels its time axis 0:00, 15:00, 30:00, 45:00 and 1:00:00

#### Scenario: A pace readout
- **WHEN** a viewer in metric units reads out a run's pace chart at 10:00, where the pace is 5:12 per kilometre
- **THEN** every UI reads "10:00 · 5:12 /km"

#### Scenario: The power curve's labels
- **WHEN** a ride's power curve has durations from 1 second to 1 hour
- **THEN** every UI labels only 5s, 1m, 5m, 20m and 1h along it

#### Scenario: Labels loading on the web
- **WHEN** a chart is drawn on the web before its strings have loaded
- **THEN** its axis labels are blank until they load, never bare numbers
