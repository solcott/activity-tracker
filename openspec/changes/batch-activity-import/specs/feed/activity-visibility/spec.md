# Spec Delta

## MODIFIED Requirements

### Requirement: Visibility chosen for each new activity
Recording, manual entry and import (including batch import) SHALL each show a visibility picker for the activity, prefilled from the defaults, on every platform, together with the category pickers in `feed/detail-visibility`. Changing it SHALL affect only that activity (or batch). In a batch, an activity whose account export marks it narrower SHALL be saved at the narrower visibility, as in `feed/batch-import`.

#### Scenario: Override the default
- **WHEN** an athlete whose default is Followers sets one manual entry to Public
- **THEN** that activity is published as Public, and the default stays Followers

#### Scenario: Batch import
- **WHEN** an athlete imports several dives at once with visibility Mutuals
- **THEN** every activity in the batch is published as Mutuals

#### Scenario: Batch with a private export activity
- **WHEN** an athlete imports an export with visibility Mutuals and one activity the export marks private
- **THEN** that activity is published as Only me, and the rest as Mutuals

#### Scenario: No charts picker
- **WHEN** an athlete imports a ride with heart rate and power
- **THEN** no single "who can see these charts" picker is shown, and the Heart rate and Power pickers are
