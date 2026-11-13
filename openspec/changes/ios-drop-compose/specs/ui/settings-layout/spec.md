## ADDED Requirements

### Requirement: A settings page shows natively beside the iOS list
On a wide iOS window a settings page SHALL show in the detail pane beside the native list, drawn natively, with the same content and actions it has full width.

#### Scenario: Privacy zones beside the list
- **WHEN** on a wide iOS window an athlete chooses Privacy zones
- **THEN** Privacy zones shows beside the list with its title and no back button, and the list marks Privacy zones

#### Scenario: A deeper page
- **WHEN** on a wide iOS window an athlete opens Training zones and then a sport's zones
- **THEN** the sport's zones show in the detail pane with a back button that returns to Training zones, and the list still marks Training zones

## REMOVED Requirements

### Requirement: A page shows beside the iOS list whichever UI draws it
**Reason**: No settings page is drawn by the shared UI on iOS any more, so there is no second kind of page to place beside the list.
**Migration**: "A settings page shows natively beside the iOS list" holds the same behaviour for the native pages.
