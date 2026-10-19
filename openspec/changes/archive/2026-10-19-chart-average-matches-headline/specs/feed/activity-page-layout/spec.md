# Spec Delta

## MODIFIED Requirements

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
