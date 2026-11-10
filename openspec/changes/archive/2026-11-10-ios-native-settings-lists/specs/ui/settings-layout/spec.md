# Spec Delta

## ADDED Requirements

### Requirement: The Gear list is native on iOS
On iOS the Gear page SHALL be drawn with the system's own grouped list and navigation bar, and SHALL show what the shared page shows, in the same words and figures: the athlete's collections, then their active items by type, then their retired items, each item with its photo, name, kind and visibility and its one usage total; Add and Ignored devices in the page's bar; and the empty page's message. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Mixed gear
- **WHEN** alice, who rides in metric, opens Gear with a gravel bike used for 3,200 km, new trail shoes she alone sees, and skis with 14 days and 41,000 m of vertical
- **THEN** the bike reads "Bike · Public" and "3,200 km" under Bike, the shoes "Shoes · Only me" and "No activities yet", and the skis "14 days · 41,000 m"

#### Scenario: No collections yet
- **WHEN** alice has gear but no collection
- **THEN** the Collections section reads "Gear you use together. Only you see your collections." and offers New collection

#### Scenario: No gear at all
- **WHEN** alice has no gear and no collection
- **THEN** the page reads "You haven't added any gear yet." with Add gear, and shows no Collections section

#### Scenario: Opening an item
- **WHEN** alice taps an item, a collection, Add, New collection or Ignored devices
- **THEN** that screen opens as it does from the shared page

### Requirement: The blocked and muted lists are native on iOS
On iOS the Blocked accounts and Muted accounts pages SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared pages show, in the same words: each account with its avatar, name and handle, Unblock or Unmute where this app can undo it, and the Bluesky note where it can't; a failed unblock or unmute above the accounts; and the list's empty message. They SHALL load page by page as `ui/content-loading` says.

#### Scenario: Unblocking
- **WHEN** alice taps Unblock beside bob
- **THEN** that button is unavailable while the unblock is written, and bob leaves the list

#### Scenario: A block made on Bluesky
- **WHEN** alice's blocked list holds an account she blocked only on Bluesky
- **THEN** it reads "Blocked on Bluesky. Unblock them there." under the account, with no Unblock

#### Scenario: A failed unblock
- **WHEN** unblocking fails because the server can't be reached
- **THEN** the failure shows above the accounts and the accounts stay

#### Scenario: Nobody muted
- **WHEN** alice opens Muted accounts and has muted no one
- **THEN** the page reads "You haven't muted anyone."

#### Scenario: A long list
- **WHEN** alice scrolls to the end of the accounts loaded so far and more remain
- **THEN** the next page is asked for, with progress below the accounts until it comes

### Requirement: What the iOS lists open opens full width
On iOS a screen opened from the Gear, Blocked accounts or Muted accounts page that isn't a settings page (an item, a collection, a new item or collection, Ignored devices, a profile) SHALL open full width over Settings, alone or beside the list, and back from it SHALL return to the page it was opened from.

#### Scenario: A profile from the blocked list beside the list
- **WHEN** on an iPad alice opens bob's profile from Blocked accounts beside Settings' list
- **THEN** the profile takes the whole window, and back returns to Settings with Blocked accounts beside the list

#### Scenario: An item from the gear list beside the list
- **WHEN** on an iPad alice opens her gravel bike from Gear beside Settings' list
- **THEN** the item takes the whole window, and back returns to Settings with Gear beside the list
