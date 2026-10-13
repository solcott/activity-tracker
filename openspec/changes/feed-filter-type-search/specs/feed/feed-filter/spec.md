# Spec Delta

## ADDED Requirements

### Requirement: Type picker search
The type picker SHALL offer a search field. A type SHALL match a query when its label, its sport's label, or its sport or sub-sport token contains the query, ignoring case. The query SHALL be empty each time the picker opens.

#### Scenario: Search by sub-sport label
- **WHEN** the athlete types "gravel" into the search
- **THEN** Gravel Ride is listed and Road Ride is not

#### Scenario: Search by sport label
- **WHEN** the athlete types "row"
- **THEN** Row is listed under Water Sports, and Indoor Row and Virtual Row under Gym & Fitness

#### Scenario: Query resets
- **WHEN** the athlete searches, dismisses or applies the picker, and opens it again
- **THEN** the search field is empty and the full list is shown

### Requirement: Search shows every match in the type picker
While a query is entered, the type picker SHALL show every matching type with its selection state, regardless of which sports are collapsed. It SHALL hide categories with no matching types, hide the recent and common types, and say so when nothing matches.

#### Scenario: Matching sub-sport of a collapsed sport
- **WHEN** the athlete types "trail"
- **THEN** Trail Run is shown without expanding Run

#### Scenario: No matches
- **WHEN** the athlete types a query that no type matches
- **THEN** no categories are listed and the picker says no sports match

#### Scenario: Selection survives clearing the query
- **WHEN** the athlete searches "gravel", selects Gravel Ride, and clears the query
- **THEN** the full list returns with Gravel Ride still selected in the draft

### Requirement: Category selection is unavailable during search
While a query is entered, category headers in the type picker SHALL show no checkbox and SHALL NOT change the draft when tapped. Sport and type rows SHALL stay selectable, and a sport row SHALL still select the whole sport.

#### Scenario: Tapping a category while searching
- **WHEN** the athlete types "row" and taps the Water Sports header
- **THEN** the draft is unchanged, and Kayak and the other hidden Water Sports types stay unselected

#### Scenario: Category checkboxes return
- **WHEN** the athlete clears the query
- **THEN** every category header shows its checkbox again, reflecting the draft
