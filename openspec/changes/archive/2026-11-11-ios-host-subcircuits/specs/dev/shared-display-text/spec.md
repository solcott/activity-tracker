## ADDED Requirements

### Requirement: The feed filter bar's chips read the same in both UIs
What the feed filter bar's chips say SHALL be chosen once in shared code and resolved by each UI: the types chip names all types when none is chosen, the one type when one is, and the first type with how many more when several are (sorted by sport, then sub-sport); the date chip names any time, the last N days, this year, or a custom range from its first day to its last.

#### Scenario: Several types
- **WHEN** the filter holds a ride and two runs' sub-sports
- **THEN** both UIs' types chip reads the first of them by sport with "+2"

#### Scenario: A custom range
- **WHEN** the filter's range is a custom range from 1 March to 15 March 2026
- **THEN** both UIs' date chip names that range with its two days
