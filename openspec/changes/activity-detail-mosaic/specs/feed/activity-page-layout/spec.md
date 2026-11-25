## MODIFIED Requirements

### Requirement: A card for each metric
The page SHALL show one card for each metric the activity has data for and the viewer was served: heart rate, power, cycling dynamics, speed, cadence, elevation, temperature, physiology, depth, oxygen, tank pressure, respiration, running dynamics, e-bike, gears, legs, lengths, sets and laps. The cards SHALL follow the sport's order (Lead card by sport). The first chart card SHALL open at full size; every other chart card SHALL show as a strip that opens in place, and an open card SHALL close back to its strip. Laps, legs, lengths and sets SHALL always show as tables.

#### Scenario: Ride with heart rate and power
- **WHEN** a ride with heart rate, power, speed, cadence and elevation streams is opened
- **THEN** the power card is open, and heart rate, speed, cadence and elevation show as strips in that order

#### Scenario: Opening a strip
- **WHEN** the viewer taps the heart rate strip
- **THEN** the heart rate card opens in its place with its chart and zones, and tapping its title closes it to a strip again

#### Scenario: No streams
- **WHEN** a manually entered run with no streams is opened
- **THEN** the page shows the hero and no metric cards or strips

### Requirement: Layout by window width
The page SHALL arrange the same content by its window's width, on every platform alike. Below 600dp: one column of the mosaic, the header, the hero, the likes and comments block, the training load card, the lead card, the strips, the tables, segment efforts, lifts and the All data row. From 600dp to under 840dp: the mosaic and the hero side by side, then the rest as below 600dp with the strips two to a row. From 840dp: a side pane with the mosaic, header, hero, likes, comments block, Together with, training load and the All data row, beside the lead card, the strips (two to a row once their pane is 640dp wide), tables, segment efforts and lifts, the side pane staying in view as the cards scroll.

#### Scenario: Phone
- **WHEN** the page is shown on a 390dp-wide iPhone or a 412dp-wide Android phone
- **THEN** it is one column, the mosaic first

#### Scenario: Narrow desktop window
- **WHEN** a desktop or web window is resized to 500dp wide
- **THEN** the page shows the same one-column layout as a phone

#### Scenario: Wide desktop window
- **WHEN** the page is shown in a 1280dp-wide desktop or web window
- **THEN** the mosaic, hero and comments sit in a side pane, the cards beside them, and the side pane stays in view while the cards scroll

#### Scenario: Comments before the cards
- **WHEN** a ride with eight metric cards and 14 comments is shown on a phone
- **THEN** the comments block shows right after the likes, before the training load card and the cards

### Requirement: All stats list
The All data page SHALL list every summary figure the viewer was served, including the hero's, as label and value rows in named groups: Timing, Distance & speed, Heart rate, Power, Cadence, Elevation, Work & energy, Temperature, Counts, Dive and Device. The start is the exception: it shows in the activity page's header instead (feed/activity-start-time). A group with no figures SHALL be left out. The activity page SHALL end with a row naming how many figures All data holds, which opens it.

#### Scenario: Dive computer summary
- **WHEN** a dive with a bottom time, a gas, a surface interval and end CNS is opened and the viewer opens All data
- **THEN** its Dive group shows each of them

#### Scenario: Figure not in the hero
- **WHEN** a ride with a descent and front and rear shift counts is opened and the viewer opens All data
- **THEN** it shows the descent under Elevation and the shifts under Counts

#### Scenario: Device
- **WHEN** an activity recorded on a Garmin Edge with a heart rate strap is opened and the viewer opens All data
- **THEN** its Device group names the Edge and the heart rate sensor

#### Scenario: Timing without the start
- **WHEN** a ride with moving and elapsed times is opened and the viewer opens All data
- **THEN** the Timing group shows moving time and elapsed time, and no start time

### Requirement: Avg and max table
The All data page SHALL show a table with one row per metric that has an average or maximum the viewer was served (heart rate, power, speed or pace, cadence, temperature), and columns Avg and Max. When the viewer was served a temperature stream, the table SHALL also have a Min column between Avg and Max, giving the lowest temperature, with an empty cell for every other metric. The activity page itself SHALL NOT show the table; each card's strip and headline give its average and maximum.

#### Scenario: All data
- **WHEN** a viewer opens All data for a ride with heart rate, power, speed and cadence
- **THEN** a table lists heart rate, power, speed and cadence, each with its average and maximum

#### Scenario: Temperature range
- **WHEN** a ride whose temperature samples run from 16 to 27 °C and average 19.24 °C is opened in All data by a viewer using imperial units
- **THEN** the table has Avg, Min and Max columns, its temperature row reads 66.6 °F, 60.8 °F and 80.6 °F, and its other rows leave Min empty

#### Scenario: No temperature stream
- **WHEN** an indoor ride with no temperature stream is opened in All data
- **THEN** the table has only Avg and Max columns

