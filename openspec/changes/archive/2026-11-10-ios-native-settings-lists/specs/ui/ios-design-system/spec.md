# Spec Delta

## ADDED Requirements

### Requirement: Pictures from the network show with a placeholder
A native screen SHALL show a photo or an avatar from its URL filling its frame, with a placeholder in its place until it loads, when it fails, and when there is none. A photo this device uploaded and holds for the session (a `heldphoto:` URL) SHALL show from those bytes, as on the other platforms. An avatar SHALL show the athlete's initial until its picture loads.

#### Scenario: An item with no photo
- **WHEN** a gear item has no photo
- **THEN** its row shows the placeholder in the photo's place

#### Scenario: A non-public photo
- **WHEN** alice's gear item visible only to her has a photo the session holds
- **THEN** its row shows that photo

#### Scenario: An athlete without a picture
- **WHEN** an account named Bob has no avatar
- **THEN** its avatar shows "B"

### Requirement: A paged list follows the loading rules natively
A native screen that shows a paged list SHALL follow `ui/content-loading` as the shared UI's paged list does: progress or the first page's failure with Retry until the first answer; once answered, a refresh's progress or failure above the items, the empty message when the list holds nothing, and the next page's progress or failure with Retry below the items. It SHALL ask for the next page once the end of the items is on screen and more can be loaded, and not before.

#### Scenario: A failed next page
- **WHEN** a list's next page fails because the server can't be reached
- **THEN** the loaded items stay, and below them it reads that it couldn't reach the server, with Retry

#### Scenario: The end not yet on screen
- **WHEN** a list has loaded more items than fit on screen, and more remain
- **THEN** no further page is asked for until the last loaded item is scrolled onto the screen

### Requirement: A page's actions sit beside its title
A native page's actions (such as Add) SHALL show at the trailing end of the stack's bar when the page is alone, and at the trailing end of its column header when it is drawn beside another screen.

#### Scenario: Gear beside the list
- **WHEN** Gear is drawn beside Settings' list
- **THEN** its header reads "Gear" with Add and More at its trailing end
