# Spec Delta

## ADDED Requirements

### Requirement: The feed is native on iOS
On iOS the feed SHALL be drawn with the system's list and navigation bar under a large title naming the chosen scope, with the native filter bar at the top of the list and Add in the bar, offering Record where the device can record, a manual activity and an imported file. Its loading and error states SHALL follow `ui/content-loading`, with the filter bar shown over a first load's progress or failure, and it SHALL pull to refresh.

#### Scenario: Filtering
- **WHEN** the athlete narrows the native feed to This year
- **THEN** the filter bar's date chip reads "This year", Clear filters shows, and the list shows only this year's activities

#### Scenario: Pull to refresh
- **WHEN** the athlete pulls the native feed down
- **THEN** the system's spinner shows until the refresh ends, and a failed refresh shows why with Retry over the list

### Requirement: The native feed picks its scope from a chip and its title
The native feed SHALL offer the scopes from a chip leading the filter bar, since a large title shows no menu, and from the title's menu once the title collapses inline.

#### Scenario: Switching scope
- **WHEN** the athlete opens the native feed's scope chip on iOS and chooses Mine
- **THEN** the title and the chip read "Mine" and the list shows only their own activities

### Requirement: The native feed lists the whole timeline
The native feed SHALL list the upload rows, each opening the upload queue, the segments and grouping prompts, and the timeline's activities, import cards, posts and reposts as native cards in the timeline's order, or the empty message, with Clear filters when the filters left nothing.

#### Scenario: Nothing matches
- **WHEN** the filters leave nothing on the native feed
- **THEN** it says "No activities match these filters." with Clear filters, which shows everything again

#### Scenario: Opening an item
- **WHEN** the athlete taps an activity, a post or an import card on the native feed
- **THEN** its page opens, as from the shared feed
