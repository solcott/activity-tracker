# Spec Delta

## ADDED Requirements

### Requirement: The iOS app turns with the device
The iOS app SHALL show upright and on either side on an iPhone, and any way up on an iPad, turning with the device on every screen, whether a native view or the shared UI draws it. Turning SHALL keep each tab's stack, the screen on top and what it shows.

#### Scenario: An iPhone turned on the feed
- **WHEN** an athlete turns an iPhone showing the feed onto its side
- **THEN** the feed and the tabs lay out across the wider screen, at the same place in the feed

#### Scenario: A screen the shared UI draws
- **WHEN** an athlete turns an iPhone showing an activity, which the shared UI draws, onto its side
- **THEN** the activity lays out across the wider screen, and back returns to the screen it was opened from

#### Scenario: An iPad upside down
- **WHEN** an athlete turns an iPad upside down
- **THEN** the app turns to stay upright for them

#### Scenario: An iPhone upside down
- **WHEN** an athlete turns an iPhone upside down
- **THEN** the app stays as it was
