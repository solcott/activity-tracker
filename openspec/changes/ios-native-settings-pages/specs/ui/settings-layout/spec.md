# Spec Delta

## ADDED Requirements

### Requirement: Activity defaults is native on iOS
On iOS the Activity defaults page SHALL be drawn with the system's own form, pickers and navigation bar, and SHALL offer what the shared page offers: who new activities are shown to, each detail category's default with its help, the Followers note, and the note under the choices. For the same state it SHALL say what the shared page says, in the same words.

#### Scenario: A category capped by the activity
- **WHEN** alice's defaults are Followers for new activities and she opens a detail category's choice
- **THEN** it offers Followers, Mutuals and Only me, and not Public

#### Scenario: Followers without approval
- **WHEN** alice, who doesn't approve followers, has Followers as a default
- **THEN** the page reads "Anyone who follows you can see what you share with Followers. Turn on Approve followers in your profile to choose who."

#### Scenario: A session that can't choose
- **WHEN** alice's session was signed in before it could reach her spaces
- **THEN** every choice is dimmed, shows Public, and the note reads "Sign in again to choose who sees your activities. Until then they're Public."

#### Scenario: Saving
- **WHEN** alice changes a default
- **THEN** the choices can't be changed while it saves, the note reads "Saving…", and then the help again

#### Scenario: A failed save
- **WHEN** saving a default fails
- **THEN** the page shows the shared failure text under the note

### Requirement: Segments & groups is native on iOS
On iOS the Segments & groups page SHALL be drawn with the system's own form, switches, segmented choice and navigation bar, and SHALL offer each choice under the same conditions the shared page does, with the same notes, in the same words.

#### Scenario: Leaderboards follow matching
- **WHEN** alice turns segment matching off
- **THEN** the leaderboard and bracket-board switches are no longer shown

#### Scenario: A grouping choice never made
- **WHEN** alice has never been asked whether to group her activities
- **THEN** the grouping choice shows Off

#### Scenario: A session that can't use them
- **WHEN** alice's session was signed in before it could use segments and grouping
- **THEN** both choices are dimmed and their notes read "Sign in again to use segments." and "Sign in again to use group activities."

#### Scenario: Saving a switch
- **WHEN** alice turns on the bracket boards
- **THEN** the segment switches can't be changed while it saves and the segments note reads "Saving…"

### Requirement: A native page beside the iOS list shows its own title
On a wide iOS window a settings page drawn natively SHALL show its title at the top of the detail pane, under the bar that spans both columns, with no back button, as a page the shared UI draws there does. Alone it SHALL show its title in the stack's own bar, with the stack's back button.

#### Scenario: Activity defaults beside the list
- **WHEN** alice opens Settings on an iPad held upright
- **THEN** "Activity defaults" heads the detail pane with no back button, the bar over both columns keeps "Settings" over the list, and the list marks Activity defaults

#### Scenario: Segments & groups alone
- **WHEN** alice taps Segments & groups on an iPhone
- **THEN** it replaces the list with "Segments & groups" as the bar's title and the system's back button, which returns to the list
