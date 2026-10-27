## ADDED Requirements

### Requirement: Moderator photo links cover the subject's gear
When a `getSubject` answer for a non-public activity lists gear items, their photo links SHALL work for the moderator it was signed for, at any visibility of the item, within the same limits as the activity's own photo links. A moderator's link to the photo of a gear item that no activity they opened through `getSubject` lists SHALL be refused, as for any other viewer the item's visibility doesn't admit.

#### Scenario: Gear photo on a reported activity
- **WHEN** a moderator opens bob's reported Followers activity, which lists his Followers bike with a photo, and bob blocks the moderator
- **THEN** the bike's photo link in the answer works for the moderator

#### Scenario: Gear photo link expires
- **WHEN** that gear photo link is more than 30 minutes old
- **THEN** the AppView refuses it

#### Scenario: Gear the subject doesn't list
- **WHEN** a moderator who opened bob's reported activity requests a signed link to the photo of bob's Only me shoes, which that activity doesn't list
- **THEN** the AppView refuses it
