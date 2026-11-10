# Spec Delta

## ADDED Requirements

### Requirement: The host says when Settings can show beside its page
For each stack, the host SHALL tell a UI that has reported a size with room for two panes when the top of the stack is Settings, alone or with only settings pages above it: which screen is Settings, the pages above it in the order they were opened, and the page whose row the list marks. With any other screen on top, or for a UI that hasn't reported such a size, it SHALL report nothing. Room for two panes SHALL be at least 700 points of width and 500 of height.

#### Scenario: A page open
- **WHEN** a wide UI's stack is a profile, Settings and Privacy zones
- **THEN** the host reports Settings with Privacy zones beside it, and Privacy zones as the marked page

#### Scenario: A deeper page
- **WHEN** a wide UI's stack is a profile, Settings, Training zones and a sport's zones
- **THEN** the host reports Settings with Training zones and the sport's zones beside it in that order, and Training zones as the marked page

#### Scenario: Another screen on top
- **WHEN** a wide UI's stack is a profile, Settings, Blocked accounts and another athlete's profile
- **THEN** the host reports nothing, and the UI shows the profile over the rest

#### Scenario: A narrow UI
- **WHEN** a UI that reported 699 points of width has a stack of a profile, Settings and Privacy zones
- **THEN** the host reports nothing

#### Scenario: A wide UI that is short
- **WHEN** a UI that reported 956 points of width and 440 of height has a stack of a profile, Settings and Privacy zones
- **THEN** the host reports nothing

#### Scenario: A UI turned on its side
- **WHEN** a UI showing Settings beside Privacy zones at 834 by 1,210 points reports 1,210 by 834
- **THEN** the host still reports Settings with Privacy zones beside it

### Requirement: A UI's size never changes its stack
The host SHALL hold the same screens in a stack whatever size the UI reports: laying Settings out beside its page, or one screen at a time, SHALL be only a way of showing the stack.

#### Scenario: Size doesn't change the stack
- **WHEN** a UI showing Settings beside Privacy zones reports a narrower width
- **THEN** the stack is still the profile, Settings and Privacy zones, and the host reports nothing to show beside Settings

### Requirement: The page shown before a row is chosen is not on the stack
While Settings is on top of a wide UI's stack with no page above it, the host SHALL offer Activity defaults as the page to show beside it, without putting it on the stack: going back from Settings SHALL leave Settings. Its presenter SHALL run while the UI reports it visible, and what it retained SHALL be kept for as long as that Settings screen is on the stack and dropped when it leaves.

#### Scenario: Opening Settings wide
- **WHEN** a wide UI opens Settings from a profile
- **THEN** the stack is the profile and Settings, the host offers Activity defaults beside it as the marked page, and Activity defaults publishes its state once the UI reports it visible

#### Scenario: Choosing a row
- **WHEN** an athlete then chooses Gear
- **THEN** the stack is the profile, Settings and Gear, and the host reports Gear beside Settings in place of Activity defaults

#### Scenario: Leaving Settings
- **WHEN** Settings is popped and opened again
- **THEN** the Activity defaults offered beside it starts with nothing retained from the first visit

### Requirement: A pane's first page knows it has no way back
The host SHALL tell the first page shown beside Settings that the list is beside it, and every other screen that it isn't, so a UI that draws the page gives it no back button there and gives it one when the same page is shown on its own.

#### Scenario: The same page, wide then narrow
- **WHEN** Privacy zones is shown beside the list and the UI then reports a narrow width
- **THEN** the page is told first that it is a pane's first page and then that it isn't
