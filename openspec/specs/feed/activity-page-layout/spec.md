# feed/activity-page-layout Specification

## Purpose

How an activity's page and its import preview present the activity's data: the headline figures, one card per metric, the training-load card, the full stats list and the layout for each window width, so every figure is readable on Android, iOS, desktop and web.

## Requirements

### Requirement: Hero figures by sport
The activity page SHALL open with a hero row of at most six figures, chosen and ordered by the activity's sport family. A figure the viewer was not served, or that `feed/activity-stats` leaves out, SHALL be skipped without a gap. Ride: distance, moving time, elevation gain, avg speed, avg power, calories. Run, walk, hike: distance, moving time, avg pace, elevation gain, avg heart rate, calories.

#### Scenario: Mountain bike ride with power
- **WHEN** a viewer admitted to every category opens a mountain bike ride with distance, moving time, elevation gain, speed, power and calories
- **THEN** the hero shows, in order, distance, moving time, elevation gain, avg speed, avg power and calories

#### Scenario: Hidden power
- **WHEN** a viewer not admitted to a ride's Power tier opens it
- **THEN** the hero shows its other figures with no avg power and no empty slot

#### Scenario: Run
- **WHEN** a run with distance, moving time, pace, elevation gain, heart rate and calories is opened
- **THEN** the hero shows distance, moving time, avg pace, elevation gain, avg heart rate and calories

### Requirement: Hero figures for other sports
The hero SHALL use these orders for the other sport families. Swim: distance, moving time, avg pace per 100, avg heart rate, calories. Dive: max depth, dive time, minimum water temperature, avg depth. Ski and snowboard: runs, descent, moving time, max speed. Sports without a pace or speed: time, avg heart rate, calories, then sets or reps when recorded. Any other sport SHALL use the ride order without avg power.

#### Scenario: Dive
- **WHEN** a dive with depths, a dive time and water temperatures is opened
- **THEN** the hero shows max depth, dive time, minimum water temperature and avg depth

#### Scenario: Strength workout
- **WHEN** a strength workout with no distance, a heart rate and recorded sets is opened
- **THEN** the hero shows time, avg heart rate, calories and its sets, and no distance

### Requirement: Readable figures
Every figure on the page and the import preview SHALL show its full label, wrapping to more lines rather than being cut off or ellipsized. Its value SHALL be shown in full, with its unit smaller than its number. Labels SHALL name the figure in full, such as "Normalized power" and "Avg speed", not a shortened form.

#### Scenario: Narrow window
- **WHEN** the page is shown in a 360dp-wide window
- **THEN** every hero and card label is shown in full, wrapping where it doesn't fit on one line

#### Scenario: Value with unit
- **WHEN** the hero shows a distance of 7.83 mi
- **THEN** "7.83" is drawn larger than "mi"

### Requirement: A card for each metric
The page SHALL show one card for each metric the activity has data for and the viewer was served: heart rate, power, cycling dynamics, speed, cadence, elevation, temperature, physiology, depth, oxygen, tank pressure, respiration, running dynamics, e-bike, gears, legs, lengths, sets and laps. A card SHALL always be open. The page SHALL have no control to collapse or expand cards.

#### Scenario: Ride with heart rate and power
- **WHEN** a ride with heart rate, power, speed, cadence and elevation streams is opened
- **THEN** each of those five shows as an open card with its chart, and there is no expand or collapse control

#### Scenario: No streams
- **WHEN** a manually entered run with no streams is opened
- **THEN** the page shows the hero and the stats list and no metric cards

### Requirement: Metric card contents
A chart card SHALL show, in order: the metric's title; its headline figures (average and maximum, or what replaces them for the metric, such as normalized power beside power); its chart with the average marked; the metric's other figures as label and value rows; then the zones, power curve or gears the metric has. The average the chart marks, and the average and maximum it gives a screen reader, SHALL be the ones its headline shows. From 600dp wide, when the activity has an elevation series the viewer was served, each other chart SHALL show it faintly behind its own series. Below 600dp charts SHALL draw no elevation behind them, to keep each within a frame on phones.

#### Scenario: Power card
- **WHEN** a viewer admitted to Power opens a ride with power and power zones
- **THEN** the power card shows avg, max and normalized power, the power chart with its average line over the ride's elevation, work and the other power figures as rows, then time in power zones and the power curve

#### Scenario: Chart average matches the headline
- **WHEN** a pool swim whose stored average pace is 1:59 /100 yd, and whose speed samples average 2:00 /100 yd, is opened
- **THEN** the Pace card's headline, its chart's average line and the chart's description all give 1:59 /100 yd

#### Scenario: No stored average
- **WHEN** an activity with a heart rate stream but no stored average heart rate is opened
- **THEN** the heart rate card's headline and chart both give the stream's average

#### Scenario: Phone
- **WHEN** a ride with an elevation series is shown 390dp wide
- **THEN** its charts show no elevation behind them

#### Scenario: No elevation
- **WHEN** an indoor ride with no elevation series is opened
- **THEN** its charts show no elevation behind them

