# Spec Delta

## ADDED Requirements

### Requirement: A trail or road row says which sport groups go one way
A trail or road's row SHALL say, after its sport groups, which of them go one way only and which way, as a path view gives it (`segments/segment-paths`), in the wording segment group rows use: once after the sports when every listed sport group goes the same one way, otherwise after each one-way sport group, with the others listed first and plain. When the list is narrowed to one sport group, the row SHALL say only that group's way.

#### Scenario: Downhill-only ride trail
- **WHEN** the name search "rush" finds the Rush trail, whose ride segments go one way only, downhill
- **THEN** its row reads "Trail · 4.70 mi · Ride · Downhill only · 21 segments"

#### Scenario: Mixed trail
- **WHEN** a row lists Clarks, whose ride segments go one way only, uphill, and whose run and hike segments go both ways
- **THEN** its row reads "Trail · 1.42 mi · Run, Hike · Ride uphill only · 3 segments"

#### Scenario: Two-way trail
- **WHEN** a row lists Ann's, with no one-way sport group
- **THEN** its row reads as before, "Trail · 5.40 mi · Ride, Run, Hike · 3 segments"
