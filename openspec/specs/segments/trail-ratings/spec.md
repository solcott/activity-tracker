# segments/trail-ratings Specification

## Purpose

Shows how hard a trail or segment is at a glance: one rating level, read from its trail attributes for a sport group, drawn on maps and list rows in the colours trail signs use.

## Requirements

### Requirement: A rating level comes from the sport group's scale
A segment or trail with trail attributes (`segments/trail-attributes`) SHALL have one rating level for a sport group: Easiest, Easy, Intermediate, Difficult or Extreme, or none. The sport group SHALL pick the scale: ride the IMBA rating, else the MTB scale; run and hike the SAC scale; alpine the piste difficulty. Other sport groups, lifts and anything without that scale's value SHALL be unrated.

#### Scenario: IMBA rating on a ride segment
- **WHEN** a ride segment's trail has IMBA rating 3 and MTB scale 2
- **THEN** its level is Difficult, from the IMBA rating

#### Scenario: MTB scale alone
- **WHEN** a ride segment's trail has MTB scale 5 and no IMBA rating
- **THEN** its level is Extreme

#### Scenario: Run segment on a rated ride trail
- **WHEN** a run segment's trail has an IMBA rating but no SAC scale
- **THEN** it is unrated

#### Scenario: Piste
- **WHEN** an alpine segment's piste difficulty is advanced
- **THEN** its level is Difficult

#### Scenario: Athlete's own segment
- **WHEN** a segment has no trail attributes
- **THEN** it is unrated

### Requirement: Each scale maps onto the five levels
The scales SHALL map as follows. IMBA 0 to 4: Easiest, Easy, Intermediate, Difficult, Extreme. MTB scale 0: Easiest, 1: Easy, 2: Intermediate, 3: Difficult, 4 to 6: Extreme. SAC hiking: Easy, mountain hiking: Intermediate, demanding mountain hiking: Difficult, any alpine hiking: Extreme. Piste novice: Easiest, easy: Easy, intermediate: Intermediate, advanced: Difficult, expert, freeride and extreme: Extreme.

#### Scenario: Demanding mountain hike
- **WHEN** a hike segment's SAC scale is demanding mountain hiking
- **THEN** its level is Difficult

#### Scenario: Expert piste
- **WHEN** an alpine segment's piste difficulty is freeride
- **THEN** its level is Extreme

### Requirement: A trail with no sport chosen takes its first known scale
A trail shown with no sport group chosen (browse with no sport chip, the Trail page opened without one) SHALL take its level from the first scale it has, in the order IMBA, MTB scale, SAC scale, piste difficulty. With a sport group chosen it SHALL use that group's scale.

#### Scenario: Unfiltered trail
- **WHEN** browse has no sport chosen and a trail has MTB scale 1 and SAC scale demanding mountain hiking
- **THEN** its line and row are Easy

#### Scenario: Narrowed to hike
- **WHEN** the same trail is shown with Hike chosen
- **THEN** its line and row are Difficult

### Requirement: Maps draw runs in their level's colour
The browse map and the Trail page map SHALL draw each run's line in its level's colour: Easiest white, Easy green, Intermediate blue, Difficult black, Extreme black drawn as a double line. Every run SHALL have a casing that contrasts with its colour, so white and black lines read on light and dark maps. Unrated runs SHALL be drawn in a neutral grey.

#### Scenario: Mixed trail network
- **WHEN** the browse map shows a green, a blue and an unrated run
- **THEN** they are drawn green, blue and grey, each on its casing

#### Scenario: Extreme run without colour
- **WHEN** an athlete who can't tell black from dark grey looks at an Extreme run beside an unrated one
- **THEN** the Extreme run is told apart by its double line

### Requirement: Highlight and lifts stay apart from the levels
A selected or hovered run SHALL be drawn in the highlight colour over its casing, above every other line, whatever its level. Lifts SHALL stay thin, dashed and muted, in no level's colour.

#### Scenario: Selecting a black run
- **WHEN** an athlete selects a Difficult run's row
- **THEN** its line is drawn in the highlight colour over the others, and turns black again once deselected

### Requirement: A map's legend lists the levels it draws
The browse map and the Trail page map SHALL show a small legend naming each level drawn in the visible lines, in order from Easiest to Extreme, then Unrated, each beside its line sample. A map drawing no rated run SHALL show no legend.

#### Scenario: Legend for a valley
- **WHEN** the browse map draws Easy, Difficult and unrated runs
- **THEN** its legend lists Easy, Difficult and Unrated, in that order

#### Scenario: No ratings in view
- **WHEN** every run on the map is unrated
- **THEN** no legend shows

### Requirement: Rated rows show their level
A browse row and a Trail page row SHALL show a swatch of their line's level before the name, and a screen reader SHALL read the level's name with the row. An unrated row SHALL show no swatch. On web, which has no browse map, the rows SHALL show it too.

#### Scenario: Browse on web
- **WHEN** an athlete browses by name on the web and Ridge Trail is Intermediate
- **THEN** its row shows a blue swatch read as "Intermediate"

#### Scenario: Unrated row
- **WHEN** a row's segment group is unrated
- **THEN** it shows no swatch

### Requirement: iOS shows the same levels natively
On iOS the native browse map and Trail page map SHALL draw each level, the casing, the highlight and the legend as the shared maps do, and the native rows SHALL show the same swatches, from the same rule.

#### Scenario: Native Trail page
- **WHEN** alice opens Ann's on her iPhone and its stretches are Easy and Intermediate
- **THEN** the native map draws them green and blue with a legend listing Easy and Intermediate, and their rows show the same swatches