### Requirement: Training load card
When the viewer was served the activity's training load, the page SHALL show it in its own card above the metric cards: FTP, intensity factor, training stress score, aerobic and anaerobic training effect and RMSSD, each where recorded. Viewers not served it SHALL see no such card.

#### Scenario: Owner with training load
- **WHEN** an athlete opens their own ride with a training stress score and training effects
- **THEN** a training load card above the metric cards shows them

#### Scenario: Viewer without zones
- **WHEN** a viewer not admitted to the activity's zones opens it
- **THEN** there is no training load card

### Requirement: Pace card
For a sport timed by pace (runs, walks, hikes and swims), the speed card SHALL be a Pace card. Its headline SHALL give the average and best pace, per kilometer or mile, or per 100 m or yd swimming, and its chart SHALL plot pace with the faster pace at the top. Where the page draws elevation behind charts, the Pace card's chart SHALL draw it as every other card does, higher ground at the top, and SHALL NOT flip it with the pace line.

#### Scenario: Pace card
- **WHEN** a pool swim with a speed stream is opened
- **THEN** it shows a Pace card with the average and best pace per 100 and a pace chart, and no speed card

#### Scenario: Hilly run
- **WHEN** a run with pace and elevation streams is shown 900dp wide
- **THEN** its pace chart shows the run's elevation faintly behind the pace line, with each climb drawn rising, and the line and its fill stay readable over it

#### Scenario: Hilly run on a phone
- **WHEN** the same run is shown 390dp wide
- **THEN** its pace chart shows no elevation behind it

