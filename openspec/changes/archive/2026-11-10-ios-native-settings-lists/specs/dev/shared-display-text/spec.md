# Spec Delta

## ADDED Requirements

### Requirement: Gear reads the same in both UIs
A gear type's and kind's names, an item's usage total, the line under an item's name, and a collection's item count SHALL be chosen by shared code in the athlete's units, and both UIs SHALL show that choice.

#### Scenario: A kind the app doesn't name
- **WHEN** an item's kind is one this client has no name for
- **THEN** both UIs show its type's name in its place

#### Scenario: Skis in imperial
- **WHEN** skis have 14 days and 41,000 m of vertical and the athlete uses imperial units
- **THEN** both UIs read "14 days · 134,514 ft"

#### Scenario: A retired item where retirement is shown
- **WHEN** a retired bike visible to everyone is listed where retirement is shown
- **THEN** both UIs read "Bike · Public · Retired" under its name

### Requirement: The block lists read the same in both UIs
Each block list's title, empty message, remove action, the note for a block this app can't undo, and what failed when the list can't load SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: The muted list
- **WHEN** the muted list is shown
- **THEN** both UIs title it "Muted accounts" and offer "Unmute"

### Requirement: A paged list's statuses are decided once
What a paged list shows around its items in each loading state SHALL be decided by shared code that both UIs ask, so the two can't differ.

#### Scenario: A refresh of an empty list
- **WHEN** an answered list holds nothing and is refreshing
- **THEN** both UIs show the refresh's progress above and not the empty message
