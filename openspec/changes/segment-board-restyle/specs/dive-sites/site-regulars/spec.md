# Spec Delta

## ADDED Requirements

### Requirement: The Regulars screen pins the viewer's place
While the viewer is on a site's Regulars board but not among the entries loaded, their entry SHALL show in a bar fixed to the bottom of the screen under "Your place", which stays while the list scrolls, and the list SHALL leave room so the bar never covers its last row. The bar SHALL add no gap line. A viewer not on the board SHALL see no bar.

#### Scenario: Far down the Regulars
- **WHEN** a viewer ranked 40th opens a site's Regulars
- **THEN** a bar at the bottom shows their row, with no line about the leader

#### Scenario: Among the first page
- **WHEN** the viewer is ranked 3rd
- **THEN** their row in the list is marked as theirs and no bar shows
