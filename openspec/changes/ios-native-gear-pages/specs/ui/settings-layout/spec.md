# Spec Delta

## ADDED Requirements

### Requirement: A gear collection is native on iOS
On iOS a gear collection's page SHALL be drawn with the system's own grouped list and navigation bar, and SHALL show what the shared page shows, in the same words: the collection's name as its title, its notes, and its items in the athlete's order, each with its photo, name, kind, who may see it and Retired where it is; the empty collection's message; and a failed delete above the items. Its loading SHALL follow `ui/content-loading`.

#### Scenario: A collection with a retired item
- **WHEN** alice opens her collection "Dive kit", noted "Cold water setup", holding a regulator and fins everyone may see and a retired fullsuit she alone sees
- **THEN** the page is titled "Dive kit", reads "Cold water setup", and lists the regulator as "Regulator · Public", the fullsuit as "Fullsuit · Only me · Retired" and the fins as "Fins · Public", in that order

#### Scenario: An empty collection
- **WHEN** alice opens a collection that holds no items
- **THEN** the page reads "No items yet"

#### Scenario: Before it loads
- **WHEN** alice opens a collection that hasn't loaded yet
- **THEN** the page is titled "Collections", shows its progress, and offers no Edit or Delete

#### Scenario: Opening an item
- **WHEN** alice taps the regulator in "Dive kit"
- **THEN** the regulator's gear page opens, as from the shared page

### Requirement: Editing and deleting a collection on iOS
On iOS a loaded collection's page SHALL offer Edit and Delete in its bar, as the shared page does. Delete SHALL ask first in the system's alert, in the shared page's words, and only its confirmation SHALL delete. While the collection is being deleted Edit and Delete SHALL be unavailable; once deleted the page SHALL close.

#### Scenario: Asked before deleting
- **WHEN** alice taps Delete on "Dive kit"
- **THEN** an alert asks "Delete this collection?", says "Its items, and the gear on your activities, stay as they are.", and offers Delete collection and Cancel

#### Scenario: Cancelled
- **WHEN** alice taps Cancel in that alert
- **THEN** the alert closes and the collection stays

#### Scenario: A failed delete
- **WHEN** deleting fails because the server can't be reached
- **THEN** the failure shows above the items, the items stay, and Edit and Delete are available again

### Requirement: Ignored devices is native on iOS
On iOS the Ignored devices page SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared page shows, in the same words: each ignored device by its maker and model, with the end of its serial or that it may match other units, and Un-ignore; a failed un-ignore above the devices; and the empty page's explanation. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Two ignored devices
- **WHEN** alice has ignored a Garmin Edge 840 whose serial ends 2071 and a Polar H10 known only by its model
- **THEN** the page lists "Garmin Edge 840" with "Serial …2071" and "Polar H10" with "Known only by its model, so it may match other units", each with Un-ignore

#### Scenario: Un-ignore read aloud
- **WHEN** VoiceOver reads the Edge's Un-ignore
- **THEN** it reads "Un-ignore Garmin Edge 840"

#### Scenario: Nothing ignored
- **WHEN** alice has ignored no device
- **THEN** the page reads "No ignored devices. Devices you mark as not gear when importing are listed here."

#### Scenario: A failed un-ignore
- **WHEN** un-ignoring fails because the server can't be reached
- **THEN** the failure shows above the devices and the devices stay

### Requirement: The iOS gear pages open full width
On iOS a gear collection's page and the Ignored devices page SHALL open full width over Settings, alone or beside the list, and back from either SHALL return to the page it was opened from. What they open SHALL open as it does from the shared pages.

#### Scenario: A collection from Gear beside the list
- **WHEN** on an iPad alice opens "Dive kit" from Gear beside Settings' list
- **THEN** the collection takes the whole window, and back returns to Settings with Gear beside the list

#### Scenario: An item from a collection, and back
- **WHEN** alice opens the regulator from "Dive kit" and goes back
- **THEN** she is on "Dive kit" again
