# Spec Delta

## MODIFIED Requirements

### Requirement: The Trail page draws its path and segments
Where the segment page draws a map, the Trail page SHALL draw the path's line framed whole, muted, with its listed segments' lines over it, each in its rating level's colour for the page's sport group (`segments/trail-ratings`), and the legend of the levels it draws.

#### Scenario: Opening Ann's
- **WHEN** an athlete opens Ann's with three segment groups
- **THEN** the map shows all of Ann's line with the three groups' lines drawn over it

#### Scenario: Rated stretches
- **WHEN** an athlete opens Ann's from browse with Ride chosen, and its two stretches are IMBA Easy and Intermediate
- **THEN** the stretches are drawn green and blue over the muted path, and the legend lists Easy and Intermediate