### Requirement: All stats list
The page SHALL list every summary figure the viewer was served, including the hero's, as label and value rows in named groups: Timing, Distance & speed, Heart rate, Power, Cadence, Elevation, Work & energy, Temperature, Counts (steps, strokes, jumps, shifts, runs, and a mountain bike ride's grit and flow beside its jumps), Dive (the dive computer's summary: bottom time, gases, surface interval, CNS, nitrogen, ascent and descent, conditions and its settings) and Device (the recording device and each sensor). A group with no figures SHALL be left out.

#### Scenario: Dive computer summary
- **WHEN** a dive with a bottom time, a gas, a surface interval and end CNS is opened
- **THEN** the stats list's Dive group shows each of them

#### Scenario: Figure not in the hero
- **WHEN** a ride with a descent and front and rear shift counts is opened
- **THEN** the stats list shows the descent under Elevation and the shifts under Counts

#### Scenario: Device
- **WHEN** an activity recorded on a Garmin Edge with a heart rate strap is opened
- **THEN** the stats list's Device group names the Edge and the heart rate sensor

### Requirement: Elevation gain only when it says something
The hero and the stats list SHALL show elevation gain only when it is recorded, and for a swim only when it is above zero. Unlike distance, a missing elevation gain SHALL NOT be shown as a dash.

#### Scenario: Pool swim
- **WHEN** a pool swim with no elevation gain is opened
- **THEN** the stats list has no Elevation group

### Requirement: Layout by window width
The page SHALL arrange the same content by its window's width, on every platform alike. Below 600dp: one column of hero, map, training load card, metric cards, stats list and comments. From 600dp to under 840dp: hero and map side by side, cards two to a row, laps full width. From 840dp: a side pane with the map, hero and avg/max table beside the cards, the map staying in view as the cards scroll.

#### Scenario: Phone
- **WHEN** the page is shown on a 390dp-wide iPhone or a 412dp-wide Android phone
- **THEN** it is one column, the hero first

#### Scenario: Narrow desktop window
- **WHEN** a desktop or web window is resized to 500dp wide
- **THEN** the page shows the same one-column layout as a phone

#### Scenario: Wide desktop window
- **WHEN** the page is shown in a 1280dp-wide desktop or web window
- **THEN** the map, hero and avg/max table sit in a side pane, the cards beside them, and the map stays in view while the cards scroll

### Requirement: Avg and max table
From 600dp wide, the page SHALL show a table with one row per metric that has an average or maximum the viewer was served (heart rate, power, speed or pace, cadence, temperature), and columns Avg and Max. Below 600dp the page SHALL NOT show the table.

#### Scenario: Wide window
- **WHEN** a ride with heart rate, power, speed and cadence is shown 900dp wide
- **THEN** a table lists heart rate, power, speed and cadence, each with its average and maximum

#### Scenario: Phone
- **WHEN** the same ride is shown 390dp wide
- **THEN** there is no avg/max table, and each card's headline shows its average and maximum

### Requirement: Visibility shown where the data is
On their own activity, the athlete SHALL see each category narrower than the activity marked with its visibility on the card that shows it. Heart rate is marked on the heart rate card, power on the power card, and pace/speed on the speed card. The Start time category SHALL be marked by the start time, and Map by the map. Viewers SHALL see no such marks.

#### Scenario: Owner with private heart rate
- **WHEN** the athlete opens their Public ride with Heart rate Only me
- **THEN** the heart rate card's title is marked Only me, and no other card is marked

#### Scenario: Another viewer
- **WHEN** a follower opens the same ride
- **THEN** no card is marked with a visibility

### Requirement: Native card surfaces
Cards SHALL use each platform's surface for grouped content: inset grouped sections on iOS, and outlined cards on Android, desktop and web.

#### Scenario: iOS
- **WHEN** the page is shown on iOS
- **THEN** each card is an inset grouped section on the grouped background

#### Scenario: Android
- **WHEN** the page is shown on Android
- **THEN** each card is an outlined card

### Requirement: Import preview shows the page
Before saving a single activity, the import screen SHALL show what the activity's page will show its owner: the same hero, training load card, metric cards and stats list, built from the picked file. It SHALL show every category, whatever the visibility chosen, with each category marked as Requirement "Visibility shown where the data is" describes.

#### Scenario: FIT ride with power
- **WHEN** the athlete picks a FIT ride with heart rate, power and cadence
- **THEN** before saving they see its hero, its heart rate, power and cadence cards with charts and zones, and its stats list

#### Scenario: Narrowing a category
- **WHEN** the athlete sets the preview's Heart rate to Only me
- **THEN** the heart rate card stays shown and is marked Only me

### Requirement: Preview follows the chosen type
Changing the activity type on the import screen SHALL update the preview to that type's hero figures and stats, as Requirement "Hero figures by sport" describes, before the athlete saves.

#### Scenario: Ride to run
- **WHEN** the athlete changes a preview's type from Ride to Run
- **THEN** the hero shows avg pace in place of avg speed

### Requirement: Import save panel
The import screen SHALL keep its choices (type, title, description, photos, visibility, and the threshold changes saving makes) in a save panel apart from the preview. From 840dp wide the panel SHALL sit beside the preview. Below 840dp it SHALL follow the preview, with Save in the navigation bar on iOS and in a bottom action bar on Android, desktop and web.

#### Scenario: Wide window
- **WHEN** the athlete previews a ride in a 1200dp-wide window
- **THEN** the save panel is beside the preview

#### Scenario: iPhone
- **WHEN** the athlete previews a ride on an iPhone
- **THEN** the save panel follows the preview, and Save is in the navigation bar

#### Scenario: Android phone
- **WHEN** the athlete previews a ride on an Android phone
- **THEN** the save panel follows the preview, and Save is in a bottom action bar

### Requirement: Import notices above the preview
The import screen's duplicate notice and its time zone choice for a file without one SHALL show above the preview, since they change what it shows. A logbook of several dives SHALL keep its batch form in place of the preview.

#### Scenario: Zone-less dive log
- **WHEN** the athlete picks a dive log whose times have no zone
- **THEN** the time zone choice shows above the dive's hero

#### Scenario: Logbook
- **WHEN** the athlete picks a logbook of several dives
- **THEN** they see the batch form, not a single activity's preview

### Requirement: Charts break at gaps in their samples
A time-series chart on the activity page SHALL draw no line and no fill between two consecutive samples of a series that are further apart than that series' gap limit: 2 minutes, or four of its usual sample intervals when that is longer. This SHALL hold for every series drawn over time, including a second line, the elevation behind a chart and a dive's lines, on every activity, combined or not. A sample missing its value SHALL count as no sample. Gears and the power curve SHALL NOT break.

#### Scenario: Device change on a combined ride
- **WHEN** a combined ride whose bike computer stopped recording heart rate and power, and whose watch started recording 23 minutes later, is opened
- **THEN** its heart rate and power charts draw no line or fill across those 23 minutes, and the line resumes where the watch's samples begin

#### Scenario: Auto-pause on a single recording
- **WHEN** a ride recorded at one sample a second with a 10-minute auto-pause is opened
- **THEN** each of its charts breaks across the 10 minutes and draws unbroken on either side

#### Scenario: Short stops stay joined
- **WHEN** a ride recorded at one sample a second with a 90-second stop is opened
- **THEN** its charts draw unbroken lines across the stop

#### Scenario: Smart recording
- **WHEN** a ride with smart recording, samples 1 to 8 seconds apart and no longer gaps, is opened
- **THEN** its charts draw unbroken lines

#### Scenario: Sparse steady recording
- **WHEN** a run with heart rate logged every 60 seconds is opened
- **THEN** its heart rate chart draws one unbroken line

#### Scenario: Sensor dropout
- **WHEN** a ride whose heart rate strap stopped for 5 minutes while power kept recording is opened
- **THEN** the heart rate chart breaks across those 5 minutes and the power chart does not

#### Scenario: Gears
- **WHEN** a ride with a gear shift 10 minutes after the previous one is opened
- **THEN** the gears chart holds the earlier gear until the shift, unbroken

### Requirement: Thinned charts keep the recorded gaps
When a long series is thinned to fewer chart points, the chart SHALL break exactly where the recorded samples have a gap: no point SHALL be drawn inside a gap, and thinning SHALL neither hide a gap nor make one where the samples had none.

#### Scenario: Long ride with a short gap
- **WHEN** a 10-hour ride recorded at one sample a second, with a 3-minute gap in the middle, is opened
- **THEN** its charts break across those 3 minutes, with no point inside them, and draw unbroken elsewhere
