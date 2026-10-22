# Spec Delta

## Purpose

Defines gear collections: named, private groups of an athlete's gear items, where an item can belong to many collections, and how a collection's items are added to an activity in one step.

## ADDED Requirements

### Requirement: A collection groups the athlete's items
A gear collection SHALL have a name and optional notes, and SHALL list up to 50 of its athlete's own items in an order the athlete chooses. An item SHALL be able to belong to any number of collections.

#### Scenario: Race kit
- **WHEN** an athlete creates "Race kit" with the Tarmac, an aero helmet, race shoes and a power meter
- **THEN** the collection lists those four items in that order

#### Scenario: Item in two collections
- **WHEN** the athlete also adds the power meter to "Winter training"
- **THEN** the power meter is listed in both collections

#### Scenario: Name required
- **WHEN** the athlete tries to save a collection with a blank name
- **THEN** the save is refused and the form says a name is needed

### Requirement: Collections are private
Collections SHALL be stored only in the athlete's `self` space. The AppView SHALL NOT index or serve them, and no other viewer SHALL see them.

#### Scenario: Public items in a collection
- **WHEN** an athlete puts two Public items in a collection
- **THEN** the collection is stored in their `self` space, and nothing about it is in their repo

### Requirement: Managing collections
The gear list SHALL have a Collections section listing the athlete's collections, each with its item count. A collection's page SHALL list its members, retired ones marked. The athlete SHALL be able to create, rename, reorder, add and remove members, and delete a collection.

#### Scenario: Collection page
- **WHEN** an athlete opens "Dive kit", which holds a retired wetsuit
- **THEN** its members are listed in order and the wetsuit is marked retired

### Requirement: An item's collections
An item's edit form and gear page SHALL show the collections it belongs to and SHALL let the athlete add it to or remove it from any of their collections.

#### Scenario: Adding from the item
- **WHEN** an athlete opens their helmet's edit form and adds it to "Race kit"
- **THEN** "Race kit" lists the helmet at the end

### Requirement: Adding a collection to an activity
The gear picker SHALL offer the athlete's collections. Choosing one SHALL add its members to the activity's gear in collection order, skipping retired and deleted items and items already listed, up to the activity's gear limit. When the limit stops some members from being added, the picker SHALL say so.

#### Scenario: Race kit on a race
- **WHEN** an athlete imports a race with no gear and adds "Race kit"
- **THEN** the race lists the Tarmac, the aero helmet, the race shoes and the power meter

#### Scenario: Already listed
- **WHEN** the activity already lists the Tarmac and the athlete adds "Race kit"
- **THEN** the Tarmac is listed once, and the other three are added after it

#### Scenario: Limit reached
- **WHEN** an activity lists 14 items and the athlete adds a collection of four new items
- **THEN** the first two are added and the picker says two weren't

### Requirement: Activities keep items, not collections
An activity SHALL keep the items a collection added, and SHALL NOT refer to the collection. Changing or deleting a collection SHALL NOT change any activity's gear.

#### Scenario: Collection edited later
- **WHEN** an athlete removes the aero helmet from "Race kit" after adding the collection to last week's race
- **THEN** last week's race still lists the aero helmet

#### Scenario: Collection deleted
- **WHEN** the athlete deletes "Race kit"
- **THEN** its items and every activity's gear are unchanged

### Requirement: Deleted items leave their collections
A collection SHALL NOT list an item that has been deleted. Deleting an item SHALL remove it from the athlete's collections.

#### Scenario: Deleted shoes
- **WHEN** an athlete deletes race shoes that are in "Race kit"
- **THEN** "Race kit" lists three items
