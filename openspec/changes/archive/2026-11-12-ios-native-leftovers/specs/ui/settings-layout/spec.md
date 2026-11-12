# Spec Delta

## MODIFIED Requirements

### Requirement: The iOS zones pages open as the shared pages do
On iOS, Training zones and Privacy zones SHALL open as Settings' pages, beside the list on an iPad as wide as Settings needs. The default zones and a sport's zones opened from Training zones SHALL show beside the list too, as the column's second page with a back button to Training zones, and alone in the stack with the stack's back button. Threshold history SHALL open the same way, drawn natively.

#### Scenario: A sport's zones beside the list
- **WHEN** on an iPad alice opens Training zones beside Settings' list and taps Edit beside her Run zones
- **THEN** Run's zones show beside the list with a back button, and back returns to Training zones beside the list

#### Scenario: Threshold history
- **WHEN** alice taps Threshold history on the native Training zones
- **THEN** the native threshold history opens, with its charts

## ADDED Requirements

### Requirement: Threshold history is native on iOS
On iOS threshold history SHALL be drawn with the system's own grouped list and navigation bar: the shared help, Expand all or Collapse all in the bar, then the defaults and each sport with history as a group whose header expands or collapses it and summarises its thresholds and values. Its loading and refresh SHALL follow `ui/content-loading`, and a failed removal SHALL show why over the list.

#### Scenario: Collapsing a group
- **WHEN** alice taps the header of her expanded defaults on the native threshold history
- **THEN** the defaults collapse to a tile per threshold, and Collapse all becomes Expand all once no group is expanded

#### Scenario: No history
- **WHEN** alice has no threshold history
- **THEN** the page says so

### Requirement: An expanded threshold on iOS shows its chart and values
In an expanded group on iOS each threshold SHALL show its value now and how it moved, a step chart of its dated values running on to today with a dot and its value over each (filled on the current value), its first date and Today under the chart with how many values have no date, and its values newest first with date, source and Current, the newest few unless all are shown.

#### Scenario: A threshold with five values
- **WHEN** alice's threshold power has five values
- **THEN** the newest three show with Show all (5), which shows every value, and then Show fewer

#### Scenario: A single value
- **WHEN** a threshold has one value
- **THEN** it says "Only value" and shows no chart

### Requirement: A collapsed group on iOS shows an overview
In a collapsed group on iOS each threshold SHALL show as a tile with its value now, a sparkline of its progress when it has two dated values, and how it moved.

#### Scenario: A collapsed sport
- **WHEN** alice's Ride history is collapsed
- **THEN** a tile per Ride threshold shows its value, its sparkline and its change

### Requirement: Removing a threshold value on iOS
On iOS each value SHALL offer Remove from a menu, after which it is removed as on the shared page; while one value is being removed no other can be, and the removed row shows progress.

#### Scenario: Removing the current value
- **WHEN** alice removes her current threshold power on the native page
- **THEN** the row shows progress, and the threshold goes back to the value before it
