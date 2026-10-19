# feed/activity-devices Specification

## Purpose

Keeps, privately and per activity, the recording device and the sensors an imported file names, with the identifiers that tell one device from another across activities, so gear can later be assigned from them.

## Requirements

### Requirement: An imported activity keeps its devices
Importing an activity from a file that names its recording device or paired sensors SHALL store the activity's devices: one entry for the recording device and one for each paired sensor, in the order the file first lists them. An activity recorded in the app, entered by hand, or imported from a file naming no device SHALL have no devices.

#### Scenario: FIT ride with sensors
- **WHEN** an athlete imports a FIT ride recorded on a Garmin Edge 840 with a Garmin HRM 200 and a SRAM AXS derailleur paired
- **THEN** the activity's devices list the Edge 840 as the recorder, then the HRM 200 and the derailleur as sensors

#### Scenario: TCX run
- **WHEN** an athlete imports a TCX run whose `Creator` names a Forerunner 965 with a `UnitId`
- **THEN** the activity's devices list the Forerunner 965 as the recorder, with that unit ID as its serial number

#### Scenario: GPX import
- **WHEN** an athlete imports a GPX file
- **THEN** the activity has no devices

#### Scenario: Recorded in the app
- **WHEN** an athlete records a run in the app and saves it
- **THEN** the run has no devices

