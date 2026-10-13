# Spec Delta

## ADDED Requirements

### Requirement: Sub-sports filed elsewhere are top-level rows in the type picker
A sub-sport listed in a category other than its sport's SHALL show as a top-level row in the type picker and SHALL NOT be hidden by collapsing.

#### Scenario: Indoor Row under Gym & Fitness
- **WHEN** the athlete opens the type picker
- **THEN** Indoor Row is visible and selectable under Gym & Fitness without expanding anything

## MODIFIED Requirements

### Requirement: Activity type multi-select
The athlete SHALL be able to select any number of activity types from the sport catalog: whole categories, sports, or individual sport and sub-sport pairs. The feed SHALL show activities matching any selected type. An empty selection SHALL mean all types.

#### Scenario: Select a sport
- **WHEN** the athlete selects Run
- **THEN** the feed shows runs of every sub-sport (road, trail, treadmill and so on)

#### Scenario: Select one sub-sport
- **WHEN** the athlete selects only Trail Run
- **THEN** the feed shows only trail runs

#### Scenario: Select a category
- **WHEN** the athlete selects a whole category such as Cycling
- **THEN** every type listed in that category is selected, each sport in it whole, and the feed shows activities of any of them

#### Scenario: Category covers its sports' sub-sports filed elsewhere
- **WHEN** the athlete selects Water Sports
- **THEN** Row is selected whole, so rows of every sub-sport match, and Indoor Row under Gym & Fitness shows as checked

#### Scenario: Full selection collapses
- **WHEN** the athlete individually selects every sub-sport of a sport, including any filed in another category
- **THEN** the selection is treated as the sport itself, so its generic form also matches

### Requirement: Sports collapse in the type picker
In the type picker, each sport that has sub-sports listed under it SHALL be collapsible, in every category,, with an expand control separate from selecting the sport. Each such sport SHALL start collapsed every time the picker opens. A collapsed sport SHALL hide its sub-sports, and expanding it SHALL show them beneath it. Collapsing SHALL NOT change the draft selection.

#### Scenario: Collapsed when opened
- **WHEN** the athlete opens the type picker
- **THEN** every collapsible sport is collapsed and no sub-sport listed under its sport is shown

#### Scenario: Expand a sport
- **WHEN** the athlete expands Ride
- **THEN** Ride's sub-sports appear beneath it with their selection state, and the other sports stay collapsed

#### Scenario: Expanding does not select
- **WHEN** the athlete expands or collapses a sport
- **THEN** the draft selection is unchanged

#### Scenario: Reopened collapsed
- **WHEN** the athlete expands a sport, applies or dismisses the picker, and opens it again
- **THEN** every sport is collapsed again

### Requirement: Partial selection uses a mixed checkbox
A category or sport with some, but not all, of its types selected in the draft SHALL show its checkbox in the mixed (indeterminate) state, collapsed or expanded, as Material's and Apple's guidelines draw a partly selected parent. No separate text marker SHALL be shown.

#### Scenario: One sub-sport selected then collapsed
- **WHEN** the athlete expands Ride, selects Gravel Ride, and collapses Ride
- **THEN** the Ride row's checkbox and the Cycling category's checkbox both show the mixed state

#### Scenario: Whole sport selected
- **WHEN** Ride itself is selected and collapsed
- **THEN** the Ride row's checkbox is checked

#### Scenario: Sub-sport filed elsewhere selected
- **WHEN** the athlete selects only Indoor Row under Gym & Fitness
- **THEN** the Gym & Fitness category's checkbox shows the mixed state

## REMOVED Requirements

### Requirement: Shortcut types always visible in the picker
**Reason**: Shortcuts listed a type in a second category. Each type is now listed once, and a sub-sport may be filed under another category instead.
**Migration**: Replaced by "Sub-sports filed elsewhere are top-level rows in the type picker".
