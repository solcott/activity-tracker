# Spec Delta

## Purpose

Defines how an athlete links the sensors, recording devices and activity profiles their imported files name to gear items, how those links, with the activity type's defaults, fill in an imported activity's gear, and how the import asks about sensors it hasn't seen before.

## ADDED Requirements

### Requirement: A sensor is identified by its strongest key
A device SHALL be identified by its maker and serial number when the file gives a serial. Otherwise it SHALL be identified by its maker, ANT device number and transmission type when the file gives them. Otherwise it SHALL be identified by its maker and model, and such a key SHALL be shown as one that may match other units of that model.

#### Scenario: SRAM power meter with a serial
- **WHEN** an import names an SRAM power meter with serial 3236233426
- **THEN** its key is SRAM and that serial

#### Scenario: Bluetooth strap without a serial
- **WHEN** an import names a heart rate strap with only its maker and model
- **THEN** its key is its maker and model, marked as a weak match

### Requirement: Links connect sensors and profiles to gear
An athlete SHALL be able to link a sensor or recording device key to one of their items, and an activity profile name to one of their items. A key or profile name SHALL link to at most one item. An item SHALL be able to have several links.

#### Scenario: Linking the head unit
- **WHEN** an athlete links their Edge 530 recorder to their commuter bike
- **THEN** imports recorded on that Edge 530 match the commuter bike

#### Scenario: Profile link
- **WHEN** an athlete links the activity profile `MOUNTAIN` to the Stumpjumper
- **THEN** imports recorded under `MOUNTAIN` match the Stumpjumper

### Requirement: Links stay private
Sensor links, profile links and ignored sensors SHALL be stored only in the athlete's `self` space. No gear item, activity or repo record SHALL hold a serial number or ANT ID. The AppView SHALL NOT index or serve them.

#### Scenario: Public bike with a linked crank
- **WHEN** an athlete links their power-meter crank's serial to a Public bike
- **THEN** the bike's record in their repo holds no serial, and the link is in their `self` space

### Requirement: Imported activities start with matched gear
An imported activity SHALL start with gear chosen per gear type, in this order of priority: items linked to its sensors and recorder, then the item linked to its activity profile, then the defaults for its activity type. A source SHALL fill a gear type only if no higher-priority source filled it. Retired items SHALL be skipped. Collections SHALL NOT be applied automatically.

#### Scenario: Crank beats the sport default
- **WHEN** the ride default is the Tarmac, and an athlete imports a ride whose power meter is linked to the Stumpjumper's crank
- **THEN** the ride starts with the Stumpjumper and not the Tarmac

#### Scenario: Sensors and defaults combine
- **WHEN** a ride's linked sensors give the Stumpjumper, and the ride default also includes a helmet
- **THEN** the ride starts with the Stumpjumper and the helmet

#### Scenario: Profile when no sensor matches
- **WHEN** a ride has no linked sensors and was recorded under the linked profile `MOUNTAIN`
- **THEN** the ride starts with the Stumpjumper

#### Scenario: The athlete can change it
- **WHEN** the import preview shows matched gear
- **THEN** the athlete can change it before saving, as with any gear

### Requirement: A mounted match brings its parent
When a linked item was mounted on another item at the activity's start, the activity SHALL start with the top-level item it was mounted on instead of the linked item. A linked item mounted on nothing then SHALL be added itself.

#### Scenario: Power-meter crank on a bike
- **WHEN** an imported ride names a power meter linked to a crank that has been mounted on the Tarmac since 2026-03-01, and the ride is from April 2026
- **THEN** the ride starts with the Tarmac, and the crank counts the ride through its mount

#### Scenario: Crank moved to another bike
- **WHEN** the same crank was moved to the Stumpjumper on 2026-06-01 and the athlete imports a ride from May 2026
- **THEN** the ride starts with the Tarmac

#### Scenario: Heart rate strap on its own
- **WHEN** an imported run names a strap linked to an unmounted heart rate strap item
- **THEN** the run starts with the strap

