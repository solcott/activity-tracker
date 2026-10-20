# Spec Delta

## MODIFIED Requirements

### Requirement: Metric card contents
A chart card SHALL show, in order: the metric's title; its headline figures (average and maximum, or what replaces them for the metric, such as normalized power beside power); its chart with the average marked; the metric's other figures as label and value rows; then the zones, power curve or gears the metric has.

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

### Requirement: All stats list
The page SHALL list every summary figure the viewer was served, including the hero's, as label and value rows in named groups: Timing, Distance & speed, Heart rate, Power, Cadence, Elevation, Work & energy, Temperature, Counts, Dive and Device. A group with no figures SHALL be left out.

#### Scenario: Dive computer summary
- **WHEN** a dive with a bottom time, a gas, a surface interval and end CNS is opened
- **THEN** the stats list's Dive group shows each of them

#### Scenario: Figure not in the hero
- **WHEN** a ride with a descent and front and rear shift counts is opened
- **THEN** the stats list shows the descent under Elevation and the shifts under Counts

#### Scenario: Device
- **WHEN** an activity recorded on a Garmin Edge with a heart rate strap is opened
- **THEN** the stats list's Device group names the Edge and the heart rate sensor

## ADDED Requirements

### Requirement: A chart's average is its headline's
The average a chart card's chart marks, and the average and maximum it gives a screen reader, SHALL be the ones its headline shows.

#### Scenario: Screen reader
- **WHEN** a screen reader reads the heart rate chart of a ride whose heart rate headline gives an average of 143 bpm and a maximum of 175 bpm
- **THEN** it reads an average of 143 bpm and a maximum of 175 bpm

### Requirement: Elevation behind charts on wide windows
From 600dp wide, when the activity has an elevation series the viewer was served, each other chart SHALL show it faintly behind its own series. Below 600dp charts SHALL draw no elevation behind them, to keep each within a frame on phones.

#### Scenario: Wide window
- **WHEN** a ride with an elevation series and a heart rate stream is shown 1200dp wide
- **THEN** its heart rate chart shows the ride's elevation faintly behind the heart rate

### Requirement: What the Counts, Dive and Device groups hold
The stats list's Counts group SHALL hold steps, strokes, jumps, shifts, runs, and a mountain bike ride's grit and flow beside its jumps. Its Dive group SHALL hold the dive computer's summary: bottom time, gases, surface interval, CNS, nitrogen, ascent and descent, conditions and its settings. Its Device group SHALL hold the recording device and each sensor.

#### Scenario: Mountain bike ride
- **WHEN** a mountain bike ride with jumps, grit and flow is opened
- **THEN** the stats list's Counts group shows its jumps with its grit and flow beside them
