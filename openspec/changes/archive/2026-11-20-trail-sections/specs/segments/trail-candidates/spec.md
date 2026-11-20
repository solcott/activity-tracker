# Spec Delta

## MODIFIED Requirements

### Requirement: Stretches are named after their ends

A stretch, or a run cut at a change of direction, SHALL be named after its trail and the full names, sections included, of the named trails or roads at its ends, as "<trail>: <start> → <end>", or "<trail> (part n)" when an end has no name. When the trail is named after its family alone and both ends are sections of that family, it SHALL be named "<start> → <end>", since each end already names the trail.

#### Scenario: Stretch between two crossings
- **WHEN** a stretch of "Oak Trail" runs from where "Fire Road 3" crosses it to where "Pine Trail" meets it
- **THEN** it is named "Oak: Fire Road 3 → Pine"

#### Scenario: Stretch ending at a section
- **WHEN** a stretch of "Bonneville Shoreline Trail - Draper Section" runs from where "Rush Trail - Dreamline Section" meets it to where "Aqueduct Trail" meets it
- **THEN** it is named "Bonneville Shoreline - Draper: Rush - Dreamline → Aqueduct"

#### Scenario: Stretch between two of its family's sections
- **WHEN** a stretch of ways named "Rush Trail" runs from where "Rush Trail - Exit Stage Left Section" meets it to where "Rush Trail - Mystic Rhythms Section" meets it
- **THEN** it is named "Rush - Exit Stage Left → Rush - Mystic Rhythms"

#### Scenario: A section's stretch keeps its trail's name
- **WHEN** a stretch of "Rush Trail - Through the Trees Trail Section" runs from where "Rush Trail - Exit Stage Left Section" meets it to where "Rush Access Trail" meets it
- **THEN** it is named "Rush - Through the Trees Trail: Rush - Exit Stage Left → Rush Access"

## ADDED Requirements

### Requirement: A candidate knows the family section it is named after
Extract SHALL give each candidate the section of its family its name comes from, named as the section is in candidate names (without its generic words), or none. The section SHALL be:
- for a branching parent trail's whole candidate, the section after `›` or `from`;
- for a junction span named after its own parent trail, that trail's section;
- for a candidate of a trail of ways named `<Parent> - <Section>`, whole or a stretch, that `<Section>`.

Every other candidate SHALL have none.

#### Scenario: Branch, its spans and its section
- **WHEN** a family "Rush Trail" gives "Rush › Through the Trees Trail", its span "Rush › Through the Trees Trail → Canyon Hollow", and the section trail "Rush - Through the Trees Trail" with its stretch "Rush - Through the Trees Trail: Rush Access → Canyon Hollow"
- **THEN** all four have the section "Through the Trees Trail"

#### Scenario: Entry partway down
- **WHEN** a parent trail is named "Rush from Mystic Rhythms" and its span "Rush from Mystic Rhythms → Bonneville Shoreline"
- **THEN** both have the section "Mystic Rhythms"

#### Scenario: Span named after the family
- **WHEN** a span is named "Rush from Canyon Hollow" because every parent trail follows its ways
- **THEN** it has no section

#### Scenario: Unbranched family
- **WHEN** a family gives a single parent trail, named "Rush"
- **THEN** that parent trail and its spans have no section, and each `Rush - <Section>` trail's candidates have their own section

#### Scenario: Plain trail
- **WHEN** a trail of ways named "Ann's Trail" gives its whole candidate and stretches
- **THEN** none of them has a section
