# Spec Delta

## Purpose
Which summary stats an activity's feed card, activity page and import preview show, so each sport shows the figures that mean something for it and no empty ones.

## ADDED Requirements

### Requirement: No empty distance for sports without a rate
For an activity whose sport has no pace or speed (strength, yoga, HIIT, climbing and the like), the feed card, the activity page and the import preview SHALL leave out the distance stat when the activity's distance is absent or zero, and the activity page SHALL leave out the elevation gain stat when it is absent or zero.

#### Scenario: Strength workout with zero distance
- **WHEN** a strength workout stored with a distance of 0 m and no elevation gain is shown
- **THEN** its feed card shows its time and no distance, and its page shows no distance and no elevation gain

#### Scenario: Strength workout imported from a file
- **WHEN** the athlete previews a strength workout FIT file whose session distance is 0 m
- **THEN** the import preview shows no distance

#### Scenario: Older workouts
- **WHEN** a strength workout published before this change, stored with a distance of 0 m, is shown
- **THEN** it shows no distance, without being edited or republished

### Requirement: Real distance still shown
For a sport without a pace or speed, a distance or elevation gain above zero SHALL still be shown.

#### Scenario: Indoor climb with ascent
- **WHEN** an indoor climb with a stored elevation gain of 120 m is shown
- **THEN** its page shows the elevation gain

### Requirement: Sports with a rate unchanged
For a sport with a pace or speed, the feed card and activity page SHALL show distance as they do today, a dash when it is absent.

#### Scenario: Run without distance
- **WHEN** a manually entered run with no distance is shown
- **THEN** its feed card shows the distance stat with a dash
