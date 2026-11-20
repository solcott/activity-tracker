## ADDED Requirements

### Requirement: Trail attributes are an optional public record object
`feed.defs#trailAttributes` SHALL be an object whose fields are all optional, so a record carries only what its source data says:
- `surfaces`: up to 8 entries of a surface and its whole-number percent of the length whose surface is known. The surface is an open enum: `paved`, `compacted`, `gravel`, `dirt`, `grass`, `rock`, `sand`, `wood`, `snow`, `other`. Entries run from the largest share down, and their sum is at most 100, rounding and dropped small shares making up the rest.
- `smoothness`: `excellent`, `good`, `intermediate`, `bad`, `very_bad`, `horrible`, `very_horrible` or `impassable`.
- `sacScale`: `hiking`, `mountain_hiking`, `demanding_mountain_hiking`, `alpine_hiking`, `demanding_alpine_hiking` or `difficult_alpine_hiking`.
- `mtbScale`: an integer from 0 to 6.
- `mtbScaleImba`: an integer from 0 to 4.
- `mtbScaleUphill`: an integer from 0 to 5.
- `pisteDifficulty`: `novice`, `easy`, `intermediate`, `advanced`, `expert`, `freeride` or `extreme`.
- `pisteGrooming`: `classic`, `skating`, `classic_skating`, `mogul`, `backcountry` or `scooter`.
- `trailClass`: an integer from 1 to 5.
- `visibility`: `excellent`, `good`, `intermediate`, `bad`, `horrible` or `no`.
- `widthCm`: a positive integer.
- `gradePercent`: a non-negative integer.
- `lit`: `yes`, `partly` or `no`.
- `dog`: `yes`, `leashed` or `no`.
- `horse`: `designated`, `yes` or `no`.
- `wheelchair`: `yes`, `limited` or `no`.
- `seasons`: a non-empty list of `spring`, `summer`, `autumn`, `winter`, `dry_season` or `wet_season`.
- `seasonal`: `true` when it is open only part of the year with no seasons or dates known.
- `openFrom` and `openUntil`: each a month and day as `MM-DD`, given together.
- `operator`: at most 100 graphemes.
- `website`: an `http` or `https` URL of at most 512 characters.

`feed.segment` and `feed.path` SHALL each accept an optional `trail` holding this object. A record without it SHALL say nothing about its trail.

#### Scenario: Record without attributes
- **WHEN** a `feed.segment` has no `trail`
- **THEN** it is indexed as before, and its view carries no trail attributes

#### Scenario: Partial attributes
- **WHEN** an importer segment's `trail` holds only `surfaces` of 100% `gravel`
- **THEN** its view carries that surface and no other trail attribute

### Requirement: OSM tags map to trail attributes
The importer SHALL read each attribute from an OSM way's tags:
- **Surface:** `surface`. `asphalt`, `concrete` (with any subtype), `paved`, `paving_stones`, `sett`, `chipseal`, `metal` and `bricks` SHALL be `paved`. `compacted` and `fine_gravel` SHALL be `compacted`. `gravel` and `pebblestone` SHALL be `gravel`. `dirt`, `earth`, `ground`, `soil`, `mud` and `unpaved` SHALL be `dirt`. `grass` and `grass_paver` SHALL be `grass`. `rock` and `stone` SHALL be `rock`. `sand` SHALL be `sand`. `wood` and `woodchips` SHALL be `wood`. `snow` and `ice` SHALL be `snow`. Any other value SHALL be `other`.
- **Surface from track type:** a way without `surface` SHALL take it from `tracktype`: `grade1` as `paved`, `grade2` as `gravel`, and `grade3` to `grade5` as `dirt`.
- **Smoothness:** `smoothness`.
- **Hiking difficulty:** `sac_scale`.
- **MTB difficulty:** `mtb:scale`, `mtb:scale:imba` and `mtb:scale:uphill`, each by its leading digit. A value such as `2+` counts as 2.
- **Piste difficulty and grooming:** `piste:difficulty`, and `piste:grooming` with `classic+skating` read as `classic_skating`.
- **Visibility:** `trail_visibility`.
- **Width:** `width`, in metres unless it names `cm`, `m`, `ft`, `'` or `"`.
- **Access:** `lit`, `dog` and `wheelchair`. For `lit`, any value but `no` SHALL count as lit. For `dog`, `designated` and `permissive` SHALL be `yes`.
- **Horses:** `horse`, with `permissive` read as `yes`.
- **Seasons:** `seasonal`, either `yes`, giving `seasonal` with no season known, or a `;`-separated list of seasons.
- **Open dates:** `opening_hours`, only in the form of one month or month-day range such as `Apr-Nov` or `May 15-Oct 31`. Any other form SHALL be ignored.
- **Operator and website:** `operator`, and `website` (or `contact:website`).

