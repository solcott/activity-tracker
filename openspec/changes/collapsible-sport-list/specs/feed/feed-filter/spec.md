# Spec Delta

## ADDED Requirements

### Requirement: Sports collapse in the type picker
In the type picker, each sport that has sub-sports SHALL be collapsible, with an expand control separate from selecting the sport. Each such sport SHALL start collapsed every time the picker opens. A collapsed sport SHALL hide its sub-sports, and expanding it SHALL show them beneath it. Collapsing SHALL NOT change the draft selection.

#### Scenario: Collapsed when opened
- **WHEN** the athlete opens the type picker
- **THEN** every sport with sub-sports is collapsed and no sub-sport listed under its sport is shown

#### Scenario: Expand a sport
- **WHEN** the athlete expands Running
- **THEN** Running's sub-sports appear beneath it with their selection state, and the other sports stay collapsed

#### Scenario: Expanding does not select
- **WHEN** the athlete expands or collapses a sport
- **THEN** the draft selection is unchanged

#### Scenario: Reopened collapsed
- **WHEN** the athlete expands a sport, applies or dismisses the picker, and opens it again
- **THEN** every sport is collapsed again

### Requirement: Partial selection uses a mixed checkbox
A category or sport with some, but not all, of its types selected in the draft SHALL show its checkbox in the mixed (indeterminate) state, collapsed or expanded, as Material's and Apple's guidelines draw a partly selected parent. No separate text marker SHALL be shown.

#### Scenario: One sub-sport selected then collapsed
- **WHEN** the athlete expands Running, selects Trail Run, and collapses Running
- **THEN** the Run row's checkbox and the Running category's checkbox both show the mixed state

#### Scenario: Whole sport selected
- **WHEN** Running itself is selected and collapsed
- **THEN** the Run row's checkbox is checked

### Requirement: Shortcut types always visible in the picker
A sub-sport listed in a category without its own sport (a shortcut such as Indoor Ride under Gym) SHALL show as a top-level row in the type picker and SHALL NOT be hidden by collapsing.

#### Scenario: Shortcut under another category
- **WHEN** the athlete opens the type picker
- **THEN** Indoor Ride is visible and selectable under Gym without expanding anything
