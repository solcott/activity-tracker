## ADDED Requirements

### Requirement: Stats and units are chosen once
An activity's zone rows (each zone's title, its share of the time as a percent and the sentence a screen reader says for it), the avg/max
table's Min column and its fallback for a missing value, the split of a figure's value into number and unit runs, and the comment counts
SHALL be chosen by shared functions both UIs ask. A figure's unit SHALL NOT wrap inside itself in either UI.

#### Scenario: A sliver of a zone
- **WHEN** an activity spent 0.3% of its time in zone 5
- **THEN** both UIs show "<1%" for it, and a zone with 58.4% shows "58%"

#### Scenario: A zone row read aloud
- **WHEN** a screen reader reaches zone 2, Endurance, 120–145 bpm, 35:00, 58%
- **THEN** both UIs read "Z2 Endurance, 120–145 bpm: 35:00, 58%"

#### Scenario: A swim pace in a narrow column
- **WHEN** a hero figure reads "1:59 /100 yd" in a column too narrow for it
- **THEN** both UIs break it only between "1:59" and "/100 yd", with the number larger than its unit

#### Scenario: More replies
- **WHEN** a thread has 1234 more replies
- **THEN** both UIs read "View 1,234 more replies"
