# Spec Delta

## ADDED Requirements

### Requirement: The Settings list is native on iOS
On iOS the Settings list SHALL be drawn with the system's own grouped list, navigation bar and controls, and SHALL offer what the shared list offers: the sections in the same order, the units choice saved in place, and a row per page with its summary. For the same state it SHALL say what the shared list says, in the same words. It SHALL NOT offer Moderation, on an iPhone or an iPad.

#### Scenario: Summaries
- **WHEN** alice, with one privacy zone, hidden route ends, Followers as her default visibility and segment matching on, opens Settings on an iPhone
- **THEN** the rows read "Followers", "1 zone, start and end hidden" and "Matching on", as the shared list does

#### Scenario: A summary not read yet
- **WHEN** the privacy zones haven't been read
- **THEN** the Privacy zones row shows its title and no summary

#### Scenario: Switching units
- **WHEN** an athlete chooses Imperial under General
- **THEN** the choice can't be changed again while it saves, the line under it reads "Saving…", and then "Miles, feet, °F"

#### Scenario: A moderator on an iPad
- **WHEN** a moderator opens Settings on an iPad
- **THEN** the list ends with Safety and shows no Moderation row

### Requirement: iOS decides wide and narrow by the window's size in points
On iOS, Settings SHALL show its list beside its page when the app's window gives the screen at least 700 points of width and 500 of height, and one page at a time otherwise. On iOS this SHALL stand in place of the 840 the other platforms use. The device's kind and which way up it is held SHALL NOT decide it.

#### Scenario: An 11-inch iPad upright
- **WHEN** an athlete opens Settings on an iPad whose window is 834 points wide and 1,210 tall
- **THEN** the list shows beside Activity defaults, whose row is marked

#### Scenario: An iPad mini upright
- **WHEN** an athlete opens Settings on an iPad whose window is 744 points wide and 1,133 tall, and taps Privacy zones
- **THEN** Privacy zones shows beside the list, which marks Privacy zones

#### Scenario: An iPad on its side
- **WHEN** an athlete opens Settings on an iPad whose window is 1,210 points wide and 834 tall
- **THEN** the list shows beside Activity defaults

#### Scenario: An iPad sharing its screen
- **WHEN** an athlete opens Settings in a window 605 points wide beside another app, and taps Privacy zones
- **THEN** Privacy zones replaces the list, and back returns to the list

#### Scenario: An iPhone upright
- **WHEN** an athlete opens Settings on an iPhone held upright
- **THEN** the list fills the screen and no page shows beside it

#### Scenario: An iPhone on its side
- **WHEN** an athlete opens Settings on an iPhone on its side, whose window is 956 points wide and 440 tall, and taps Gear
- **THEN** Gear replaces the list, and back returns to the list

### Requirement: A page shows beside the iOS list whichever UI draws it
On a wide iOS window a settings page SHALL show in the detail pane beside the native list whether the page is drawn natively or by the shared UI, with the same content and actions it has full width.

#### Scenario: A page the shared UI draws
- **WHEN** on a wide iOS window an athlete chooses Privacy zones, which the shared UI draws
- **THEN** Privacy zones shows beside the list with its title and no back button, and the list marks Privacy zones

#### Scenario: A deeper page
- **WHEN** on a wide iOS window an athlete opens Training zones and then a sport's zones
- **THEN** the sport's zones show in the detail pane with a back button that returns to Training zones, and the list still marks Training zones

### Requirement: Back beside a page on iOS leaves Settings
On a wide iOS window Settings' back button, over its list, and a swipe from the window's leading edge SHALL both leave Settings, page and all. A deeper page in the detail pane SHALL go back with its own back button.

#### Scenario: Swiping back with a page open
- **WHEN** on a wide iOS window an athlete with Gear open beside the list swipes from the leading edge
- **THEN** Settings closes, page and all, and the screen it was opened from shows

#### Scenario: Settings' back button
- **WHEN** on a wide iOS window an athlete with a sport's zones open in the detail pane taps Settings' back button
- **THEN** Settings closes and the screen it was opened from shows

### Requirement: Resizing or turning an iOS window keeps the list and the page
Changing an iOS window's size while Settings is open, by resizing it or turning the device, SHALL keep the native list as it was, at its scroll position and with its summaries, and the open page as it was, without either loading again, whether or not the change moves Settings between one page at a time and the list beside its page.

#### Scenario: An iPad window widened with a page open
- **WHEN** an iPad window showing Privacy zones, scrolled part way, is widened from 605 points to 834
- **THEN** Privacy zones shows beside the list at the same scroll position, and the list shows its summaries at once with no loading

#### Scenario: An iPad window narrowed from the list
- **WHEN** an iPad window showing the list, scrolled to Safety, beside Activity defaults is narrowed below 700 points
- **THEN** the list fills the window, still scrolled to Safety

#### Scenario: An iPad turned with a page open
- **WHEN** an iPad showing Gear beside the list is turned onto its side
- **THEN** Gear still shows beside the list, and neither loads again

#### Scenario: An iPhone turned with a page open
- **WHEN** an iPhone showing Privacy zones is turned onto its side and back
- **THEN** Privacy zones fills the screen throughout, and back returns to the list

## REMOVED Requirements

### Requirement: iOS shows one settings page at a time
**Reason**: It was a stopgap for the SwiftUI shell, which couldn't run the shared UI's list-detail layout. Settings' list is now native and lays itself out beside its page.
**Migration**: None. On iOS the requirements for wide and narrow windows now hold on iOS too, with iOS deciding wide and narrow by the window's size in points.
