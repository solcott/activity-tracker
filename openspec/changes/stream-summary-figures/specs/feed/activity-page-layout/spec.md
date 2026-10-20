# Spec Delta

## ADDED Requirements

### Requirement: Grade shows its maximum only
The Elevation card's Grade chart SHALL give its maximum grade and SHALL NOT give an average grade: no average figure in its header, no average line on its chart, and none in its screen-reader description.

#### Scenario: Loop ride
- **WHEN** a viewer opens a ride that ends where it started, with a maximum grade of 13.2%
- **THEN** its Grade chart is headed "Max 13.2%" and draws no average line

#### Scenario: Screen reader
- **WHEN** a screen reader reads that ride's Grade chart
- **THEN** it reads its maximum grade and no average

### Requirement: Minimums beside maximums
Where the activity has the stream, the Elevation card SHALL show the lowest point beside the highest. The Temperature card's headline SHALL give average, minimum and maximum. The Respiration card's headline SHALL give average, minimum and maximum. Each minimum SHALL be the lowest sample of its stream the viewer was served.

#### Scenario: Mountain bike ride
- **WHEN** a viewer opens a ride whose elevation runs from 4,557 to 5,841 ft, whose temperature samples run from 16 to 27 °C and whose respiration runs from 16.4 to 42.4 brpm
- **THEN** the Elevation card shows lowest point 4,557 ft beside highest point 5,841 ft, the Temperature card shows min 60.8 °F and max 80.6 °F with its average, and the Respiration card shows min 16.4 brpm

#### Scenario: No elevation stream
- **WHEN** a viewer opens an indoor ride with no elevation stream
- **THEN** there is no lowest point

### Requirement: Minimums in the stats list
The stats list's Elevation group SHALL show the lowest elevation after the highest where the viewer was served the elevation stream. Its Temperature group SHALL show the minimum and maximum temperature after the average where the viewer was served the temperature stream.

#### Scenario: Ride stats
- **WHEN** a viewer opens the ride from Requirement "Minimums beside maximums"
- **THEN** the stats list's Elevation group lists elevation gain, descent, highest and lowest point, and its Temperature group lists avg, min and max temperature

### Requirement: Temperatures to one decimal
Wherever the app shows an activity's temperature (the page, the import preview, feed cards and dive stats), it SHALL show it to one decimal place in the viewer's units, so a whole-degree Celsius sample reads exactly in Fahrenheit.

#### Scenario: Fahrenheit
- **WHEN** a viewer using imperial units opens a ride whose samples peak at 27 °C
- **THEN** its maximum temperature reads 80.6 °F

#### Scenario: Celsius
- **WHEN** a viewer using metric units opens the same ride
- **THEN** its maximum temperature reads 27.0 °C

## MODIFIED Requirements

### Requirement: Avg and max table
From 600dp wide, the page SHALL show a table with one row per metric that has an average or maximum the viewer was served (heart rate, power, speed or pace, cadence, temperature), and columns Avg and Max. When the viewer was served a temperature stream, the table SHALL also have a Min column between Avg and Max, giving the lowest temperature, with an empty cell for every other metric. Below 600dp the page SHALL NOT show the table.

#### Scenario: Wide window
- **WHEN** a ride with heart rate, power, speed and cadence is shown 900dp wide
- **THEN** a table lists heart rate, power, speed and cadence, each with its average and maximum

#### Scenario: Phone
- **WHEN** the same ride is shown 390dp wide
- **THEN** there is no avg/max table, and each card's headline shows its average and maximum

#### Scenario: Temperature range
- **WHEN** a ride whose temperature samples run from 16 to 27 °C and average 19.24 °C is shown 900dp wide to a viewer using imperial units
- **THEN** the table has Avg, Min and Max columns, its temperature row reads 66.6 °F, 60.8 °F and 80.6 °F, and its other rows leave Min empty

#### Scenario: No temperature stream
- **WHEN** an indoor ride with no temperature stream is shown 900dp wide
- **THEN** the table has only Avg and Max columns
