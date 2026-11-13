## ADDED Requirements

### Requirement: A suite run alone compares the same every run
A suite of snapshot tests run on its own on the recorded simulator, with nothing changed, SHALL pass every run, including its wide (iPad)
samples. A sample that drew differently from run to run SHALL be made to draw the same, not passed by a looser tolerance or dropped.

#### Scenario: Import alone, five times
- **WHEN** the import screen's snapshot suite runs alone five times in a row on the recorded simulator
- **THEN** every run passes, its iPad page included
