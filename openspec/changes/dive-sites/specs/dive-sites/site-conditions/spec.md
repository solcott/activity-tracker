# Spec Delta

## Purpose

What a dive site's conditions are and whose dives they come from: water temperature, visibility and depth aggregated over the dives a viewer may see.

## ADDED Requirements

### Requirement: Conditions come only from dives the viewer may see
`feed.getDiveSiteConditions` SHALL aggregate only over the site's visits the viewer may see whose Dive category the viewer may also see, and SHALL say how many dives it used. A site with none SHALL return no conditions.

#### Scenario: Dive category hidden
- **WHEN** a site's only visible visit is from a dive whose Dive category is Only me
- **THEN** a follower gets no conditions

### Requirement: Conditions summarize temperature, visibility and depth
Conditions SHALL give, over the dives used, the lowest, median and highest water temperature, the median visibility and the median and greatest maximum depth, each from the dives that record it, and the same median temperature and visibility for each calendar month with dives.

#### Scenario: Summer and winter
- **WHEN** a viewer may see eight dives at a site, four in August at 26 °C and four in February at 18 °C
- **THEN** the conditions give 18 °C lowest, 26 °C highest, and August and February medians of 26 °C and 18 °C

#### Scenario: Visibility not recorded
- **WHEN** none of the dives used records visibility
- **THEN** the conditions give no visibility

### Requirement: The site screen shows conditions
The site screen SHALL show the conditions in the viewer's units with the number of dives they come from, and a month-by-month view when more than one month has dives. Without conditions it SHALL show none.

#### Scenario: Imperial viewer
- **WHEN** a viewer using imperial units opens a site with conditions
- **THEN** temperatures show in °F and depths in feet, with the dive count