### Requirement: The native activity page lays out by width
On iOS, in a regular-width window at least 840pt wide, the native page SHALL put the mosaic, header, hero, likes, comments block, Together with, training load and the All data row in a side pane beside the lead card, strips, tables, segment efforts and lifts, each scrolling on its own, and SHALL show All data in that pane; otherwise it SHALL show one list in the shared page's compact order, with the section chips, and push All data as a page.

#### Scenario: iPad
- **WHEN** an activity with cards is opened on an iPad at full screen and the viewer opens All data
- **THEN** All data replaces the side pane's summary, with a close button, beside the cards

#### Scenario: iPhone
- **WHEN** the same activity is opened on an iPhone
- **THEN** it shows as one list, the mosaic first, and All data opens as a page of its own

## ADDED Requirements

### Requirement: Media mosaic
The page SHALL open with a mosaic of the activity's route and photos the viewer may see. With a route, the map SHALL take the large tile and up to two photos SHALL stack beside it; with more photos than tiles, the last tile SHALL say how many more ("+2"). With no route, the photos SHALL fill the mosaic, one photo alone filling it. With neither, there SHALL be no mosaic. Tapping the map SHALL open the map, and tapping a photo SHALL open the gallery at it. Photos with a warning label SHALL stay blurred until revealed, and a taken-down activity SHALL show none.

#### Scenario: Ride with four photos
- **WHEN** a ride with a route and four photos is opened on a phone
- **THEN** the map fills the mosaic's large tile, two photos stack beside it, and the second says "+2"

#### Scenario: Dive with one photo
- **WHEN** a dive with no route and one photo is opened
- **THEN** the photo fills the mosaic

#### Scenario: Nothing to show
- **WHEN** a manual walk with no route and no photos is opened
- **THEN** there is no mosaic and the title leads the page

### Requirement: Lead card by sport
The cards SHALL follow the sport's order: on a ride power, heart rate, speed, cadence, elevation; on foot pace, heart rate, elevation, cadence, power; in a swim pace, heart rate, cadence; on a dive depth, tank pressure, temperature, oxygen, heart rate; on a ski or snowboard day the runs, speed, elevation, heart rate; in a sport without a rate the sets, then heart rate; otherwise heart rate, speed, power, elevation, cadence. Sections the order doesn't name SHALL follow in the page's section order. The first card in that order SHALL be the lead, open when the page opens.

#### Scenario: Ride without power
- **WHEN** a ride with heart rate, speed and elevation but no power is opened
- **THEN** heart rate is the open card and speed and elevation are strips

#### Scenario: Strength
- **WHEN** a weight training session with sets and heart rate is opened
- **THEN** the sets table leads, and heart rate is a strip

### Requirement: Strips
A strip SHALL show, on one line, the card's title, its first headline figure larger than its label, its second headline figure muted, and a sparkline of its first chart line; a heart rate or power strip with zones SHALL show its zone times as a thin bar. A strip SHALL be one control with its card's name, saying whether it is open.

#### Scenario: Heart rate strip
- **WHEN** a ride with heart rate zones is opened and heart rate isn't the lead
- **THEN** its strip shows "Heart rate", the average, the maximum, a sparkline and the zone bar

### Requirement: Tables show five rows
Laps, legs, lengths and sets on the activity page SHALL show their first five rows; with more, a Show all button naming the count SHALL open All data at that table.

#### Scenario: Thirteen laps
- **WHEN** a ride with 13 laps is opened
- **THEN** the laps table shows laps 1 to 5 and "Show all 13 laps", which opens All data at the laps

### Requirement: Section chips
Below 840dp, once the mosaic has scrolled out of view, the page SHALL show under its bar a row of chips for the sections it has: Overview, each card in order, each table, Segments when there are efforts, and All data. Tapping a chip SHALL scroll to its section, opening a strip it names. The chip of the section in view SHALL show as selected.

#### Scenario: Jumping to laps
- **WHEN** a viewer scrolls a ride past its mosaic and taps Laps
- **THEN** the page scrolls to the laps table and the Laps chip is selected

### Requirement: All data page
All data SHALL show, under chips for its sections, the all stats list, the avg/max table, every row of the laps, legs, lengths and sets tables, and, for a combined activity, which recording each chart's data came from. Opened from a Show all, it SHALL start at that table.

#### Scenario: Combined ride
- **WHEN** the viewer opens All data on a ride combined from a bike computer and a heart rate strap's recording
- **THEN** its Sources section names the bike computer's recording for speed and the strap's for heart rate

### Requirement: Little data
A section with nothing to show SHALL be left out without a placeholder. With three hero figures or fewer, the hero SHALL show them in one row, set larger than a full hero's.

#### Scenario: Manual walk
- **WHEN** a manual walk with distance, time and pace and no streams is opened
- **THEN** the page shows the header, the three figures in one large row, and the likes and comments block, and no cards or tables
