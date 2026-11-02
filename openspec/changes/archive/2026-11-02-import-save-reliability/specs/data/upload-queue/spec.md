## MODIFIED Requirements

### Requirement: Imports are grouped
The activities of one saved import SHALL be listed together under it, with:
- what was imported
- when it was saved
- how many of its activities are uploaded out of all it saved
- how many are refused and skipped

The athlete's other items SHALL be listed apart from imports.

#### Scenario: Import part way
- **WHEN** 412 of an import's 1,362 saved activities are uploaded and 3 were skipped at save
- **THEN** its group shows 412 of 1,362 uploaded and 3 skipped

#### Scenario: Import still saving
- **WHEN** an import of 1,362 activities has saved 500 so far
- **THEN** its group shows it saving, 500 of 1,362

#### Scenario: Import stopped while saving
- **WHEN** an import of 400 selected activities stopped saving after 120 and can't resume
- **THEN** its group says it stopped after 120 of 400 and to pick the files again to import the rest

### Requirement: Cancelling the rest of an import
The athlete SHALL be able to cancel an import's waiting and refused items, and its activities not yet saved, at once. Its uploading and uploaded activities SHALL be kept.

#### Scenario: Stop a large import
- **WHEN** the athlete cancels the rest of an import with 412 uploaded, 1 uploading and 949 waiting
- **THEN** the 949 are dropped, and the 413 others are kept and finish uploading

#### Scenario: Stop an import still saving
- **WHEN** the athlete cancels the rest of an import with 500 saved and 400 not yet saved
- **THEN** its waiting items are dropped and the 400 are never saved
