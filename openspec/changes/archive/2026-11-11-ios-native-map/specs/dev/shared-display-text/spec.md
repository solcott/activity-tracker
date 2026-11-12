# Spec Delta

## ADDED Requirements

### Requirement: A route map shows the same marks in every UI
What a route map marks SHALL be decided once in shared code and drawn by each UI: the line's pieces between its breaks, smoothed, and where the start, end, stop and resume markers go, with the start and end at the ends others see when given and none where hidden. The live maps, their line previews and the feed's map images SHALL ask it.

#### Scenario: Owner's markers
- **WHEN** the owner's map draws their untrimmed track with the ends others see given
- **THEN** every UI marks the start and end at those ends, not at the track's own

### Requirement: A route map frames the same in every UI
The camera that frames a route north-up in a map of a given size, with a margin, any space a caller draws over the map's top kept clear, and never closer than the closest framing, SHALL be decided once in shared code, and the feed's map images and the native iOS map SHALL ask it.

#### Scenario: Same frame
- **WHEN** a route is framed in a 360 by 240 point map by the native iOS map and by a feed map image of that size
- **THEN** both centre it on the same point at the same zoom

### Requirement: The dive site page reads the same in both UIs
What the dive site page says SHALL be chosen once in shared code and resolved by each UI: the radius in the viewer's units, the creator, the credit and licence, the status label and help with whether Open the original shows, the Regulars card, the conditions' lines in the viewer's units, and the dives' heading and empty message.

#### Scenario: Imperial conditions
- **WHEN** a viewer in imperial units opens a site whose median water temperature is 27 °C
- **THEN** both UIs read "Water 80.6 °F" with the range in °F

### Requirement: A place's names read the same in both UIs
What a place's names section says SHALL be chosen once in shared code for every place with names: "originally", who proposed a name and its totals, the vote labels and what a vote tap sends, why voting is unavailable, and each history entry with its date and proposer.

#### Scenario: Totals
- **WHEN** a proposal has 1,200 votes for and 3 against
- **THEN** both UIs read its totals with the count grouped
