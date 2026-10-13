# Spec Delta

## Purpose

Lets the athlete choose one activity type when recording, entering or importing an activity, from a searchable list of the sport catalog grouped by category.

## ADDED Requirements

### Requirement: Sports collapse in the type list
In the type list, each sport that has sub-sports SHALL be collapsible, with an expand control separate from choosing the sport. Each such sport SHALL start collapsed every time the list opens. A collapsed sport SHALL hide its sub-sports, and expanding it SHALL show them beneath it.

#### Scenario: Collapsed when opened
- **WHEN** the athlete opens the type list
- **THEN** every sport with sub-sports is collapsed and no sub-sport listed under its sport is shown

#### Scenario: Expand a sport
- **WHEN** the athlete expands Running
- **THEN** Running's sub-sports appear beneath it, and the other sports stay collapsed

#### Scenario: Expanding does not choose
- **WHEN** the athlete expands or collapses a sport
- **THEN** the chosen activity type is unchanged and the list stays open

#### Scenario: Reopened collapsed
- **WHEN** the athlete expands a sport, closes the list and opens it again
- **THEN** every sport is collapsed again

### Requirement: Hidden choice is marked
When the chosen activity type is a sub-sport of a collapsed sport, that sport's row SHALL name the chosen sub-sport under its label.

#### Scenario: Chosen sub-sport collapsed
- **WHEN** Trail Run is the chosen type and the list opens
- **THEN** the Run row shows "Trail Run" under its label

### Requirement: Shortcut types always visible
A sub-sport listed in a category without its own sport (a shortcut such as Indoor Ride under Gym) SHALL show as a top-level row and SHALL NOT be hidden by collapsing.

#### Scenario: Shortcut under another category
- **WHEN** the athlete opens the type list
- **THEN** Indoor Ride is visible under Gym without expanding anything

### Requirement: Search shows every match
While a search query is entered, the type list SHALL show every matching type, including matching sub-sports, regardless of which sports are collapsed.

#### Scenario: Search for a sub-sport
- **WHEN** the athlete types "trail" into the search
- **THEN** Trail Run is shown without expanding Running
