# Spec Delta

## MODIFIED Requirements

### Requirement: The Segment and Trail pages show trail details
The Segment page and the Trail page SHALL show a Trail details section built from the served `trail`, with one row for each attribute it holds and none for attributes it lacks. The rows SHALL be grouped into cards, in this order: **Difficulty** (the difficulty scales, trail class and visibility), **Surface & width** (surface, width and typical grade) and **Access & season** (lit, dogs, horses, wheelchair, season, maintained by and website). A card with no rows SHALL be left out. A missing attribute SHALL NOT be shown as a default value. The section SHALL be absent when there is no `trail`. The rows SHALL be:
- **Surface:** the surfaces with their percents, such as "70% paved · 30% gravel", or the surface alone when it is 100%. The smoothness follows in words when known.
- **Difficulty:** each known scale with its name and value in words: "Hiking: Demanding mountain hiking (T3)", "MTB: Intermediate: roots, rocks and small steps", "MTB uphill: Some steep or loose pitches", "IMBA: Blue", "Piste: Intermediate", "Grooming: Classic and skating" and "Trail class: 3 (Developed)". On the Segment page, only the scales that fit the segment's sport group SHALL be shown: hiking for `run` and `hike`; the MTB scales and IMBA for `ride`; piste difficulty for `alpine`; grooming for `ski` and `alpine`. Trail class is shown for every group. The Trail page SHALL show every scale it has.
- **Visibility:** in words.
- **Width:** as the kind of trail that width makes, with no number: "Singletrack" under 1.5 m, "Doubletrack" from 1.5 m to under 3 m, "Road width" from 3 m. The Segment page of a `ski` or `alpine` segment SHALL show no Width row.
- **MTB scales in words:** the MTB scale reads S0 "Beginner: smooth, no obstacles", S1 "Easy: small roots and rocks", S2 "Intermediate: roots, rocks and small steps", S3 "Advanced: big rocks, drops and tight turns", S4 "Expert: very steep and technical", S5 "Extreme: big drops and blocky rock" and S6 "Barely rideable"; the MTB uphill scale reads 0 "Easy climbing", 1 "Some steep or loose pitches", 2 "Steep or loose climbing", 3 "Very steep, technical climbing", 4 "Hike-a-bike in places" and 5 "Mostly hike-a-bike". The S codes are not shown, since most riders don't know them.
- **Typical grade:** as a percent.
- **Lit:** "Lit at night" or "Partly lit". "Not lit" is shown only when the served value is `no`.
- **Dogs:** "Dogs allowed", "Dogs on leash" or "No dogs".
- **Horses:** "Horse trail", "Shared with horses" or "No horses".
- **Wheelchair:** "Wheelchair accessible", "Partly accessible" or "Not accessible".
- **Season:** the open dates in the athlete's locale, such as "Open Jun 15 – Oct 15", else the seasons, such as "Winter only", else "Seasonal".
- **Maintained by:** the operator, as written.
- **Website:** a link that opens the URL in the system browser.

On iOS these rows SHALL be drawn natively, in the shared pages' words, in the same cards.

#### Scenario: Ride segment with mixed data
- **WHEN** a `ride` segment's `trail` holds 70% `paved` and 30% `gravel`, `mtbScale: 2`, `sacScale: hiking` and `dog: leashed`
- **THEN** its Trail details read "70% paved · 30% gravel", "MTB: Intermediate: roots, rocks and small steps" and "Dogs on leash", and no hiking difficulty

#### Scenario: Width as a kind of trail
- **WHEN** a `ride` segment's `trail` holds `widthCm: 100`
- **THEN** its Width row reads "Singletrack", with no measurement

#### Scenario: No width on a piste
- **WHEN** an `alpine` segment's `trail` holds `widthCm: 400`
- **THEN** its page shows no Width row

#### Scenario: Nothing known
- **WHEN** a segment's view carries no `trail`
- **THEN** its page shows no Trail details section

#### Scenario: Website opened
- **WHEN** an athlete taps the Website row of a trail whose website is `https://kingdomtrails.org`
- **THEN** the system browser opens that URL

#### Scenario: Grouped into cards
- **WHEN** a `ride` segment's `trail` holds `mtbScale: 2`, 100% `dirt`, `widthCm: 100` and `dog: leashed`
- **THEN** its Trail details show a Difficulty card with the MTB row, a Surface & width card with "Dirt" and "Singletrack", and an Access & season card with "Dogs on leash"

#### Scenario: Only access known
- **WHEN** a segment's `trail` holds only `dog: no`
- **THEN** its Trail details show the Access & season card alone

## ADDED Requirements

### Requirement: The Trail page starts with its details folded
The Trail page SHALL show its Trail details folded to one line: up to three values, from Surface, Width, Dogs and Season in that order, joined with " · ", and a control that unfolds the grouped cards and folds them again. The page SHALL open folded each time. Without a `trail` it SHALL show neither the line nor the control.

#### Scenario: Folded summary
- **WHEN** an athlete opens a trail whose `trail` holds 100% `dirt`, `widthCm: 100`, `dog: leashed` and open dates Jun 15 to Oct 15
- **THEN** the page reads "Dirt · Singletrack · Dogs on leash" with a Trail details control, and no cards

#### Scenario: Unfolding
- **WHEN** the athlete taps the Trail details control
- **THEN** the Difficulty, Surface & width and Access & season cards show under the line, and tapping again folds them

#### Scenario: No summary values
- **WHEN** a trail's `trail` holds only `mtbScale: 3`
- **THEN** the folded line reads the Trail details control alone, which unfolds the Difficulty card

#### Scenario: Native Trail page
- **WHEN** alice opens a trail with trail details on her iPhone
- **THEN** the native Trail page shows the same folded line and unfolds the same cards