### Requirement: Conflicting matches are flagged
When sensors point at two or more items of the same gear type, the activity SHALL start with all of them, and the import preview SHALL say which items conflict.

#### Scenario: Pedals moved without updating mounts
- **WHEN** a ride's power pedals are linked to an item mounted on the Tarmac and its speed sensor to one mounted on the Stumpjumper
- **THEN** the preview lists both bikes and says two bikes matched

### Requirement: New sensors are offered as gear
The import preview SHALL list each device the file names that is neither linked nor ignored, once per import or batch. For each one, the athlete SHALL be able to add it as gear, mark it as not gear, or leave it.

#### Scenario: First import from a new head unit
- **WHEN** an athlete imports their first ride from an Edge 840 with an HRM 200 and an SRAM power meter, none linked
- **THEN** the preview offers the Edge 840, the HRM 200 and the power meter as new devices

#### Scenario: Batch import
- **WHEN** an athlete imports five rides at once, all with the same new power meter
- **THEN** the preview offers the power meter once

#### Scenario: Leave for later
- **WHEN** the athlete saves without answering for the HRM 200
- **THEN** their next import that names it offers it again

### Requirement: Adding a new sensor as gear
Adding a device as gear SHALL create an item named from the file's maker and model, which the athlete can keep or rename, with a type and kind suggested from the sensor's kind and a visibility of Only me. The athlete SHALL be able to mount it on one of their items and add it to collections before saving. Saving SHALL link the device's key to the new item and add the item, or the item it's mounted on, to this import.

#### Scenario: Power meter added and mounted
- **WHEN** the athlete adds the SRAM power meter as gear, keeps the name "SRAM Power meter", mounts it on the Tarmac and adds it to "Race kit"
- **THEN** a component of kind power meter exists, mounted on the Tarmac from the ride's date, listed in "Race kit", linked to the meter's serial, and the ride lists the Tarmac

#### Scenario: Renamed heart rate strap
- **WHEN** the athlete adds the HRM 200 as gear and renames it "Chest strap"
- **THEN** an item of type sensor named "Chest strap" exists, linked to the strap, and the ride lists it

### Requirement: Ignored sensors aren't offered again
Marking a device as not gear SHALL store its key as ignored, and later imports SHALL NOT offer it. The athlete SHALL be able to see their ignored devices and un-ignore any of them.

#### Scenario: Ignore the head unit
- **WHEN** the athlete marks their Edge 840 as not gear
- **THEN** later imports from it don't offer it, and it is listed under Ignored sensors

#### Scenario: Un-ignore
- **WHEN** the athlete un-ignores the Edge 840
- **THEN** the next import from it offers it again

### Requirement: Linking a profile from the import
When an import was recorded under an activity profile that isn't linked, and the athlete has picked gear for it, the preview SHALL offer to link that profile to one of the picked items.

#### Scenario: MOUNTAIN profile
- **WHEN** an athlete picks the Stumpjumper for an import recorded under unlinked `MOUNTAIN` and accepts the offer
- **THEN** `MOUNTAIN` is linked to the Stumpjumper

### Requirement: A new link can be applied to past imports
After a sensor is linked, the app SHALL offer to add its item, or the top-level item it was mounted on at each activity's start, to the athlete's earlier imports whose devices include that sensor and that list no item of that gear type. Accepting SHALL edit those activities' gear. Declining SHALL change nothing.

#### Scenario: Back-filling the Tarmac
- **WHEN** an athlete links a crank mounted on the Tarmac, and 12 earlier imports name that crank, 2 of which already list a bike
- **THEN** the app offers to add the Tarmac to 10 activities, and accepting adds it to those 10

### Requirement: Managing links
An item's gear page SHALL list the sensors and profiles linked to it, each sensor by its maker, model and the last four characters of its serial when it has one, and SHALL let the athlete unlink each one. Unlinking SHALL NOT change any activity.

#### Scenario: Unlink a sold power meter
- **WHEN** the athlete unlinks the power meter from its item
- **THEN** later imports naming it offer it as a new device, and past activities keep their gear
