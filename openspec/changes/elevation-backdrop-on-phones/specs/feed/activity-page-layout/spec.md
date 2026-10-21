# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Elevation behind charts on wide windows`
- TO: `### Requirement: Elevation behind charts`

## MODIFIED Requirements

### Requirement: Elevation behind charts
At every window width, when the activity has an elevation series the viewer was served, each other chart SHALL show it faintly behind its own series. This applies on the activity page and in the import preview.

#### Scenario: Wide window
- **WHEN** a ride with an elevation series and a heart rate stream is shown 1200dp wide
- **THEN** its heart rate chart shows the ride's elevation faintly behind the heart rate

#### Scenario: Phone
- **WHEN** a ride with an elevation series and a heart rate stream is shown 412dp wide on an Android phone
- **THEN** its heart rate chart shows the ride's elevation faintly behind the heart rate, as it does on a wide window

#### Scenario: Import preview on a phone
- **WHEN** a FIT file of a ride with altitude and heart rate is previewed for import 390dp wide
- **THEN** the preview's heart rate chart shows the ride's elevation faintly behind the heart rate

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
- **THEN** its charts show the ride's elevation faintly behind them

#### Scenario: No elevation
- **WHEN** an indoor ride with no elevation series is opened
- **THEN** its charts show no elevation behind them

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
- **THEN** its pace chart shows the run's elevation faintly behind the pace line, with each climb drawn rising, as on a wide window
