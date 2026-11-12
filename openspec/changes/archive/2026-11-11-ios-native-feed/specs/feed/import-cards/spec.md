# Spec Delta

## ADDED Requirements

### Requirement: The import card is native on iOS
On iOS the feed's import card SHALL be a native card saying what the shared one says: the author with the months of the first and last start in the viewer's time zone, how many activities, each sport's count and the total distance in the viewer's units, and up to four of the routes as line sketches. A tap SHALL open the batch.

#### Scenario: Bob's import
- **WHEN** the native feed lists Bob's import of 312 activities
- **THEN** its card reads "Imported 312 activities" with his sports' counts and distance, and a tap opens "Imported by Bob"
