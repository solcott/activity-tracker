# ui/settings-layout Specification

## Purpose
How Settings is organized and navigated: a grouped list of rows that opens a page for each larger setting, shown as a list beside a detail pane on wide windows and one page at a time on narrow ones.

## Requirements

### Requirement: Settings is a grouped list
Settings SHALL be a list of titled sections, in order: General (units), Privacy & sharing (Activity defaults, Privacy zones, Segments & groups), Training (Training zones, Gear), Safety (Blocked accounts, Muted accounts), then Moderation where offered, then Sign out. Each row that opens a page SHALL be drawn as a navigation row, not a button.

#### Scenario: Sections in order
- **WHEN** an athlete who isn't a moderator opens Settings
- **THEN** it shows General, Privacy & sharing, Training and Safety in that order, then Sign out, with no Moderation row

#### Scenario: Moderator on desktop
- **WHEN** a moderator opens Settings on desktop or web
- **THEN** a Moderation row shows after Safety and before Sign out

### Requirement: Units are chosen on the list
The units choice SHALL stay on the Settings list itself, under General, with the same immediate save and its example line.

#### Scenario: Switching units
- **WHEN** an athlete chooses Imperial under General
- **THEN** the choice saves without opening a page, and the example line shows imperial units

### Requirement: Rows summarize their page
The Activity defaults row SHALL show the default activity visibility. The Privacy zones row SHALL show the zone count and whether route ends are hidden. The Segments & groups row SHALL show whether segment matching is on. A summary that hasn't been read yet SHALL be left out, never guessed.

#### Scenario: Privacy zones summary
- **WHEN** an athlete with two privacy zones and hidden route ends opens Settings
- **THEN** the Privacy zones row reads that they have 2 zones with route ends hidden

#### Scenario: Summary not read yet
- **WHEN** the segment preferences haven't been read
- **THEN** the Segments & groups row shows no on/off summary

### Requirement: Activity defaults page
An Activity defaults page SHALL hold the default activity visibility and each detail category's default, with the same choices, caps, locked note, saving and failure reporting that `feed/activity-visibility` and `feed/detail-visibility` require. A failure SHALL be reported on that page.

#### Scenario: Changing a category default
- **WHEN** an athlete opens Activity defaults and sets Heart rate to Only me
- **THEN** the default saves and the page shows Heart rate as Only me

#### Scenario: Failed save
- **WHEN** saving a default fails
- **THEN** the Activity defaults page reports the failure and shows the earlier choice

### Requirement: Segments & groups page
A Segments & groups page SHALL hold segment matching, the leaderboard and bracket-board choices and the activity grouping choice. Each SHALL keep the conditions for being offered, its sign-in-again note, and the save and failure behaviour its own capability requires. A failure SHALL be reported on that page.

#### Scenario: Leaderboard choice follows matching
- **WHEN** an athlete turns segment matching on in Segments & groups
- **THEN** the page offers the leaderboard choice beneath it

#### Scenario: Grouping on the same page
- **WHEN** an athlete chooses Connected on Segments & groups
- **THEN** the grouping choice saves and shows Connected

### Requirement: Narrow windows show one page at a time
When less than 840dp of width is available to the screen, choosing a row SHALL open its page in place of Settings, and back SHALL return to Settings, with the platform's back gesture.

#### Scenario: Phone
- **WHEN** an athlete on a phone taps Activity defaults
- **THEN** the page replaces Settings, and back returns to the Settings list

### Requirement: Wide windows show list and detail side by side
With at least 840dp of width available, Settings SHALL show its list beside a detail pane, and choosing a row SHALL replace the detail pane's page rather than stacking on it. The list SHALL mark the row whose page is shown.

#### Scenario: Choosing rows in turn
- **WHEN** on desktop an athlete chooses Privacy zones and then Gear
- **THEN** the detail pane shows Gear, the list marks Gear, and back leaves Settings instead of returning to Privacy zones

#### Scenario: Settings' back with a page open
- **WHEN** on a wide window an athlete with Gear open beside the list taps the list's back button
- **THEN** Settings closes, page and all, and the screen it was opened from shows

### Requirement: Activity defaults shows until a row is chosen
On a wide window, the detail pane SHALL show Activity defaults while no row has been chosen, and that SHALL NOT count as a page opened: back from Settings SHALL leave Settings.

#### Scenario: Opening Settings wide
- **WHEN** an athlete opens Settings on a wide desktop window
- **THEN** the detail pane shows Activity defaults, its row is marked, and back returns to the screen Settings was opened from

### Requirement: Only settings pages open in the detail pane
Settings' pages and the pages they lead to within settings (such as a sport's zones, threshold history, gear list, blocked and muted lists) SHALL open in the detail pane. Any other screen opened from them SHALL open full width, and back from it SHALL return to the list-detail layout.

#### Scenario: Sub-page in the pane
- **WHEN** on a wide window an athlete opens Training zones and then a sport's zones
- **THEN** the sport's zones show in the detail pane with a back button that returns to Training zones

#### Scenario: Profile from the blocked list
- **WHEN** on a wide window an athlete opens a profile from Blocked accounts
- **THEN** the profile opens full width, and back returns to Settings with Blocked accounts in the detail pane

#### Scenario: Moderation is full width
- **WHEN** a moderator chooses Moderation on a wide window
- **THEN** Moderation opens full width

### Requirement: The detail page's first level has no back button
On a wide window, the page shown first in the detail pane SHALL have no back button, since the list stays visible beside it. Deeper pages in the pane SHALL have one.

#### Scenario: First page in the pane
- **WHEN** a wide window shows Segments & groups in the detail pane
- **THEN** its top bar shows no back button

### Requirement: Resizing keeps the open page
Crossing 840dp while Settings is open SHALL keep the page that was open and the list's scroll position, without reloading the list.

#### Scenario: Narrowing with a page open
- **WHEN** a desktop window showing Settings and Privacy zones is narrowed below 840dp
- **THEN** Privacy zones fills the window, and back returns to the Settings list

#### Scenario: Widening from the list
- **WHEN** a window showing only the Settings list is widened past 840dp
- **THEN** the detail pane shows Activity defaults and the list keeps its scroll position
