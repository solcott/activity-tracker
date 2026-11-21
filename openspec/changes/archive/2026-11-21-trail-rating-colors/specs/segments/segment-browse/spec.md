# Spec Delta

## MODIFIED Requirements

### Requirement: Lifts appear only on the map
The browse map SHALL draw ski lifts in the visible area in a muted style, distinct from runs and from every rating level's colour (`segments/trail-ratings`), so a resort's layout is recognisable. Lifts SHALL never be listed, selected or found by name.

#### Scenario: Resort area
- **WHEN** an athlete browses a ski resort with five pistes and two chairlifts
- **THEN** the map draws all seven lines, the lifts muted, and the list holds only the five pistes

#### Scenario: Pistes in their colours
- **WHEN** the same resort's pistes are novice, easy, intermediate, advanced and expert
- **THEN** the pistes are drawn white, green, blue, black and double black, and the lifts thin, dashed and grey
