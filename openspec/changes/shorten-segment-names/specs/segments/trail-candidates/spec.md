# Spec Delta

## ADDED Requirements

### Requirement: Generic words are dropped from names
Each OSM, route or agency name a candidate name is built from SHALL lose one trailing generic word of the region's language, matched as a whole word in any case: in English, Foot Path, Footpath, Trail, Path or Section. A `<Parent> - <Section>` name SHALL lose one from each side, and a name ending in a parenthesised qualifier one from before it. A name that would be left empty or only an article SHALL keep its word. Every other word, Road included, SHALL stay.

#### Scenario: Trail and path
- **WHEN** a stretch of "Gas Line Trail" runs from where "Lower Corner Canyon Trail" meets it to where "Hoof N Boot Path" meets it
- **THEN** it is named "Gas Line: Lower Corner Canyon → Hoof N Boot"

#### Scenario: Road kept
- **WHEN** a stretch of "Hog Hollow Road" runs from where "Pork U Pine Trail" meets it to where "Canyon Pointe Road" meets it
- **THEN** it is named "Hog Hollow Road: Pork U Pine → Canyon Pointe Road"

#### Scenario: Sectioned name
- **WHEN** a trail is named "Bonneville Shoreline Trail - Draper Section"
- **THEN** its whole-trail candidate is named "Bonneville Shoreline - Draper"

#### Scenario: Qualifier after the word
- **WHEN** "Peak View Trail (Lower)" may be ridden both ways and runs mostly east from its start
- **THEN** one of its whole-trail candidates is named "Peak View (Lower) (eastbound)"

#### Scenario: Nothing left without the word
- **WHEN** trails are named "Trail" and "The Trail"
- **THEN** their whole-trail candidates are named "Trail" and "The Trail"

#### Scenario: Generic word mid-name
- **WHEN** a trail is named "Section 2 Path"
- **THEN** its whole-trail candidate is named "Section 2"

#### Scenario: Agency name in capitals
- **WHEN** a Forest Service trail is named "SKYLINE TRAIL"
- **THEN** its whole-trail candidate is named "Skyline", title-cased as agency names are

### Requirement: Extract takes the region's language
`extract` SHALL take the region's language as an ISO 639-1 code, `en` when none is given, and drop generic words from names only for a language it has a word list for. For any other language it SHALL keep every name as its source has it, say so in its report, and otherwise extract as usual. It SHALL refuse a language option that isn't a two-letter code.

#### Scenario: Default language
- **WHEN** an operator extracts `us-utah` without a language
- **THEN** names are shortened with the English word list

#### Scenario: Language with no word list
- **WHEN** an operator extracts a region with the language `de`
- **THEN** "Hochwald Weg" keeps its name, the separators are still `›` and `→`, and the report says German names were kept as they are

#### Scenario: Malformed language
- **WHEN** an operator extracts with the language `english`
- **THEN** the importer stops with its usage and writes nothing

## MODIFIED Requirements

### Requirement: Candidates are named after their trail
A whole-trail candidate SHALL be named after its trail. A stretch SHALL be named after its trail and the named trails or roads at its ends, as "<trail>: <start> → <end>", or "<trail> (part n)" when an end has no name. An end that is a `<Parent> - <Section>` way SHALL be named with its section. When a trail has candidates in both directions, a whole-trail name SHALL add its direction of travel: clockwise or counterclockwise for a loop, whose ends are within 30 m, otherwise the compass direction. Names SHALL be at most 100 graphemes.

#### Scenario: Stretch between two crossings
- **WHEN** a stretch of "Oak Trail" runs from where "Fire Road 3" crosses it to where "Pine Trail" meets it
- **THEN** it is named "Oak: Fire Road 3 → Pine"

#### Scenario: Stretch ending at a section
- **WHEN** a stretch of "Bonneville Shoreline Trail - Draper Section" runs from where "Rush Trail - Dreamline Section" meets it to where "Aqueduct Trail" meets it
- **THEN** it is named "Bonneville Shoreline - Draper: Rush - Dreamline → Aqueduct"

#### Scenario: Whole two-way trail
- **WHEN** "Oak Trail" runs mostly north from its start and may be ridden both ways
- **THEN** its whole-trail candidates are named "Oak (northbound)" and "Oak (southbound)"

#### Scenario: Two-way loop
- **WHEN** "Pond Loop" starts and ends at the same place and may be ridden both ways
- **THEN** its whole-trail candidates are named "Pond Loop (clockwise)" and "Pond Loop (counterclockwise)"

### Requirement: A branching family gives a parent trail per path
Where a family's end-to-end joins branch, each path SHALL be its own parent trail, named `<Parent> › <section>` after the first section on it that is on none of the family's other paths, or `<Parent> from <section>` after its first section when every section on it is on another path too. A family SHALL give at most 4 parent trails per sport group, a path and its reverse counting as one; when it has more, only the longest SHALL be kept.

#### Scenario: Alternatives that rejoin
- **WHEN** "Rush Trail - Tom Sawyer Section" leads into both "Rush Trail - Exit Stage Left Section" and "Rush Trail - Through the Trees Trail Section", which both lead into the bare "Rush Trail" way
- **THEN** the family gives two parent trails, whose whole candidates are named "Rush › Exit Stage Left" and "Rush › Through the Trees Trail"

#### Scenario: Entry that joins partway down
- **WHEN** besides two paths from "Rush Trail - Tom Sawyer Section", a third path starts on a way named just "Rush Trail" and continues down "Rush Trail - Mystic Rhythms Section" and sections the other two also follow
- **THEN** the third parent trail's whole candidate is named "Rush from Mystic Rhythms"

#### Scenario: Too many paths
- **WHEN** a one-way family's joins give 6 paths
- **THEN** it gives one parent trail, along the longest path, named `<Parent>`

### Requirement: Junction spans are named after their junction
A span ending at a junction SHALL be named `<trail> → <junction>`, and one starting at a junction `<trail> from <junction>`, in its direction of travel. `<trail>` is its parent trail's name when the span follows a way on none of the family's other parent trails, else `<Parent>`. `<junction>` is the family name of the first, by name, of the named ways outside the family there.

#### Scenario: Span down a branch
- **WHEN** the parent trail "Rush Trail" through "Rush Trail - Exit Stage Left Section", the only one of its family down that section, has a span from its start to where "Canyon Hollow Trail" and "Coyote Hollow Foot Path" meet it
- **THEN** the span is named "Rush › Exit Stage Left → Canyon Hollow"

#### Scenario: Span shared by every branch
- **WHEN** the spans of three parent trails of "Rush Trail" from Canyon Hollow Trail to their end follow the same ways
- **THEN** one candidate is kept for them, named "Rush from Canyon Hollow"

#### Scenario: Junction on a sectioned trail
- **WHEN** a span ends where "Bonneville Shoreline Trail - Draper Section" meets the parent trail
- **THEN** it is named after "Bonneville Shoreline", without its section
