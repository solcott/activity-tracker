## ADDED Requirements

### Requirement: Both UIs draw threshold history's one chart
Both UIs SHALL draw each threshold's chart, in full and as a collapsed group's sparkline, from that one shared chart through their shared chart drawing, with no chart of the page's own.

#### Scenario: The same chart in both UIs
- **WHEN** a threshold has three dated values, the newest current, and its group is expanded
- **THEN** both UIs draw its steps running on to today, a filled dot with its value over the current one and hollow dots with theirs over the others, and its lowest and highest values on the y axis

#### Scenario: A sparkline
- **WHEN** a group is collapsed
- **THEN** each threshold's tile shows its chart with no dots, labels or readout, and a touch on it does nothing
