## MODIFIED Requirements

### Requirement: The Trail page draws its path and segments
Where the segment page draws a map, the Trail page SHALL draw the path's line framed whole, muted, with its listed segments' lines over it, each in its rating level's colour for the page's sport group (`segments/trail-ratings`), and the legend of the levels it draws. A segment whose conditions lead with Closed (`segments/trail-reports`) SHALL also be marked on the map with a label reading Closed, in the error colour with its icon, so it is told apart without colour.

#### Scenario: Opening Ann's
- **WHEN** an athlete opens Ann's with three segment groups
- **THEN** the map shows all of Ann's line with the three groups' lines drawn over it

#### Scenario: Rated stretches
- **WHEN** an athlete opens Ann's from browse with Ride chosen, and its two stretches are IMBA Easy and Intermediate
- **THEN** the stretches are drawn green and blue over the muted path, and the legend lists Easy and Intermediate

#### Scenario: Closed stretch
- **WHEN** an athlete opens Kingdom Loop, whose Ridge Run segment leads with Closed
- **THEN** Ridge Run's line keeps its rating colour and carries a "Closed" label on the map

## ADDED Requirements

### Requirement: The Trail page shows its conditions
The Trail page SHALL show a Conditions card at the head of its list when any listed segment has current conditions or the trail has photos. It SHALL say how many of its segments have reports in the last 14 days, list each such segment with its summary, riders and age, Closed ones first, each opening that segment, and show a strip of the trail's newest photos with the total. iOS SHALL draw it natively.

#### Scenario: Kingdom Loop
- **WHEN** a viewer opens Kingdom Loop, where 3 of its 8 segments have current reports and Ridge Run's leads with Closed
- **THEN** the card reads "Reports on 3 of 8 segments, last 14 days" and lists Ridge Run first with Closed

#### Scenario: Quiet trail
- **WHEN** no segment of a trail has reports or photos
- **THEN** the Trail page shows no Conditions card

#### Scenario: Segment row marks Closed
- **WHEN** Ridge Run leads with Closed
- **THEN** its row in the segment list reads "Closed · 1 day ago" under its name
