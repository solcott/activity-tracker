## MODIFIED Requirements

### Requirement: Activities can be declined
Each row SHALL have a checkbox, every non-duplicate selected at first. The athlete SHALL be able to select or deselect all and filter the list to all, new or duplicate activities. Only selected activities SHALL be saved, and select or deselect all SHALL apply to the activities the filter shows. A duplicate's row SHALL say whether it is an exact copy, and the summary SHALL count exact copies apart from other duplicates.

#### Scenario: Decline two
- **WHEN** an athlete deselects two of ten activities and saves
- **THEN** eight activities are saved, and the two declined aren't in their log

#### Scenario: Deselect duplicates only
- **WHEN** an athlete filters to duplicates and taps Deselect all
- **THEN** every duplicate is deselected and the new activities stay selected

#### Scenario: Exact copies counted
- **WHEN** a batch holds 900 exact copies and 40 other duplicates
- **THEN** the summary names both counts, and each exact copy's row says it is an exact copy

### Requirement: Each activity can be previewed
Tapping a row SHALL open a preview of that activity showing its map, stats and charts, as the single-file preview shows them. The preview SHALL let the athlete change only its title and type. It SHALL NOT offer visibility, categories, gear or description. For a duplicate, the preview SHALL also show what it matched (the logged activity, or the earlier activity in the batch): its title, sport, start, length, distance and device beside the duplicate's own, and whether it is an exact copy.

#### Scenario: Check a ride before saving
- **WHEN** an athlete taps a ride's row
- **THEN** they see its map, distance, time and heart rate chart, and fields for its title and type only

#### Scenario: Compare a duplicate
- **WHEN** an athlete opens the preview of a ride flagged as repeating a logged ride
- **THEN** they see the logged ride's title, sport, start, length, distance and device beside the import's

## ADDED Requirements

### Requirement: One action for many duplicates
With the list filtered to duplicates, the athlete SHALL be able to set Ignore, Import anyway or Combine for every duplicate the filter shows at once. Choosing Combine SHALL set Import anyway for any shown duplicate that can't be combined yet. Each duplicate's action SHALL still be changeable on its own afterwards.

#### Scenario: Ignore every duplicate
- **WHEN** an athlete filters a batch of 2,300 activities to its 1,050 duplicates and chooses Ignore for all
- **THEN** every duplicate shows Ignore, and saving saves only the other 1,250

#### Scenario: Combine where possible
- **WHEN** an athlete chooses Combine for all shown duplicates, and one repeats a logged activity that is still uploading
- **THEN** that one shows Import anyway, and the rest show Combine