### Requirement: Each device records what tells it apart
Each device SHALL record its role (recorder or sensor), and where the file gives them: kind (the public record's sensor kinds), maker, model, serial number, ANT device number and transmission type, connection (ANT+, Bluetooth or other), software version, battery status, percent and voltage, and total operating time. A device SHALL be kept even when the file gives it no identifier.

#### Scenario: ANT+ sensor with a serial
- **WHEN** a FIT file's `device_info` names an ANT+ power meter with serial number 3912345678, device number 51234 and transmission type 5
- **THEN** its entry has role sensor, kind power, connection ANT+, serial number `3912345678`, ANT device number 51234 and transmission type 5

#### Scenario: Edge 840 with SRAM power and shifting
- **WHEN** a FIT ride from an Edge 840 names an SRAM power meter (serial 3236233426, operating time 2,221,408 s, battery voltage 1.57 V) and an SRAM AXS derailleur (serial 1518497200, operating time 25,872 s), with no ANT device numbers
- **THEN** each entry has its serial number, operating time and battery voltage, and no ANT device number or transmission type

#### Scenario: Bluetooth sensor without a serial
- **WHEN** a FIT file names a Bluetooth heart rate strap with only its maker and product
- **THEN** its entry has role sensor, kind heart rate, connection Bluetooth, its maker and model, and no serial number or ANT fields

### Requirement: The recorder's built-in sensors are left out
The recording device's built-in sensors (GPS, barometer, wrist heart rate and the like, which FIT lists as local devices) SHALL NOT have entries of their own. The recording device's own entry SHALL take its identifiers from the file's header (FIT `file_id`) before any `device_info` message.

#### Scenario: Watch with wrist heart rate
- **WHEN** a FIT run from a Fenix 7 lists the watch, its GPS and its wrist heart rate as local devices, and nothing paired
- **THEN** the activity's devices hold one entry, the Fenix 7 as recorder, with the `file_id` serial number

### Requirement: A sensor listed several times is one device
A sensor the file lists more than once SHALL be one entry. Each field SHALL come from the last message that gives it, so a field one message lacks is filled from another, and battery and operating time reflect the activity's end.

#### Scenario: device_info at start and end
- **WHEN** a FIT file lists a heart rate strap at the start with battery Good and at the end with battery Low at 20%
- **THEN** the activity's devices hold one entry for the strap, with battery Low and 20%

#### Scenario: Serial sent after the first message
- **WHEN** a FIT file's first `device_info` for a power meter has no serial number, and a later one for the same device index has serial 3236233426
- **THEN** the power meter's entry has serial number `3236233426`

#### Scenario: A later message lacks a field
- **WHEN** a sensor's first `device_info` gives its software version and its last gives only its battery
- **THEN** its entry has both the software version and the last battery

### Requirement: The activity profile is kept with the devices
When the file names the activity profile the device recorded under (FIT session `sport_profile_name`), the activity's devices SHALL keep that name as the activity profile.

#### Scenario: Edge profile name
- **WHEN** an athlete imports a FIT ride whose session's sport profile name is `MOUNTAIN`
- **THEN** the activity's devices record the activity profile `MOUNTAIN`

#### Scenario: No profile name
- **WHEN** the FIT file's session has no sport profile name
- **THEN** the activity's devices record no activity profile

### Requirement: A dive keeps its dive computer
A dive imported from a UDDF, Subsurface or Shearwater log SHALL keep its dive computer as the recorder, with its serial number where the log gives one (UDDF `serialnumber`, Subsurface's device ID, Shearwater's serial number). Each dive of a logbook SHALL keep the computer the log assigns to it.

#### Scenario: UDDF dive computer serial
- **WHEN** an athlete imports a UDDF dive whose `divecomputer` has model `Perdix 2` and serial number `A1B2C3`
- **THEN** the dive's devices list a Perdix 2 recorder with serial number `A1B2C3`

#### Scenario: Logbook from two computers
- **WHEN** an athlete imports a Subsurface logbook whose dives were logged by two computers, and saves one dive from each
- **THEN** each saved dive's devices name the computer that logged it

### Requirement: Devices are stored only in the athlete's self space
An activity's devices SHALL be stored in the athlete's `self` space under the activity's record key, whatever the activity's visibility, and nowhere else. The athlete's repo and the activity's public record SHALL NOT hold serial numbers, ANT IDs, battery state or operating time. Changing the activity's visibility SHALL NOT move or change its devices.

#### Scenario: Public import
- **WHEN** an athlete imports a FIT ride as a Public activity
- **THEN** its devices are in the `self` space, and the activity record in their repo has make and model only

#### Scenario: Visibility change
- **WHEN** an athlete changes an imported activity from Public to Only me
- **THEN** its devices stay in the `self` space, unchanged

### Requirement: Only the athlete reads the devices
An activity's devices SHALL be readable only by the athlete who owns it. The AppView SHALL NOT index or serve them, and no view of the activity SHALL show them to anyone else.

#### Scenario: A follower views the activity
- **WHEN** a follower opens an athlete's imported Followers activity
- **THEN** the AppView's view of it carries no serial number, ANT ID or battery state

### Requirement: Devices are written with their activity
An activity's devices SHALL be queued with it and sent after it, offline or online, so an imported activity that reaches the PDS gets its devices too. An import whose original file is too large to keep SHALL still keep its devices.

#### Scenario: Offline import
- **WHEN** an athlete imports a FIT file offline and later comes online
- **THEN** the activity and then its devices are sent

#### Scenario: Oversized file
- **WHEN** an athlete imports a FIT file whose stored form exceeds 10 MB
- **THEN** the activity has no source but has its devices

### Requirement: An older session imports without devices
When the athlete's session can't write devices to their `self` space (it signed in before the app could), importing SHALL publish the activity and its other records without devices, and SHALL NOT fail or ask the athlete to sign in again.

#### Scenario: Session from before devices
- **WHEN** an athlete signed in before devices were kept imports a FIT ride
- **THEN** the ride publishes with its source and no devices

### Requirement: Merging fills missing devices
Merging an imported activity into one of the athlete's logged activities SHALL give the logged activity the import's devices when it has none, stored or queued, and SHALL NOT replace devices it already has.

#### Scenario: Merge into an app recording
- **WHEN** an athlete merges an imported FIT ride into a ride they recorded in the app
- **THEN** the recorded ride gets the FIT ride's devices

#### Scenario: Merge into an import with devices
- **WHEN** an athlete merges a FIT file into a dive that already has devices
- **THEN** the dive's devices stay as they were

### Requirement: Grouped recordings keep their own devices
An imported activity grouped as another recording of a logged activity SHALL keep its own devices. Splitting a recording off its group, and editing an activity, SHALL leave each activity's devices as they are.

#### Scenario: Two recordings of one ride
- **WHEN** an athlete groups an Edge 840 recording of a ride with a Fenix 7 recording of the same ride
- **THEN** each recording keeps its own devices, naming its own recorder

### Requirement: Deleting an activity deletes its devices
Deleting an activity SHALL delete its devices. Deleting a main activity with its recordings SHALL delete each recording's devices too. Recordings split off instead SHALL keep theirs.

#### Scenario: Delete an imported ride
- **WHEN** an athlete deletes an imported ride
- **THEN** its devices are deleted from the `self` space

### Requirement: Earlier imports are not backfilled
Activities imported before devices were kept SHALL have no devices, and the app SHALL NOT fill them in from their stored original files.

#### Scenario: Ride imported last month
- **WHEN** an athlete views a ride imported before this change
- **THEN** it has no devices, and its original file is unchanged
