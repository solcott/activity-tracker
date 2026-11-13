## MODIFIED Requirements

### Requirement: Threshold history's text and charts are chosen in shared code
What threshold history says (a group's heading, help and summary, a threshold's label, values and change, a value's date and source, Show all, the remove label, the chart's description and its first date) and each threshold's chart (its steps, dots, labels and the run on to today) SHALL be chosen in shared code that both UIs ask. Both UIs SHALL draw each threshold's chart, in full and as a collapsed group's sparkline, from that one shared chart through their shared chart drawing, with no chart of the page's own.

#### Scenario: A faster pace
- **WHEN** a Run's threshold pace rose from 3.0 to 3.2 m/s since March 2 for an athlete who reads pace per mile
- **THEN** both UIs say it got faster by the same number of seconds per mile since Mar 2

#### Scenario: The same chart in both UIs
- **WHEN** a threshold has three dated values, the newest current, and its group is expanded
- **THEN** both UIs draw its steps running on to today, a filled dot with its value over the current one and hollow dots with theirs over the others, and its lowest and highest values on the y axis

#### Scenario: A sparkline
- **WHEN** a group is collapsed
- **THEN** each threshold's tile shows its chart with no dots, labels or readout, and a touch on it does nothing
