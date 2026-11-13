## MODIFIED Requirements

### Requirement: A saved item's management on iOS
On iOS the form of a saved item SHALL also show what it is mounted on and since when, which opens that item; the day it was retired; Retire, or Put back in use for a retired item; and Delete. Delete SHALL ask first in the system's alert, in the shared form's words, offering Retire instead; only its confirmation SHALL delete.

#### Scenario: A mounted chain
- **WHEN** alice edits Chain #4, mounted on her gravel bike since 1 July 2026
- **THEN** the form reads "Mounted on Gravel bike since Jul 1, 2026", and tapping it opens the gravel bike's page

#### Scenario: Asked before deleting
- **WHEN** alice taps Delete on her gravel bike
- **THEN** an alert asks "Delete this gear?", says "Deleting removes it for good. Retiring keeps it and its details, out of the way.", and offers Retire, Delete and Cancel