A value outside an attribute's vocabulary SHALL be ignored for that attribute. Only `surface` falls back to `other`.

A way that lacks `dog`, `horse`, `wheelchair`, `operator` or `website` SHALL take the value from a named route relation it belongs to when every such relation that has the tag agrees.

#### Scenario: Gravel path
- **WHEN** a trail's only way is tagged `surface=fine_gravel`
- **THEN** its candidate's attributes hold `surfaces` of 100% `compacted`

#### Scenario: Track without a surface
- **WHEN** a trail's only way is tagged `highway=track` and `tracktype=grade3` with no `surface`
- **THEN** its candidate's attributes hold `surfaces` of 100% `dirt`

#### Scenario: Plus grade
- **WHEN** a way is tagged `mtb:scale=2+`
- **THEN** it counts as MTB scale 2

#### Scenario: Unknown value
- **WHEN** a way is tagged `sac_scale=T3`, which isn't one of the scale's values
- **THEN** it gives no hiking difficulty

#### Scenario: Website from the route
- **WHEN** a way without `website` belongs to the route relation "Kingdom Trails Loop" tagged `website=https://kingdomtrails.org`
- **THEN** the way counts as having that website

#### Scenario: Complex opening hours
- **WHEN** a way is tagged `opening_hours=Mo-Fr 08:00-18:00`
- **THEN** it gives no open dates

### Requirement: Agency fields map to trail attributes
The importer SHALL read trail attributes from agency features. Values are matched ignoring case and surrounding spaces, and a leading code such as `NAT -` or `TG05 -` is ignored.

From a Forest Service feature:
- **Surface:** `trail_surface`. Asphalt and concrete SHALL be `paved`. Crushed aggregate or gravel SHALL be `gravel`. Imported compacted material SHALL be `compacted`. Native material and imported loose material SHALL be `dirt`. Chunk wood SHALL be `wood`. Snow SHALL be `snow`. Other SHALL be `other`.
- **Trail class:** `trail_class`, 1 to 5.
- **Width:** `typical_tread_width`, as the middle of its inch range in centimetres. An open-ended range counts as its bound.
- **Grade:** `typical_trail_grade`, as the upper bound of its percent range without its sign.
- **Wheelchair:** `accessibility_status`, with `ACCESSIBLE` as `yes` and `NOT ACCESSIBLE` as `no`.
- **Horses:** `designated` when pack and saddle use is managed. `yes` when it is otherwise accepted in any season, `no` when it is restricted all year and allowed in no season.
- **Open dates:** the season of the managed or accepted use for the candidate's sport group, when it is one `MM/DD-MM/DD` range that isn't the whole year.

From a Park Service feature:
- **Surface:** `TRLSURFACE`. Asphalt, Bituminous, Concrete, Brick, Paver and Masonry/Stone SHALL be `paved`. Aggregate, Gravel and Gravel Road SHALL be `gravel`. Dirt, Earth, Soil, Clay, Native, Native Material, Other Unpaved and mixtures of earth and gravel SHALL be `dirt`. Grass and Earth/Grass SHALL be `grass`. Stone and Lava SHALL be `rock`. Sand SHALL be `sand`. Wood and Wood Chips SHALL be `wood`. Snow SHALL be `snow`. Other values SHALL be `other`.
- **Trail class:** `TRLCLASS`, from the first digit, 1 to 5.
- **Seasons:** `SEASONAL` of `Yes` SHALL give `seasonal`, with no season known.
- **Operator:** `MAINTAINER`.

From a BLM feature:
- **Surface:** `OBSRVE_SRFCE_TYPE`. Solid Surface SHALL be `paved`. Aggregate SHALL be `gravel`. Natural and Natural Improved SHALL be `dirt`. Snow SHALL be `snow`.

`N/A`, `None`, `Unknown`, `Not Applicable`, `N`, numeric codes with no published meaning, and blank values SHALL give no attribute.

#### Scenario: Forest Service native trail
- **WHEN** a Forest Service feature has `trail_surface` `NAT - NATIVE MATERIAL`, `trail_class` `3` and `typical_tread_width` `TW03 - 18-24 INCHES`
- **THEN** it counts as `dirt`, trail class 3 and 53 cm wide

#### Scenario: Park Service class label
- **WHEN** a Park Service feature has `TRLCLASS` `Class 4: Highly Developed`
- **THEN** it counts as trail class 4

#### Scenario: Seasonal Forest Service bike use
- **WHEN** a Forest Service feature has `bicycle_managed` `06/15-10/15`
- **THEN** its `ride` candidate is open from `06-15` until `10-15`

