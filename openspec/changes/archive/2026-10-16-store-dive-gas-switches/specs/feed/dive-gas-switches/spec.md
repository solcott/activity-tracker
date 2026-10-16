# Spec Delta

## Purpose

Keeps when a dive switched breathing gases, as each imported dive log recorded it, so the dive's page can show where each gas was breathed and exported files can carry the switches.

## ADDED Requirements

### Requirement: Imports keep each gas switch's time
Importing a dive from a FIT, UDDF, Subsurface or Shearwater file that records gas changes SHALL store each switch in the activity's streams with its time since the dive's start and the gas switched to, as oxygen and helium in whole percents. The first gas breathed SHALL be a switch at time 0.

#### Scenario: FIT dive with a deco gas
- **WHEN** an athlete imports a FIT dive that starts on 21/0 and switches to 50/0 at 32 minutes
- **THEN** its streams hold a switch to 21/0 at 0 seconds and one to 50/0 at 1920 seconds

#### Scenario: UDDF switchmix
- **WHEN** an athlete imports a UDDF dive whose waypoint at 1500 seconds has a `switchmix` to a mix of 0.32 oxygen
- **THEN** its streams hold a switch to 32/0 at 1500 seconds

#### Scenario: Shearwater gas change
- **WHEN** an athlete imports a Shearwater dive whose records change from 0.21 to 0.50 oxygen at 1800 seconds
- **THEN** its streams hold a switch to 50/0 at 1800 seconds

#### Scenario: Single-gas dive
- **WHEN** an athlete imports a dive breathed on 32/0 throughout
- **THEN** its streams hold one switch, to 32/0 at 0 seconds

### Requirement: Gases breathed stay as before
The dive's gases breathed SHALL still be listed on the activity, in order of first use, whether or not switch times are stored.

#### Scenario: Gases and switches agree
- **WHEN** an athlete imports a dive that switches from 21/0 to 50/0 and back to 21/0
- **THEN** the activity lists 21/0 and 50/0 once each, and its streams hold three switches

### Requirement: Switches the file can't place are dropped
A switch the file records without a time, or to a gas the file doesn't define, SHALL NOT be stored in the streams. The gases breathed SHALL still be derived as before.

#### Scenario: FIT switch to an unknown slot
- **WHEN** an athlete imports a FIT dive with a `dive_gas_switched` event naming a gas index that has no `dive_gas` message
- **THEN** that switch is not stored, and the others are

### Requirement: Depth chart marks gas switches
The dive's depth chart SHALL mark each stored switch at its time with the gas's name: Air for 21/0, EAN and the oxygen percent for nitrox, Tx oxygen/helium for trimix. A dive with no stored switches SHALL show no markers.

#### Scenario: Switch to EAN50
- **WHEN** a viewer opens a dive with a switch to 50/0 at 32 minutes
- **THEN** the depth chart has an EAN50 marker at 32 minutes

#### Scenario: Older dive
- **WHEN** a viewer opens a dive imported before switch times were stored
- **THEN** the depth chart shows no gas markers

### Requirement: Merging fills missing switches
Merging another recording into a dive SHALL add the other recording's gas switches when the dive has none, shifted onto the dive's start, and SHALL leave the dive's own switches when it has them.

#### Scenario: Logbook switches added to a dive without them
- **WHEN** an athlete merges a UDDF dive with gas switches into a logged dive whose streams have none
- **THEN** the logged dive's streams hold the UDDF dive's switches at their times relative to the logged dive's start
