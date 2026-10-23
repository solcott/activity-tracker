# gear/activity-gear Specification

## Purpose

Defines which gear an activity used: how the athlete picks it when recording, entering, importing or editing an activity, the defaults per activity type that fill it in, and who may see an activity's gear.

## Requirements

### Requirement: An activity lists its gear
An activity SHALL be able to list up to 16 of its athlete's own gear items, each by its canonical URI, in the order the athlete chose. An activity with no gear SHALL have no gear list. A reference to an item of another athlete SHALL be ignored.

#### Scenario: Ride with a bike and a helmet
- **WHEN** an athlete saves a ride with their Stumpjumper and their helmet
- **THEN** the ride lists both items, Stumpjumper first

#### Scenario: Another athlete's item
- **WHEN** an activity record lists a gear URI in another athlete's repo
- **THEN** no view of the activity shows that item

### Requirement: Gear is picked when an activity is saved
Saving a recording, a manual entry, an import and each activity of a batch import, and editing an activity, SHALL offer a gear picker. It SHALL list the athlete's active items, those whose sports include the activity's sport first. It SHALL keep a retired item that the activity already lists, and SHALL NOT offer other retired items.

#### Scenario: Picking shoes for a run
- **WHEN** an athlete with two pairs of running shoes and a bike saves a manual run
- **THEN** the picker lists both shoes before the bike

#### Scenario: Retired item already on the activity
- **WHEN** an athlete edits a run that lists shoes they have since retired
- **THEN** the picker shows those shoes as selected and offers no other retired item

#### Scenario: Removing gear
- **WHEN** an athlete edits a ride and removes its only bike
- **THEN** after saving, the ride lists no gear

### Requirement: Defaults per activity type
An athlete SHALL be able to mark an item as a default for one or more activity types, either a sport or a sport with a sub-sport. A new activity SHALL start with the defaults for its type. For each gear type, defaults for the activity's sport and sub-sport SHALL replace those for its sport alone. Retired items SHALL NOT be defaults.

#### Scenario: Sport default
- **WHEN** an athlete's Tarmac is the default for ride and they start a manual ride
- **THEN** the ride starts with the Tarmac

#### Scenario: Sub-sport beats sport for the same gear type
- **WHEN** the Tarmac is the default for ride, the Stumpjumper for ride/mountain and a helmet for ride, and the athlete imports a mountain bike ride
- **THEN** the ride starts with the Stumpjumper and the helmet, and not the Tarmac

#### Scenario: Changing type changes defaults
- **WHEN** an athlete changes a manual entry from run to trail run, has made no gear choice yet, and trail run has its own default shoes
- **THEN** the picker switches to the trail run defaults

#### Scenario: Choice survives a type change
- **WHEN** an athlete has picked gear by hand and then changes the activity type
- **THEN** their picked gear is kept

#### Scenario: Retired default
- **WHEN** an athlete retires the shoes that are their run default
- **THEN** a new run starts without them

### Requirement: Defaults are private
An athlete's gear defaults SHALL be stored only in their `self` space. The AppView SHALL NOT index or serve them.

#### Scenario: Setting a default
- **WHEN** an athlete marks a Public bike as the default for ride
- **THEN** the default is stored in their `self` space and nothing about it is in their repo

### Requirement: Viewers see only gear they may see
A viewer SHALL see an item on an activity only if they may see the activity's Gear category and the item itself. The served activity SHALL NOT carry the URI of any item the viewer may not see. The athlete SHALL see all of their activity's gear.

#### Scenario: Public ride, Only me gear category
- **WHEN** a stranger views a Public ride whose Gear category is Only me
- **THEN** they see no gear on it

#### Scenario: Public gear category, private item
- **WHEN** a follower views a Public ride with Public Gear listing a Public bike and an Only me power meter
- **THEN** they see the bike and nothing about the power meter

#### Scenario: Athlete's own view
- **WHEN** the athlete views that ride
- **THEN** they see the bike and the power meter

### Requirement: Gear shown on the activity page
The activity page SHALL show the activity's visible gear in its device section, with each item's name and its kind or type. A deleted item SHALL NOT be shown.

#### Scenario: Deleted bike
- **WHEN** an athlete deletes a bike that their past rides list
- **THEN** those rides show no bike, and their records are unchanged

### Requirement: A combined activity starts with its members' gear
Combining activities SHALL give the combined activity the gear its members list, in member order and without duplicates. The athlete SHALL be able to edit it afterwards.

#### Scenario: Two recordings of one ride
- **WHEN** an athlete combines an Edge recording listing the Tarmac with a watch recording listing the Tarmac and a heart rate strap
- **THEN** the combined ride lists the Tarmac and the strap
