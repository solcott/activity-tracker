# Spec Delta

## ADDED Requirements

### Requirement: iOS shows one settings page at a time
On iOS, Settings SHALL show one page at a time at every width, as a narrow window does: choosing a row opens its page in place of the list, and back returns to the list. This takes precedence on iOS over the requirements for wide windows, which hold on Android, desktop and web.

#### Scenario: iPad in landscape
- **WHEN** an athlete on an iPad in landscape opens Settings and taps Privacy zones
- **THEN** Privacy zones replaces the Settings list, with a back button that returns to the list

#### Scenario: Opening Settings on iPad
- **WHEN** an athlete on an iPad opens Settings
- **THEN** the list fills the screen and no page shows beside it

#### Scenario: A wide desktop window
- **WHEN** an athlete opens Settings on a desktop window at least 840dp wide
- **THEN** the list shows beside Activity defaults, as before
