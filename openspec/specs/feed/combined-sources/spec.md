# feed/combined-sources Specification

## Purpose

Lets an athlete choose which member of a combined activity each kind of data (map, elevation, heart rate, power and so on) comes from, or leave a kind out, and shows viewers which device each chart came from.

## Requirements

### Requirement: Athlete sees each kind's source
A combined activity's page SHALL show its athlete every kind of data any member recorded. A kind SHALL be included by default. For each included kind it SHALL name the member it comes from, by device or start time. Other viewers SHALL NOT see this section.

#### Scenario: Watch and bike computer
- **WHEN** an athlete opens a ride combining a watch with GPS and heart rate and a bike computer with GPS, heart rate, power and altitude
- **THEN** the data sources section lists map, distance and speed, and heart rate, each with its member and a choice of the watch or the bike computer, and elevation and power from the bike computer, all included

#### Scenario: Follower
- **WHEN** a follower opens the same combined ride
- **THEN** they see no data sources section

### Requirement: Choose the member a kind comes from
The athlete SHALL be able to choose, for each listed kind that two or more members recorded, the member it comes from. The combined activity SHALL then be rebuilt with that member first for the kind and the others after it in their previous order. Its figures and charts SHALL follow the new data.

#### Scenario: Strap over wrist
- **WHEN** an athlete chooses the bike computer, which recorded a chest strap, as the heart rate source of a ride whose heart rate came from the watch
- **THEN** the combined activity's heart rate chart, average and maximum come from the bike computer's recording

### Requirement: Leave a kind out
The athlete SHALL be able to exclude any listed kind. The combined activity SHALL then be rebuilt without that kind's channels, charts and figures. Including the kind again SHALL rebuild it with the default ranking for that kind.

#### Scenario: Power left out
- **WHEN** an athlete excludes power from a ride combining a watch and a bike computer with a power meter
- **THEN** the combined ride has no power chart, average power or normalized power, and the bike computer's own activity still has its power

#### Scenario: Included again
- **WHEN** the athlete includes power again
- **THEN** the combined ride's power chart and figures come back from the bike computer

### Requirement: Other members fill in where the chosen one has no data
Where the chosen member has no sample for a kind, the combined activity SHALL take it from the next member that has one.

#### Scenario: GPS lost under trees
- **WHEN** an athlete chooses the bike computer for the map, and it lost GPS for four minutes that the watch recorded
- **THEN** the combined map follows the bike computer, except for those four minutes, which come from the watch

### Requirement: Related channels move together
Choosing a member for a kind, or excluding it, SHALL apply to every channel of that kind:
- power with left-right balance, torque effectiveness, pedal smoothness and platform centre offset;
- distance with speed;
- running dynamics together;
- dive depth with the other dive channels.

#### Scenario: Power and balance
- **WHEN** an athlete chooses their bike computer for power
- **THEN** left-right balance and pedal smoothness come from the bike computer too

#### Scenario: Power and balance left out
- **WHEN** an athlete excludes power
- **THEN** the combined ride has no left-right balance or pedal smoothness either

### Requirement: Choosing or excluding a source never changes members
Choosing or excluding a source SHALL NOT rewrite any member, and SHALL keep the combined activity's title, description, photos, visibility, likes and comments.

#### Scenario: Member untouched
- **WHEN** an athlete changes the elevation source of a combined ride, or excludes its power
- **THEN** both members' records and streams are unchanged, and the combined ride keeps its likes

### Requirement: Charts name their device
Each chart on a combined activity's page SHALL name the device its data came from, for every viewer who may see that chart. When the data switches between members over the activity, it SHALL name each device.

#### Scenario: Single source
- **WHEN** a follower views the power chart of a combined ride whose power came from an Edge 840
- **THEN** the chart is captioned with Edge 840

#### Scenario: Handover
- **WHEN** a viewer views the heart rate chart of a ride combined from a watch that died and a phone recording that finished it
- **THEN** the chart names both the watch and the phone
