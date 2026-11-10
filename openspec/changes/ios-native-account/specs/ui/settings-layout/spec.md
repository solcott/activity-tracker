## MODIFIED Requirements

### Requirement: A native page beside the iOS list shows its own title
On a wide iOS window a settings page drawn natively SHALL show its title at the top of the detail pane, under the bar that spans both columns, with no back button when it is the first page beside the list, as a page the shared UI draws there does. A deeper native page there SHALL show a back button beside its title that returns to the page it was opened from. Alone a page SHALL show its title in the stack's own bar, with the stack's back button.

#### Scenario: Activity defaults beside the list
- **WHEN** alice opens Settings on an iPad held upright
- **THEN** "Activity defaults" heads the detail pane with no back button, the bar over both columns keeps "Settings" over the list, and the list marks Activity defaults

#### Scenario: Segments & groups alone
- **WHEN** alice taps Segments & groups on an iPhone
- **THEN** it replaces the list with "Segments & groups" as the bar's title and the system's back button, which returns to the list

#### Scenario: Leave beside the list
- **WHEN** alice, with Account open beside the list on an iPad, taps Leave Activity Tracker
- **THEN** "Leave Activity Tracker" heads the detail pane with a back button, the list still marks Account, and the back button returns to Account

## ADDED Requirements

### Requirement: The Account page is native on iOS
On iOS the Account page SHALL be drawn with the system's own grouped list, alerts and navigation bar. It SHALL show what the shared page shows, in the same words: the account and its server, the sign-in's status, where the account is managed, the data export, Sign out and Leave Activity Tracker. Ending the session with work pending SHALL ask first in a system alert.

#### Scenario: Copying the DID
- **WHEN** alice taps Copy beside her DID
- **THEN** the DID is on the clipboard and the button reads "DID copied"

#### Scenario: An outdated sign-in
- **WHEN** alice signed in before the app asked for gear and leaving
- **THEN** the Sign-in section reads "You signed in before Activity Tracker asked for everything it now needs. Sign in again to use:", lists "Private gear, gear defaults and collections" and "Leaving Activity Tracker" in that order, and offers Sign in again

#### Scenario: Signing out with changes pending
- **WHEN** alice taps Sign out with 2 changes not uploaded
- **THEN** an alert titled "Sign out?" says the 2 changes stay on this device, with "Sign out anyway" as a destructive action and Cancel

#### Scenario: An export downloading files
- **WHEN** alice's export has fetched 3 of 10 files
- **THEN** the Your data section shows progress reading "Downloading files: 3 of 10" and Cancel export

#### Scenario: The session ending
- **WHEN** alice has confirmed signing out and the session is ending
- **THEN** Sign out, Sign in again and Leave Activity Tracker can't be tapped

### Requirement: Saving an export on iOS goes through the share sheet natively
On iOS, Save on the native Account page SHALL write the archive to a file without holding it in memory, then offer it in the system's share sheet. Once an activity of the sheet has run, the page SHALL ask whether the athlete kept it. When the sheet is dismissed, the export SHALL stay. When writing fails, the page SHALL show the shared failure text.

#### Scenario: Shared and kept
- **WHEN** alice saves her ready export to Files from the share sheet
- **THEN** after the sheet closes an alert asks "Did you keep the export?", and "Delete the app's copy" deletes it

#### Scenario: Shared and not kept
- **WHEN** alice answers "Keep it"
- **THEN** the export stays ready to save again

#### Scenario: Dismissed
- **WHEN** alice closes the share sheet without choosing anything
- **THEN** no question is asked and the export stays ready

### Requirement: Leave Activity Tracker is native on iOS
On iOS the Leave page SHALL be drawn with the system's own grouped list, text field and navigation bar. Before the athlete confirms, it SHALL show what the shared page shows, in the same words: what is deleted and what stays, the shared places, what this device would lose, the export, the grant and the typed-handle confirmation. Leave SHALL be on only once the typed handle names the account.

#### Scenario: Typing the handle
- **WHEN** alice types "@Alice.test" under "Type alice.test to confirm"
- **THEN** Leave Activity Tracker can be tapped

#### Scenario: A different handle
- **WHEN** alice types "alice"
- **THEN** Leave Activity Tracker can't be tapped

#### Scenario: Shared places
- **WHEN** alice has 2 segments others use and nothing else
- **THEN** the page reads "2 segments others use stay, without your name, keeping their names."

#### Scenario: An export running
- **WHEN** alice's export is running
- **THEN** Leave Activity Tracker can't be tapped, and the export offers Cancel export

### Requirement: A leave's progress is native on iOS
On iOS, once a leave runs, the native Leave page SHALL show only its progress: the step, how many records were deleted, until when the server asked it to wait, and, when it stopped, why, with Retry. The page SHALL offer no way back while the leave runs, whether it was opened from Account or shown in place of the app while a leave resumes.

#### Scenario: Deleting private data
- **WHEN** alice's leave is deleting her spaces and has deleted 12 records
- **THEN** the page shows progress reading "Deleting your private data…" and "12 records deleted", with no back button

#### Scenario: Paused by the server
- **WHEN** alice's server asked the leave to wait until 7:05 on a device that uses a 12-hour clock
- **THEN** the page reads "Your server asked to slow down. Carrying on at 7:05 AM."

#### Scenario: Stopped
- **WHEN** alice's leave was refused by her server
- **THEN** the page shows the refusal and Retry, which runs it again from where it stopped