#### Scenario: Unknown agency value
- **WHEN** a Park Service feature has `TRLSURFACE` `12`
- **THEN** it gives no surface

### Requirement: Attributes combine over the ways a line covers
A candidate's or path's attributes SHALL be combined from the ways or features it follows, each weighted by the length of the line that lies along it. Each attribute SHALL combine as follows:
- **Surfaces:** each surface's share of the length whose surface is known, rounded down to whole percents, so a stretch whose ways give no surface counts as no other surface. A surface whose share rounds down to 0% SHALL be dropped; any other is kept, so a short gravel stretch on a dirt trail still shows on the trail as on its segments.
- **Difficulties** (`smoothness`, `sacScale`, the three MTB scales, `pisteDifficulty`, `trailClass` and `visibility`): the hardest value that, counted together with every harder value, lies along at least 50 m of the line, or along all of it when the line is shorter (30 m of S4 and 30 m of S3 give S3). The hardest smoothness and visibility are the worst. The hardest trail class is the lowest, the least developed.
- **Width:** the length-weighted median of the known widths.
- **Grade:** the steepest known grade along at least 50 m of the line.
- **Lit:** `yes` when at least 90% of the line is lit, `no` when every way with a known `lit` is unlit and they cover at least 90% of the line, and `partly` otherwise when any of it is lit.
- **Dogs:** the strictest known value (`no`, then `leashed`, then `yes`).
- **Horses:** the most open known value (`designated`, then `yes`, then `no`), since it tells riders whether they may meet horses.
- **Wheelchair:** `no` when any of it is `no`, else `limited` when any of it is `limited`, else `yes` when at least 90% of the line is known accessible, else none.
- **Seasons and open dates:** kept only when every way that gives them agrees. When they disagree but every way that says says it is seasonal, the line is `seasonal`; a way tagged `seasonal=no` against one that is seasonal leaves none.
- **Operator, website and piste grooming:** the value covering the most of the line.

An attribute SHALL be included only when the ways that give it cover at least 25% of the line, except the strict ones: `dog: no`, `wheelchair: no` and `wheelchair: limited` SHALL be included when any way gives them.

#### Scenario: Mixed surface
- **WHEN** a 2 km candidate follows 1.4 km of `asphalt` and 0.6 km of `gravel`
- **THEN** its `surfaces` are 70% `paved` and 30% `gravel`

#### Scenario: Untagged stretches
- **WHEN** 740 m of a 1 km trail is tagged `surface=ground` and the other 260 m has no surface
- **THEN** its `surfaces` are 100% `dirt`

#### Scenario: A short stretch of another surface
- **WHEN** a 4 km trail is dirt except for 110 m of `gravel`
- **THEN** its `surfaces` are 97% `dirt` and 2% `gravel`

#### Scenario: One hard pitch
- **WHEN** a 3 km ride candidate is `mtb:scale=1` except for 120 m tagged `mtb:scale=3`
- **THEN** its MTB scale is 3

#### Scenario: Short hard step ignored
- **WHEN** a 3 km ride candidate is `mtb:scale=1` except for 20 m tagged `mtb:scale=4`
- **THEN** its MTB scale is 1

#### Scenario: Sparse tag left out
- **WHEN** only 300 m of a 2 km candidate carries `trail_visibility`
- **THEN** its attributes hold no visibility

#### Scenario: One no-dogs stretch
- **WHEN** 200 m of a 4 km candidate is tagged `dog=no` and the rest `dog=leashed`
- **THEN** its attributes hold `dog: no`

### Requirement: Attributes are served only for the importer's records
The segment view SHALL carry a segment's `trail` only when the segment is in the segment importer account's repo. The path view SHALL carry a path's `trail`. The AppView SHALL drop the following from a served `trail`: any field outside its vocabulary or range, `openFrom` without `openUntil` or the reverse, a `website` that isn't an absolute `http` or `https` URL, and an `operator` longer than 100 graphemes. It SHALL serve no `trail` when nothing is left.

#### Scenario: Athlete's segment with attributes
- **WHEN** an athlete publishes a `feed.segment` in their own repo with `trail` holding `mtbScale: 2`
- **THEN** its view carries no trail attributes

#### Scenario: Unsafe website dropped
- **WHEN** an importer path's `trail.website` is `javascript:alert(1)`
- **THEN** its view carries the path's other attributes and no website

### Requirement: The Segment and Trail pages show trail details
The Segment page and the Trail page SHALL show a Trail details section built from the served `trail`, with one row for each attribute it holds and none for attributes it lacks. A missing attribute SHALL NOT be shown as a default value. The section SHALL be absent when there is no `trail`. The rows SHALL be:
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

On iOS these rows SHALL be drawn natively, in the shared pages' words.

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
