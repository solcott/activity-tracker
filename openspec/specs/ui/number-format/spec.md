# ui/number-format Specification

## Purpose

How the app writes numbers for people to read on every platform: digit grouping and decimal separators follow the viewer's locale, so large figures read at a glance, and some kinds of number are exempt.

## Requirements

### Requirement: Whole numbers grouped by thousands
Every number the app shows with four or more digits before its decimal point SHALL group those digits in threes with the viewer's locale's grouping separator. This SHALL hold for figures and stats rows, feed cards, tables, the profile, chart axes and markers, and the text read to a screen reader. A decimal SHALL keep its locale's decimal separator after the grouped whole part.

#### Scenario: Elevation in English
- **WHEN** a viewer whose locale is English (US) opens a ride with 1,959 ft of elevation gain
- **THEN** the page shows "1,959 ft"

#### Scenario: German locale
- **WHEN** a viewer whose locale is German opens the same ride in metric units
- **THEN** the page shows its 597 m gain as "597 m", and its 6,676 pedal strokes as "6.676"

#### Scenario: Decimal with a large whole part
- **WHEN** a viewer whose locale is English (US) sees a distance of 1013.94 miles to one decimal place
- **THEN** it reads "1,013.9 mi"

#### Scenario: Negative values
- **WHEN** a dive shows a temperature of −1 °C or an elevation of −1,234 ft
- **THEN** the minus sign comes before the grouped digits, as in "-1,234 ft"

#### Scenario: Screen reader
- **WHEN** a screen reader reads the elevation chart of a ride whose highest point is 5,841 ft
- **THEN** it reads "5,841 ft"

### Requirement: Numbers that are not quantities stay ungrouped
Durations and clock times, dates and years, lap and row numbers, identifiers, and numbers inside text the athlete wrote (titles, descriptions, comments) SHALL NOT be grouped or otherwise reformatted.

#### Scenario: Long duration
- **WHEN** an activity of 10 hours 5 minutes 3 seconds is shown
- **THEN** its time reads "10:05:03"

#### Scenario: Year and title
- **WHEN** an activity titled "Ride 2026" from 2026 is shown
- **THEN** its title and date show "2026" without a separator

### Requirement: Same grouping on every platform
Android, iOS, desktop and web SHALL group the same number the same way for the same locale. Where a platform can't report the locale's grouping separator, the app SHALL use a comma when the decimal separator is a point, and a point otherwise.

#### Scenario: Web and desktop agree
- **WHEN** the same ride is opened on the web app and the desktop app with the locale English (US)
- **THEN** both show "1,959 ft"
