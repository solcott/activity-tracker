# ui/settings-layout Specification

## Purpose
How Settings is organized and navigated: a grouped list of rows that opens a page for each larger setting, shown as a list beside a detail pane on wide windows and one page at a time on narrow ones.

## Requirements

### Requirement: Settings is a grouped list
Settings SHALL be a list of titled sections, in order: Account (one row, the signed-in handle, opening the Account page), General (units), Privacy & sharing (Activity defaults, Privacy zones, Segments & groups), Training (Training zones, Gear), Safety (Blocked accounts, Muted accounts), then Moderation where offered. Sign out SHALL NOT be on the list; it is on the Account page. Each row that opens a page SHALL be drawn as a navigation row, not a button.

#### Scenario: Sections in order
- **WHEN** an athlete who isn't a moderator opens Settings
- **THEN** it shows Account, General, Privacy & sharing, Training and Safety in that order, with no Moderation row and no Sign out row

#### Scenario: Moderator on desktop
- **WHEN** a moderator opens Settings on desktop or web
- **THEN** a Moderation row shows after Safety, last in the list

#### Scenario: Account row
- **WHEN** alice, signed in as `alice.test`, opens Settings
- **THEN** the first section is Account, with one row reading `alice.test` that opens the Account page

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

### Requirement: Account row flags a sign-in that needs renewing
The Account row SHALL show a short note when the Account page would offer Sign in again, and none otherwise.

#### Scenario: Out-of-date sign-in
- **WHEN** bob's session has no athlete-space scope and he opens Settings
- **THEN** the Account row shows that he needs to sign in again

#### Scenario: Current sign-in
- **WHEN** alice's sign-in is up to date
- **THEN** the Account row shows only her handle

### Requirement: The Account page opens in the detail pane
The Account page SHALL be a Settings page: on a wide window it opens in the detail pane with the Account row marked, and on a narrow one it replaces Settings with back returning to the list.

#### Scenario: Account on desktop
- **WHEN** on a wide desktop window an athlete chooses the Account row
- **THEN** the Account page shows in the detail pane without a back button, and the Account row is marked

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

### Requirement: The Gear list is native on iOS
On iOS the Gear page SHALL be drawn with the system's own grouped list and navigation bar, and SHALL show what the shared page shows, in the same words and figures: the athlete's collections, then their active items by type, then their retired items, each item with its photo, name, kind and visibility and its one usage total; Add and Ignored devices in the page's bar; and the empty page's message. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Mixed gear
- **WHEN** alice, who rides in metric, opens Gear with a gravel bike used for 3,200 km, new trail shoes she alone sees, and skis with 14 days and 41,000 m of vertical
- **THEN** the bike reads "Bike · Public" and "3,200 km" under Bike, the shoes "Shoes · Only me" and "No activities yet", and the skis "14 days · 41,000 m"

#### Scenario: No collections yet
- **WHEN** alice has gear but no collection
- **THEN** the Collections section reads "Gear you use together. Only you see your collections." and offers New collection

#### Scenario: No gear at all
- **WHEN** alice has no gear and no collection
- **THEN** the page reads "You haven't added any gear yet." with Add gear, and shows no Collections section

#### Scenario: Opening an item
- **WHEN** alice taps an item, a collection, Add, New collection or Ignored devices
- **THEN** that screen opens as it does from the shared page

### Requirement: The blocked and muted lists are native on iOS
On iOS the Blocked accounts and Muted accounts pages SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared pages show, in the same words: each account with its avatar, name and handle, Unblock or Unmute where this app can undo it, and the Bluesky note where it can't; a failed unblock or unmute above the accounts; and the list's empty message. They SHALL load page by page as `ui/content-loading` says.

#### Scenario: Unblocking
- **WHEN** alice taps Unblock beside bob
- **THEN** that button is unavailable while the unblock is written, and bob leaves the list

#### Scenario: A block made on Bluesky
- **WHEN** alice's blocked list holds an account she blocked only on Bluesky
- **THEN** it reads "Blocked on Bluesky. Unblock them there." under the account, with no Unblock

#### Scenario: A failed unblock
- **WHEN** unblocking fails because the server can't be reached
- **THEN** the failure shows above the accounts and the accounts stay

#### Scenario: Nobody muted
- **WHEN** alice opens Muted accounts and has muted no one
- **THEN** the page reads "You haven't muted anyone."

#### Scenario: A long list
- **WHEN** alice scrolls to the end of the accounts loaded so far and more remain
- **THEN** the next page is asked for, with progress below the accounts until it comes

### Requirement: What the iOS lists open opens full width
On iOS a screen opened from the Gear, Blocked accounts or Muted accounts page that isn't a settings page (an item, a collection, a new item or collection, Ignored devices, a profile) SHALL open full width over Settings, alone or beside the list, and back from it SHALL return to the page it was opened from.

#### Scenario: A profile from the blocked list beside the list
- **WHEN** on an iPad alice opens bob's profile from Blocked accounts beside Settings' list
- **THEN** the profile takes the whole window, and back returns to Settings with Blocked accounts beside the list

#### Scenario: An item from the gear list beside the list
- **WHEN** on an iPad alice opens her gravel bike from Gear beside Settings' list
- **THEN** the item takes the whole window, and back returns to Settings with Gear beside the list
