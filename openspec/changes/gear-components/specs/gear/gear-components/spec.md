# Spec Delta

## Purpose

Defines how an athlete mounts one gear item on another for a period, how a mounted item counts its parent's activities in its usage, and how mounts are managed and shown.

## ADDED Requirements

### Requirement: An item can be mounted on another for a period
An athlete SHALL be able to mount one of their items on another of their items from a date, optionally until a date. An item SHALL be mounted on at most one parent at any moment, and its mounts SHALL NOT overlap. An item SHALL keep its past mounts.

#### Scenario: Chain on a bike
- **WHEN** an athlete mounts "Chain #3" on the Tarmac from 2026-03-01
- **THEN** the chain is mounted on the Tarmac from that date, with no end

#### Scenario: Overlapping mount refused
- **WHEN** the athlete tries to mount Chain #3 on the Stumpjumper from 2026-05-01 while it is still on the Tarmac
- **THEN** the mount is refused until the Tarmac mount ends on or before 2026-05-01

#### Scenario: Moved between bikes
- **WHEN** the athlete unmounts a power meter from the Tarmac on 2026-06-01 and mounts it on the Stumpjumper from 2026-06-01
- **THEN** its history shows both mounts, in date order

### Requirement: Mounts nest without loops
An item mounted on a parent SHALL itself be able to hold mounted items, up to three levels below a top-level item. A mount that would make an item its own ancestor, or go deeper than three levels, SHALL be refused.

#### Scenario: Tires on a wheelset on a bike
- **WHEN** an athlete mounts tires on a wheelset that is mounted on the Tarmac
- **THEN** both mounts are saved

#### Scenario: Loop refused
- **WHEN** the athlete tries to mount the Tarmac on that wheelset
- **THEN** the mount is refused

### Requirement: A mounted item counts its parent's activities
An item's usage SHALL count each activity that lists the item, and each activity that lists an item it was mounted on, directly or through nested mounts, at the activity's start time. An activity SHALL count once for an item, however it is reached.

#### Scenario: Chain counts rides of its bike
- **WHEN** Chain #3 is mounted on the Tarmac from 2026-03-01 and the athlete rides the Tarmac 100 km in February and 300 km in March
- **THEN** Chain #3's distance is 300 km

#### Scenario: Tires through a wheelset
- **WHEN** tires are mounted on a wheelset mounted on the Tarmac, and a ride lists only the Tarmac
- **THEN** the ride counts for the Tarmac, the wheelset and the tires

#### Scenario: Listed directly and through its parent
- **WHEN** a ride lists both the Tarmac and the power meter mounted on it
- **THEN** the ride counts once for the power meter

#### Scenario: Swap leaves history
- **WHEN** the athlete replaces Chain #3 with Chain #4 on 2026-07-01
- **THEN** rides before that date still count for Chain #3, later rides count for Chain #4, and no activity is rewritten

### Requirement: Reaching an activity through a parent needs access to the parent
For any viewer other than the athlete, an activity reached through a mount SHALL count only if that viewer may see each item on the path from the listed item down to the component, as well as the activity's gear.

#### Scenario: Public chain on a private bike
- **WHEN** a follower views a Public chain mounted on an Only me bike
- **THEN** the chain's totals for the follower count no activity reached through that bike

### Requirement: Managing mounts on the gear page
The athlete's gear page SHALL offer to mount an item on it, to unmount a mounted item on a date, and to replace a mounted item. Replace SHALL end the current item's mount today and mount the chosen new or existing item from today. Retiring a mounted item SHALL end its mount on the retirement date.

#### Scenario: Replace a chain
- **WHEN** the athlete taps Replace on Chain #3 on the Tarmac and adds "Chain #4"
- **THEN** Chain #3's mount ends today, and Chain #4 is mounted on the Tarmac from today

#### Scenario: Retire a mounted tire
- **WHEN** the athlete retires a tire mounted on the wheelset
- **THEN** its mount ends on the retirement date

### Requirement: The gear page lists components
An item's gear page SHALL list the items mounted on it now, each with its main total, and the items mounted on it before, each with its mount dates. A mounted item's page SHALL show its mount history. Other viewers SHALL see only the items they may see.

#### Scenario: Bike with current and past parts
- **WHEN** the athlete opens the Tarmac after replacing Chain #3 with Chain #4
- **THEN** Components lists Chain #4 with its distance, and Previous lists Chain #3 with its mount dates

### Requirement: Mounts reference only the athlete's own items
A mount SHALL name another item of the same athlete as its parent. A mount naming another athlete's item, or a deleted item, SHALL count nothing.

#### Scenario: Parent deleted
- **WHEN** the athlete deletes a bike that a chain was mounted on
- **THEN** the chain keeps its directly listed activities and counts none through that bike
