# Spec Delta

## ADDED Requirements

### Requirement: The gear page is native on iOS
On iOS the gear page SHALL be drawn with the system's list and navigation bar, titled with the item's name: its photo and details, the owner's collections, its components and mount history, its linked devices and profiles, its totals with any starting usage and its breakdowns, then the activities counted for the viewer as native activity cards, newest first, page by page. The owner's page SHALL offer Edit in the bar. Its loading, refresh and error states SHALL follow `ui/content-loading`, and the item's details SHALL stay on screen while its activities load. An item that doesn't exist or that the viewer may not see SHALL say so in the shared page's words, without Retry.

#### Scenario: Owner's bike
- **WHEN** the athlete opens their gravel bike with 2,000 km of starting usage and two rides
- **THEN** the native page shows Edit, the bike's totals with "Includes 2,000 km before tracking", a row per activity type and per year, and the two rides as cards

#### Scenario: Follower's view
- **WHEN** a follower opens the athlete's Public bike
- **THEN** the native page shows no Edit, no visibility, no collections and nothing to manage, and the rides the follower may see

#### Scenario: Unlinking a device
- **WHEN** the athlete taps Unlink beside the SRAM power meter on the native page
- **THEN** the device is unlinked as from the shared page

#### Scenario: Not found
- **WHEN** the viewer opens an item they may not see
- **THEN** the native page says the gear isn't available, with no Retry
