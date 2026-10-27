# gear/gear-items Specification

## Purpose

Defines an athlete's gear list: what each gear item records, the gear types offered, where each item is stored by its visibility, and how the athlete adds, edits, retires and deletes items.

## Requirements

### Requirement: A gear item records what it is
Each gear item SHALL have a name and a type. It SHALL optionally have a kind within its type, a brand, a model, the sports it is for, a weight, notes, one photo, the date it was acquired and the date it was retired.

#### Scenario: Bike with details
- **WHEN** an athlete adds a bike named "Stumpjumper" of kind mountain, brand Specialized, model "Stumpjumper EVO", for rides, acquired 2024-04-12
- **THEN** their gear list shows the Stumpjumper with that brand, model, kind and acquired date

#### Scenario: Name only
- **WHEN** an athlete adds shoes named "Old trainers" and fills in nothing else
- **THEN** the item is saved with its name and type only

#### Scenario: Name required
- **WHEN** an athlete tries to save an item with a blank name
- **THEN** the save is refused and the form says a name is needed

### Requirement: Gear types cover the app's sports
The type picker SHALL offer bike, component, shoes, ski, snowboard, ski boots, snowboard boots, cross-country skis, skates, board, boat, paddle, wetsuit, dive gear, helmet, trainer, wheelchair, stroller, sensor, device and other. Each type SHALL offer its own kinds, and kinds SHALL be optional. An item with a type or kind the app doesn't know SHALL be shown as Other, keeping its stored type and kind.

#### Scenario: Bike kinds
- **WHEN** an athlete picks the bike type
- **THEN** the kind picker offers road, gravel, mountain, cyclocross, time trial, e-bike, e-mountain bike, fat bike, kids and tandem

#### Scenario: Component kinds
- **WHEN** an athlete picks the component type
- **THEN** the kind picker offers chain, cassette, chainring, crankset, power meter, tire, wheelset, brake pads, fork, shock, derailleur, saddle, pedals and battery

#### Scenario: Type from a newer client
- **WHEN** an athlete's list holds an item whose type is one this client doesn't know
- **THEN** it is listed under Other with its name, and editing it keeps its stored type unless the athlete picks another

### Requirement: New gear defaults to Only me
A new gear item's visibility SHALL start as Only me. The athlete SHALL be able to choose Public, Followers, Mutuals or Only me for each item.

#### Scenario: Adding gear
- **WHEN** an athlete opens the form to add an item
- **THEN** its visibility is Only me

### Requirement: Each item stored in one place by its visibility
A Public item SHALL be stored in the athlete's repo. A Followers, Mutuals or Only me item SHALL be stored in the athlete's tier space for that visibility, creating the space if it doesn't exist. An item SHALL be stored in exactly one place, except during a move.

#### Scenario: Only-me item
- **WHEN** an athlete saves an Only me pair of skis
- **THEN** the skis are in their `self` space and not in their repo

#### Scenario: Public item
- **WHEN** an athlete saves a Public bike
- **THEN** the bike is in their repo and in no tier space

#### Scenario: First Followers item
- **WHEN** an athlete with no `followers` space saves a Followers item
- **THEN** the `followers` space is created with the AppView as its managing app, and the item is saved in it

### Requirement: An item keeps its identity across moves
An item's record key SHALL stay the same when its visibility changes. Its canonical URI SHALL be `at://{did}/app.activitytracker.gear.item/{rkey}`, wherever it is stored.

#### Scenario: Only me to Public
- **WHEN** an athlete changes an Only me bike to Public
- **THEN** the bike is in their repo under the same record key it had in their `self` space

### Requirement: Moving an item never loses it
When an item's visibility changes, it SHALL be written to its new place before it is removed from its old one. If the save fails part-way, the item SHALL remain stored in at least one place, and the gear list SHALL show it once.

#### Scenario: Failure after the first write
- **WHEN** an athlete changes an item from Mutuals to Only me and the removal from `mutuals` fails
- **THEN** the item is in both spaces, the list shows it once as Only me, and saving again removes the `mutuals` copy

### Requirement: The athlete's gear list
The gear list SHALL show every item the athlete has, read from their repo and tier spaces with their own session, grouped by type. Active items SHALL come first, then a Retired section. Each item SHALL show its name, its kind or type, its photo when it has one, and its visibility.

#### Scenario: Items from every place
- **WHEN** an athlete has a Public bike, a Followers helmet and Only me shoes
- **THEN** all three are listed, with visibilities Public, Followers and Only me

#### Scenario: No gear yet
- **WHEN** an athlete with no gear opens the gear list
- **THEN** it says they have no gear and offers to add an item

#### Scenario: Same item in two places
- **WHEN** an interrupted move left one item in both the repo and the `self` space
- **THEN** the list shows it once, at the narrower visibility

### Requirement: Retiring keeps an item
Retiring an item SHALL record the date it was retired and move it to the Retired section. It SHALL stay stored with all its details. Un-retiring SHALL clear the date and return it to the active items.

#### Scenario: Retire worn shoes
- **WHEN** an athlete retires their "Speedgoat 5" shoes
- **THEN** they appear under Retired with today's date, and their details are unchanged

#### Scenario: Un-retire
- **WHEN** the athlete un-retires them
- **THEN** they are listed with the active shoes again and have no retired date

### Requirement: A retired date is the day in the device's zone
The retired date an item shows SHALL be the calendar day, in the device's time zone, of the moment it was retired. That's the same zone the app uses for the day a component was mounted, so one form never shows dates read in two different zones.

#### Scenario: Retired late in the evening
- **WHEN** an athlete whose device is set to a zone behind UTC retires their shoes at 23:30 local time
- **THEN** the shoes show that day as their retired date, not the next day

#### Scenario: Same zone as the mounted date
- **WHEN** an athlete opens a component that is both mounted and retired
- **THEN** its mounted date and its retired date are both read in the device's zone

### Requirement: Deleting an item is confirmed
Deleting an item SHALL ask for confirmation, and the confirmation SHALL offer to retire the item instead. A confirmed delete SHALL remove the item from wherever it is stored.

#### Scenario: Retire instead
- **WHEN** an athlete deletes a bike and chooses Retire in the confirmation
- **THEN** the bike is retired and not deleted

#### Scenario: Confirmed delete
- **WHEN** an athlete confirms deleting a Followers helmet
- **THEN** the helmet is removed from their `followers` space and no longer listed

### Requirement: Gear photos follow their item's visibility
A Public item's photo SHALL be stored in the athlete's repo. A non-Public item's photo SHALL be stored in the same tier space as the item. Changing the item's visibility SHALL move its photo with it.

#### Scenario: Private bike photo
- **WHEN** an athlete adds a photo to an Only me bike
- **THEN** the photo is stored in their `self` space, not in their repo

### Requirement: Older sessions can save Public gear only
When the athlete's session can't write gear to their tier spaces because it signed in before gear existed, saving a non-Public item SHALL fail with a message asking them to sign in again, and SHALL keep the form as they filled it. Saving a Public item SHALL still work.

#### Scenario: Older session saves private gear
- **WHEN** an athlete who signed in before this change saves a new Only me item
- **THEN** the form stays filled in and says to sign in again to save private gear

#### Scenario: Older session saves public gear
- **WHEN** the same athlete sets the item to Public and saves
- **THEN** the item is saved in their repo
