# Spec Delta

## MODIFIED Requirements

### Requirement: Default visibilities
An athlete SHALL be able to set a default activity visibility in Settings, alongside the per-category defaults in `feed/detail-visibility`. It SHALL be Public until the athlete sets it.

#### Scenario: Never set
- **WHEN** an athlete who never set defaults starts a new activity
- **THEN** the activity starts as Public

#### Scenario: Charts default capped
- **WHEN** an athlete sets the default activity visibility to Mutuals while the Heart rate default is Public
- **THEN** the Heart rate default becomes Mutuals

### Requirement: Visibility chosen for each new activity
Recording, manual entry and import (including batch import) SHALL each show a visibility picker for the activity, prefilled from the defaults, on every platform, together with the category pickers in `feed/detail-visibility`. Changing it SHALL affect only that activity (or batch).

#### Scenario: Override the default
- **WHEN** an athlete whose default is Followers sets one manual entry to Public
- **THEN** that activity is published as Public, and the default stays Followers

#### Scenario: Batch import
- **WHEN** an athlete imports several dives at once with visibility Mutuals
- **THEN** every activity in the batch is published as Mutuals

#### Scenario: No charts picker
- **WHEN** an athlete imports a ride with heart rate and power
- **THEN** no single "who can see these charts" picker is shown, and the Heart rate and Power pickers are

### Requirement: Visibility editable after publishing
Editing an activity SHALL let the athlete change its visibility and each category's visibility, subject to the category cap.

#### Scenario: Edit visibility
- **WHEN** an athlete opens edit for their Public activity and saves it as Only me
- **THEN** after confirming the warning, the activity is stored in their `self` space, every category is Only me, and other viewers no longer see it

## REMOVED Requirements

### Requirement: Charts are never more visible than their activity
**Reason**: Replaced by one visibility for each detail category, capped at the activity's (`feed/detail-visibility`: "Each category has its own visibility").
**Migration**: No charts setting or default carries over; new activities start every category at the default activity visibility. Old activities' channels stay served at their streams record's tier until they're edited.

### Requirement: Non-public charts stored by visibility
**Reason**: An activity's streams are now split into one record per tier (`feed/detail-visibility`: "Streams split by tier"), so a single streams record no longer stands for all of its charts.
**Migration**: An existing single streams record keeps being served at its tier. Editing the activity re-stores its channels by category.
