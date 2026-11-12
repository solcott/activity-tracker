# Spec Delta

## ADDED Requirements

### Requirement: The native activity page draws every chart the shared page draws
On iOS each metric card SHALL draw the charts the shared card draws: effort lines with the elevation faintly behind them, a left/right pair's two legs on one chart, every tank on one chart, a dive's depth with its gas switches, warnings and deco ceiling, an e-bike's assist mode in steps, the power curve, and the gears in steps.

#### Scenario: A power card
- **WHEN** a ride with power and a power curve is opened on iOS
- **THEN** its power card shows the power chart with the elevation behind it, then the power curve, then its zones

#### Scenario: A dive with a gas switch
- **WHEN** a dive that switched to EAN50 at 25:00 is opened on iOS
- **THEN** its depth chart marks 25:00 with "EAN50"

#### Scenario: Pedal smoothness
- **WHEN** a ride with left and right pedal smoothness is opened on iOS
- **THEN** one chart draws both legs and its header gives each leg's average

### Requirement: The native activity page draws the structure tables
On iOS the legs, lengths and sets cards SHALL show their tables as the shared page does: a header row, then a row per leg, length or set, a rest or transition with its time only, each row read by a screen reader as one sentence.

#### Scenario: A pool swim
- **WHEN** a pool swim with 20 lengths and two rests is opened on iOS
- **THEN** its lengths card lists 22 rows, each length with its stroke, time, strokes and SWOLF, each rest with its time

### Requirement: A combined activity's cards name their sources on iOS
On iOS each card of a combined activity SHALL be captioned with the devices its data came from, in time order, as the shared card is.

#### Scenario: Two devices
- **WHEN** a combined ride's heart rate came from a Forerunner and then a phone
- **THEN** its heart rate card on iOS reads "From Forerunner, then Phone"

### Requirement: The owner edits a combination on iOS
On iOS the owner's combined activity SHALL offer Edit beside its members, opening a sheet with the members, each removable after a confirmation in the shared words, and the data sources, each kind switched on or off and, when more than one member recorded it, chosen from a menu; the sheet has only Done, since each change applies at once.

#### Scenario: Removing a member
- **WHEN** the owner chooses to remove a member in the sheet and confirms
- **THEN** that member is taken out of the combined activity

#### Scenario: Leaving a kind out
- **WHEN** the owner switches Heart rate off in the sheet
- **THEN** the combined activity is rebuilt without heart rate

### Requirement: Nested efforts show their guides and spans on iOS
On iOS each nested effort SHALL be joined to the effort it's nested under by guide lines in its indent, and each effort of a group with more than one SHALL have a bar under it marking its span within the group, as the shared card draws them.

#### Scenario: A segment within a climb
- **WHEN** an activity's efforts include a climb and a shorter segment within it
- **THEN** the shorter one is indented under the climb with a guide line to it, and each has a bar marking its share of the climb's span

## REMOVED Requirements

### Requirement: The native activity page leaves some charts and tables to later
**Reason**: The native page now draws every chart and table, captions combined cards, edits a combination and nests efforts as the shared page does.
**Migration**: None; the new requirements above replace it.
