# feed/import-cards Specification

## Purpose

Collapses a batch import's older activities into one timeline card per viewer, so a history brought in all at once shows as a single item, and lets the viewer open the batch to see its activities.

## Requirements

### Requirement: Activities carry their import batch
An activity saved from a batch import SHALL name its batch with an identifier shared by every activity saved from that batch. The AppView SHALL ignore an identifier that isn't a well-formed TID, and SHALL treat such an activity as belonging to no batch.

#### Scenario: Malformed batch identifier
- **WHEN** the AppView indexes an activity whose batch identifier is not a TID
- **THEN** the activity is placed and shown as if it named no batch

### Requirement: Backfilled activities
An activity SHALL be backfilled when it was posted more than 5 days after it ended. Only backfilled activities SHALL collapse into an import card.

#### Scenario: Recent activity in a big import
- **WHEN** an athlete imports 300 activities, one of which ended yesterday
- **THEN** that one appears on the timeline on its own, at its post time

### Requirement: Import cards replace a batch's backfilled activities
On an unfiltered timeline, when a viewer may see at least 5 backfilled activities of one batch, those activities SHALL be replaced by one import card. The card SHALL count only activities the timeline would otherwise show that viewer.

#### Scenario: Large history imported
- **WHEN** a follower who may see all 40 backfilled activities of a batch reads their timeline
- **THEN** they see one import card for the batch and none of those 40 activities on their own

#### Scenario: Viewer sees few of them
- **WHEN** a batch has 40 backfilled activities and a viewer may see only 3 of them
- **THEN** that viewer sees the 3 activities on their own, each at its feed position, and no card

#### Scenario: Small batch
- **WHEN** an athlete imports 4 old activities in one batch
- **THEN** no viewer sees an import card for it

### Requirement: Filtered timelines don't collapse
A timeline filtered by type or date SHALL list matching activities on their own and SHALL NOT show import cards.

#### Scenario: Filtered by sport
- **WHEN** a viewer filters their timeline to rides after a follower imported 40 old rides
- **THEN** the matching rides appear on their own, at their feed positions

### Requirement: Card position
An import card's feed position SHALL be the latest post time, capped at when it was indexed, among the backfilled activities the card counts for that viewer.

#### Scenario: Card at the top
- **WHEN** an athlete imports their history and a follower refreshes their timeline right after
- **THEN** the batch's import card is at the top, above activities posted before the import

### Requirement: Card contents
An import card SHALL give its athlete, the number of activities it counts, the earliest and latest start among them, a count for each sport, their total distance and up to 4 route maps. All of these SHALL come only from the activities it counts, as that viewer may see them.

#### Scenario: Private activities in the batch
- **WHEN** a batch has 30 public and 10 Only me backfilled activities and a follower reads the card
- **THEN** the card counts 30 activities and its sports, distance, dates and maps come from those 30 only

#### Scenario: A details record claims a distance
- **WHEN** a narrower tier's details record for an activity gives a distance its own record doesn't
- **THEN** the card's total distance leaves that distance out

### Requirement: Collapsed activities appear once
Paging the timeline SHALL return each import card once, and SHALL return none of the activities it counts on their own, with no gaps or duplicates between pages.

#### Scenario: Card across pages
- **WHEN** a viewer pages through a timeline that holds an import card and 30 other activities
- **THEN** the card and every other activity appear exactly once, in feed-position order

### Requirement: Cards follow blocks, mutes and labels
An import card SHALL NOT be shown to a viewer who blocked, was blocked by or muted its athlete. Activities hidden from a viewer by a label SHALL NOT be counted on the card.

#### Scenario: Muted athlete
- **WHEN** a viewer has muted an athlete who imported their history
- **THEN** the viewer's timeline shows no import card for that athlete

### Requirement: Opening a card
Tapping an import card SHALL open its batch's screen. The card SHALL NOT offer likes or comments.

#### Scenario: Tap the card
- **WHEN** a viewer taps an import card
- **THEN** the batch's screen opens

### Requirement: Batch query
`feed.getImportBatch` SHALL return an athlete's activities from one batch that the viewer may see, recent ones included, newest started first, in pages. Combined activities SHALL show in place of their members. An unknown batch SHALL return an empty list. An unknown actor SHALL be refused with `ActorNotFound`.

#### Scenario: Unknown batch
- **WHEN** a client asks for a batch identifier the athlete never used
- **THEN** the response is an empty list, not an error

#### Scenario: Paging a batch
- **WHEN** a client pages through a batch of 300 activities with each response's cursor
- **THEN** every activity the viewer may see appears once, newest started first

### Requirement: Batch screen
The batch screen SHALL list the batch's activities, newest started first and grouped by month, each opening its activity page. It SHALL show loading, empty and error states as other content-loading screens do.

#### Scenario: Open an activity from the batch
- **WHEN** a viewer taps an activity on the batch screen
- **THEN** that activity's page opens

### Requirement: Profiles don't collapse
An athlete's activity list SHALL list each activity on its own and SHALL NOT show import cards.

#### Scenario: Profile after an import
- **WHEN** a viewer opens the profile of an athlete who imported 300 old activities
- **THEN** the profile lists those activities on their own, by start
