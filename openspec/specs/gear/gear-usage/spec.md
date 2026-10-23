# gear/gear-usage Specification

## Purpose

Defines how a gear item's usage is totalled from the activities that list it, what each viewer's totals may count, and where the athlete and others see those totals.

## Requirements

### Requirement: Usage totals per item
An item's usage SHALL total, over the activities counted for it: the number of activities, moving time (elapsed time for an activity without moving time), distance, elevation gain, elevation loss and days used (distinct local dates). For dive gear, it SHALL also total dives and bottom time.

#### Scenario: Shoes over three runs
- **WHEN** an athlete's shoes are listed on three runs of 10 km, 12 km and 8 km, two of them on the same local date
- **THEN** the shoes' totals show 3 activities, 30 km and 2 days used

#### Scenario: Activity without moving time
- **WHEN** a manual ski day with 5 hours elapsed and no moving time lists the athlete's skis
- **THEN** it adds 5 hours to the skis' moving time

#### Scenario: Ski vertical
- **WHEN** skis are listed on two ski days with 3,000 m and 4,500 m of elevation loss
- **THEN** the skis' elevation loss total is 7,500 m

### Requirement: Breakdowns by activity type and year
An item's usage SHALL also be broken down by activity type and by year of the activity's local start date, each with the same totals.

#### Scenario: Bike used for rides and commutes
- **WHEN** a bike is on 20 road rides and 50 commutes over 2025 and 2026
- **THEN** its breakdown lists road rides and commutes separately, and 2025 and 2026 separately

### Requirement: Starting usage
An athlete SHALL be able to record an item's starting distance and starting time. They SHALL be added to the item's totals, and SHALL NOT appear in any breakdown.

#### Scenario: Bike bought used
- **WHEN** an athlete adds a bike with a starting distance of 2,000 km and then rides it 150 km
- **THEN** its total distance is 2,150 km, and its yearly breakdown shows 150 km

### Requirement: Each activity counts once
A recording that is a member of a combined activity SHALL NOT count on its own. The combined activity SHALL count with its own gear.

#### Scenario: Two recordings of one ride
- **WHEN** an Edge recording and a watch recording of the same 40 km ride both list the Tarmac and are combined
- **THEN** the Tarmac's distance grows by 40 km, not 80 km

#### Scenario: Split apart again
- **WHEN** the athlete splits that combined activity back into its recordings
- **THEN** each recording counts on its own again

### Requirement: The athlete's totals count everything
The athlete's own view of an item's usage SHALL count all of their activities that list it, whatever the activity's or the Gear category's visibility.

#### Scenario: Only me commute
- **WHEN** the athlete views their bike, which is on Public rides and Only me commutes
- **THEN** the totals include both

### Requirement: Other viewers' totals count only what they may see
Another viewer SHALL get an item's usage only if they may see the item. It SHALL count only the activities whose gear that viewer may see. Starting usage SHALL be added for any viewer who may see the item.

#### Scenario: Follower views a public bike
- **WHEN** a follower views a Public bike listed on 10 Public rides with Public Gear and 5 Only me commutes
- **THEN** the follower's totals count the 10 rides only

#### Scenario: Gear category narrower than the activity
- **WHEN** a ride is Public and its Gear category is Followers
- **THEN** it counts toward the bike for followers, and not for other viewers

#### Scenario: Private item
- **WHEN** a stranger asks for the usage of an Only me item
- **THEN** the request is answered as if the item didn't exist

### Requirement: Totals follow changes
Totals SHALL reflect each activity's current gear, distance, time and visibility. Editing, narrowing or deleting an activity, or removing an item from it, SHALL change the totals that count it.

#### Scenario: Gear removed
- **WHEN** an athlete removes the bike from a 50 km ride
- **THEN** the bike's distance drops by 50 km

#### Scenario: Retired item
- **WHEN** an athlete retires shoes
- **THEN** their totals stay as they were

### Requirement: The gear page
Opening an item from the gear list or from an activity's gear SHALL show its photo, details, totals, starting usage, breakdowns, and the activities counted for that viewer, newest first and paged. The athlete's own page SHALL offer Edit.

#### Scenario: Opening shoes from a run
- **WHEN** a follower taps the Public shoes on an athlete's Public run with Public Gear
- **THEN** the gear page shows the shoes' totals and lists the runs the follower may see

### Requirement: The gear list shows each item's main total
The gear list SHALL show one main total per item: distance for bikes, components, shoes, wheelchairs, strollers and skates; days used and elevation loss for skis, snowboards, cross-country skis and their boots; dives for dive gear; and moving time for every other type.

#### Scenario: Mixed list
- **WHEN** an athlete's list has a bike with 3,200 km, skis with 14 days and 41,000 m of vertical, and a helmet with 120 hours
- **THEN** the rows show 3,200 km, 14 days · 41,000 m, and 120 h, in the athlete's units
