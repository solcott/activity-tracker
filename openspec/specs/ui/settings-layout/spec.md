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

### Requirement: A gear collection is native on iOS
On iOS a gear collection's page SHALL be drawn with the system's own grouped list and navigation bar, and SHALL show what the shared page shows, in the same words: the collection's name as its title, its notes, and its items in the athlete's order, each with its photo, name, kind, who may see it and Retired where it is; the empty collection's message; and a failed delete above the items. Its loading SHALL follow `ui/content-loading`.

#### Scenario: A collection with a retired item
- **WHEN** alice opens her collection "Dive kit", noted "Cold water setup", holding a regulator and fins everyone may see and a retired fullsuit she alone sees
- **THEN** the page is titled "Dive kit", reads "Cold water setup", and lists the regulator as "Regulator · Public", the fullsuit as "Fullsuit · Only me · Retired" and the fins as "Fins · Public", in that order

#### Scenario: An empty collection
- **WHEN** alice opens a collection that holds no items
- **THEN** the page reads "No items yet"

#### Scenario: Before it loads
- **WHEN** alice opens a collection that hasn't loaded yet
- **THEN** the page is titled "Collections", shows its progress, and offers no Edit or Delete

#### Scenario: Opening an item
- **WHEN** alice taps the regulator in "Dive kit"
- **THEN** the regulator's gear page opens, as from the shared page

### Requirement: Editing and deleting a collection on iOS
On iOS a loaded collection's page SHALL offer Edit and Delete in its bar, as the shared page does. Delete SHALL ask first in the system's alert, in the shared page's words, and only its confirmation SHALL delete. While the collection is being deleted Edit and Delete SHALL be unavailable; once deleted the page SHALL close.

#### Scenario: Asked before deleting
- **WHEN** alice taps Delete on "Dive kit"
- **THEN** an alert asks "Delete this collection?", says "Its items, and the gear on your activities, stay as they are.", and offers Delete collection and Cancel

#### Scenario: Cancelled
- **WHEN** alice taps Cancel in that alert
- **THEN** the alert closes and the collection stays

#### Scenario: A failed delete
- **WHEN** deleting fails because the server can't be reached
- **THEN** the failure shows above the items, the items stay, and Edit and Delete are available again

### Requirement: Ignored devices is native on iOS
On iOS the Ignored devices page SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared page shows, in the same words: each ignored device by its maker and model, with the end of its serial or that it may match other units, and Un-ignore; a failed un-ignore above the devices; and the empty page's explanation. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Two ignored devices
- **WHEN** alice has ignored a Garmin Edge 840 whose serial ends 2071 and a Polar H10 known only by its model
- **THEN** the page lists "Garmin Edge 840" with "Serial …2071" and "Polar H10" with "Known only by its model, so it may match other units", each with Un-ignore

#### Scenario: Un-ignore read aloud
- **WHEN** VoiceOver reads the Edge's Un-ignore
- **THEN** it reads "Un-ignore Garmin Edge 840"

#### Scenario: Nothing ignored
- **WHEN** alice has ignored no device
- **THEN** the page reads "No ignored devices. Devices you mark as not gear when importing are listed here."

#### Scenario: A failed un-ignore
- **WHEN** un-ignoring fails because the server can't be reached
- **THEN** the failure shows above the devices and the devices stay

### Requirement: The iOS gear pages open full width
On iOS a gear collection's page and the Ignored devices page SHALL open full width over Settings, alone or beside the list, and back from either SHALL return to the page it was opened from. What they open SHALL open as it does from the shared pages.

#### Scenario: A collection from Gear beside the list
- **WHEN** on an iPad alice opens "Dive kit" from Gear beside Settings' list
- **THEN** the collection takes the whole window, and back returns to Settings with Gear beside the list

#### Scenario: An item from a collection, and back
- **WHEN** alice opens the regulator from "Dive kit" and goes back
- **THEN** she is on "Dive kit" again

### Requirement: The gear editor is native on iOS
On iOS the gear item form SHALL be drawn with the system's own grouped form and navigation bar, and SHALL offer the shared form's fields in its words and limits: the name, required; the type and, when it has kinds, the kind; brand and model; the weight in whole grams; the starting distance in the athlete's units and the starting time; and the notes. Save SHALL be in the bar. Its loading SHALL follow `ui/content-loading`.

#### Scenario: A new item
- **WHEN** alice opens Add gear from Gear
- **THEN** the form is titled "Add gear", its name is empty, and it starts at Only me

#### Scenario: Saving without a name
- **WHEN** alice taps Save with the name empty
- **THEN** nothing is saved, and the name field reads "Give your gear a name" in the error colour

#### Scenario: A kind on one line
- **WHEN** alice edits her gravel bike, a bike whose kind is Gravel
- **THEN** the Kind row shows "Gravel" at its trailing end on one line, and its menu offers None and each kind of bike

#### Scenario: Weight takes digits only
- **WHEN** alice types "9a" into an empty weight
- **THEN** the weight reads "9"

#### Scenario: A starting distance in miles
- **WHEN** alice's units are imperial
- **THEN** the starting distance is labelled "Starting distance (mi)"

#### Scenario: A failed save
- **WHEN** saving fails because the server can't be reached
- **THEN** the failure shows at the top of the form and what alice entered stays

### Requirement: The gear editor's choices on iOS
On iOS the gear form SHALL offer the shared form's choices in its words: type, kind and who may see it as menus, with the Followers note as shared; the sports it is for and the activity types it is the default for, each in a sheet by category, a sub-sport under its sport; its collections, ticked in place; and the acquired date, from a calendar of past days, which can be cleared.

#### Scenario: Sports in a sheet
- **WHEN** alice chooses Ride and Run in the sports sheet and closes it
- **THEN** the Sports row reads "Ride, Run"

#### Scenario: No later day
- **WHEN** alice opens the acquired date's calendar
- **THEN** no day after today can be chosen

### Requirement: Picking a gear photo on iOS
On iOS the gear form SHALL let the athlete pick its photo from the photo library, show it in the form, change it and remove it. A picked photo SHALL be re-encoded and scaled as on every other platform before it is saved, dropping its metadata, its location included.

#### Scenario: Adding a photo
- **WHEN** alice taps Add photo and picks a photo taken with her phone's location on
- **THEN** the form shows the photo with Change photo and Remove photo, and the photo saved carries no location

#### Scenario: Removing it
- **WHEN** alice taps Remove photo
- **THEN** the form shows no photo and offers Add photo

### Requirement: A saved item's management on iOS
On iOS the form of a saved item SHALL also show what it is mounted on and since when, which opens that item; the day it was retired; Retire, or Put back in use for a retired item; and Delete. Delete SHALL ask first in the system's alert, in the shared form's words, offering Retire instead; only its confirmation SHALL delete.

#### Scenario: A mounted chain
- **WHEN** alice edits Chain #4, mounted on her gravel bike since 1 July 2026
- **THEN** the form reads "Mounted on Gravel bike since Jul 1, 2026", and tapping it opens the gravel bike's page

#### Scenario: Asked before deleting
- **WHEN** alice taps Delete on her gravel bike
- **THEN** an alert asks "Delete this gear?", says "Deleting removes it for good. Retiring keeps it and its details, out of the way.", and offers Retire, Delete and Cancel

### Requirement: The collection editor is native on iOS
On iOS the collection form SHALL be drawn with the system's own grouped form and navigation bar, and SHALL offer what the shared form offers, in the same words: the name, required; the notes, with the collections help; and the items in order, each with its photo, name, kind, who may see it and Retired where it is, moved and removed in place. Save SHALL be in the bar. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Saving without a name
- **WHEN** alice taps Save on a new collection with no name
- **THEN** nothing is saved, and the name field reads "Give your collection a name" in the error colour

#### Scenario: Reordering
- **WHEN** alice drags Race shoes above Aero helmet in "Race kit" and saves
- **THEN** the collection lists Tarmac, Race shoes and Aero helmet, in that order

#### Scenario: Moving read aloud
- **WHEN** VoiceOver is on Aero helmet in the items
- **THEN** its actions include "Move Aero helmet up", "Move Aero helmet down" and "Remove Aero helmet"

### Requirement: Adding items to a collection on iOS
On iOS the collection form's Add items SHALL offer the athlete's other items to tick and add them at the end in the order ticked. Once the collection holds 50 items, the form SHALL say it holds no more in place of Add items.

#### Scenario: Adding in the order ticked
- **WHEN** alice ticks the power meter and then her wetsuit under Add items, and taps Add
- **THEN** the items end with the power meter, then the wetsuit

#### Scenario: Nothing left to add
- **WHEN** every item alice has is in the collection and she opens Add items
- **THEN** the sheet reads "All your gear is in this collection."

#### Scenario: A full collection
- **WHEN** the collection holds 50 items
- **THEN** the form reads "A collection holds at most 50 items." and offers no Add items

### Requirement: The iOS gear editors open full width
On iOS both editors SHALL open full width over Settings, alone or beside the list, and back, or a save that succeeds, SHALL return to the page they were opened from.

#### Scenario: Editing a collection from beside the list
- **WHEN** on an iPad alice opens "Race kit" from Gear beside Settings' list and taps Edit
- **THEN** the collection form takes the whole window, and back returns to "Race kit"

#### Scenario: Saving a new item
- **WHEN** alice saves a new item from Add gear
- **THEN** the form closes onto Gear, which lists the item

### Requirement: Training zones is native on iOS
On iOS the Training zones page SHALL be drawn with the system's own grouped list and navigation bar, and SHALL list the default zones and then each sport with its own, each with its heading, what it applies to, Edit, its thresholds, and each kind of zone it offers with its model and its zones, or what it needs to work them out, in the shared page's words. Its loading SHALL follow `ui/content-loading`.

#### Scenario: The defaults
- **WHEN** alice's threshold heart rate is 170 bpm, her max 190, her resting 55 and her threshold power 250 W, and she opens Training zones
- **THEN** the Default zones read "Threshold heart rate 170 bpm", "Max heart rate 190 bpm", "Resting heart rate 55 bpm" and "Threshold power 250 W", then her heart rate zones and her power zones, each with its model

#### Scenario: A sport's threshold from the defaults
- **WHEN** alice's running zones set only a threshold heart rate of 172 bpm
- **THEN** her Run zones read "Threshold heart rate 172 bpm" and "Max heart rate 190 bpm (default)"

#### Scenario: A threshold not set
- **WHEN** alice has no threshold power
- **THEN** it reads "Not set", and her power zones read "Enter your threshold power to see these zones."

#### Scenario: Opening the defaults
- **WHEN** alice taps Edit beside Default zones
- **THEN** the default zones' form opens

### Requirement: Training zones' visibility, history and sports on iOS
On iOS the Training zones page SHALL also offer, once any threshold or zone model is saved, who can see the zones, as a menu saved as soon as it is chosen, with the Followers note as shared; Threshold history when any threshold has a value; and, while any sport has no zones of its own, Add a sport, offering those sports.

#### Scenario: Who can see them
- **WHEN** alice chooses Followers for her zones
- **THEN** it is saved for every set of zones, and the page says that anyone who follows her can see them, since she doesn't approve followers

#### Scenario: Nothing saved yet
- **WHEN** alice has saved no threshold or zone model
- **THEN** the page offers no visibility and no Threshold history

#### Scenario: Adding a sport
- **WHEN** alice chooses Swim under Add a sport
- **THEN** Swim's zones open, with nothing of its own yet

### Requirement: The default zones are native on iOS
On iOS the default zones' form SHALL be drawn with the system's own grouped form and navigation bar, and SHALL offer the shared form's fields in its words: the threshold heart rate, max heart rate, resting heart rate and threshold power, each with whether imported activities may update it; and the heart rate and power zone models as menus, each with what it is based on and the zones it gives, or what it needs to work them out. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Digits only
- **WHEN** alice types "17a0" into an empty threshold heart rate
- **THEN** the field reads "170"

#### Scenario: Zones as the model gives them
- **WHEN** alice chooses Andy Coggan (5) for her heart rate zones
- **THEN** the form lists Zone 1 to Zone 5 with their names and ranges in bpm, the last open-ended

#### Scenario: Updating from imports
- **WHEN** alice turns on Update from imported activities under her max heart rate
- **THEN** the change is kept with the form and saved with it

### Requirement: Saving the iOS zones forms
On iOS the default zones' form and a sport's zones form SHALL have Save in the bar, unavailable until the saved values have loaded, while saving, and while any field holds a value out of its range, which SHALL show in the error colour. A failed save SHALL show at the top of the form and keep what was entered. A save that succeeds SHALL return to Training zones.

#### Scenario: A value out of range
- **WHEN** alice types 300 as her max heart rate
- **THEN** the field shows in the error colour and Save is unavailable

#### Scenario: A failed save
- **WHEN** saving fails because the server can't be reached
- **THEN** the failure shows at the top of the form and what alice entered stays

### Requirement: A sport's zones are native on iOS
On iOS a sport's zones form SHALL be drawn with the system's own grouped form and navigation bar, titled by the sport, and SHALL offer the shared form's fields in its words: the thresholds the sport's zone kinds use, the threshold pace as `m:ss` in the sport's pace unit, each blank one showing the default it falls back to; each kind's zone model as a menu with its zones, pace zones as paces; and, for a sport that has its own zones, Remove, which returns it to the defaults.

#### Scenario: A blank threshold
- **WHEN** alice opens Run's zones with no max heart rate of its own and a default of 190
- **THEN** the max heart rate row shows "Default: 190"

#### Scenario: Pace zones
- **WHEN** alice's running threshold pace is 4:30 per kilometer with Joe Friel Running (7)
- **THEN** the pace zones read as paces per kilometer, the first "Slower than" a pace and the last "or faster"

#### Scenario: Removing a sport's zones
- **WHEN** alice taps Remove Run zones
- **THEN** Run uses the default zones again and the form returns to Training zones

### Requirement: Privacy zones is native on iOS
On iOS the Privacy zones page SHALL be drawn with the system's own grouped form and navigation bar, in the shared page's words: each zone by its name with its radius as a menu in the athlete's units, and Remove; Hide start and end as a menu with Off; and the shared notes on what zones do, hiding an activity's start or end, and other apps' copies. A zone's centre SHALL NOT be shown. Its loading SHALL follow `ui/content-loading`.

#### Scenario: A zone's radius
- **WHEN** alice, whose units are metric, chooses 600 m for Home
- **THEN** the change is saved, and Home's radius reads "0.60 km"

#### Scenario: Removing a zone
- **WHEN** alice removes Home, her only zone
- **THEN** Home is removed, and the page reads "No privacy zones yet."

#### Scenario: Hiding the ends
- **WHEN** alice chooses Off under Hide start and end
- **THEN** her routes' starts and ends are no longer hidden outside her zones

### Requirement: Adding a privacy zone here on iOS
On an iPhone the Privacy zones page SHALL offer an optional label and Add a zone at my location, which SHALL read "Finding your location…" while it finds it, the system asking for the location first if the app may not use it yet. The zone SHALL be added with a 400 m radius and the label.

#### Scenario: Adding a zone here
- **WHEN** on an iPhone alice types "Work" and taps Add a zone at my location
- **THEN** the button reads "Finding your location…" until the zone is added, as "Work" with a 400 m radius

### Requirement: When privacy zones can't change on iOS
On iOS, while the session must sign in again, the Privacy zones page SHALL say so at the top and nothing on it SHALL change; while a change saves, nothing else SHALL change; and a failed change SHALL show at the top.

#### Scenario: Signed in before the zones' scope
- **WHEN** alice's session was granted before the zones' scope
- **THEN** the page says to sign in again, and no zone, radius or hidden distance can be changed

### Requirement: The iOS zones pages open as the shared pages do
On iOS, Training zones and Privacy zones SHALL open as Settings' pages, beside the list on an iPad as wide as Settings needs. The default zones and a sport's zones opened from Training zones SHALL show beside the list too, as the column's second page with a back button to Training zones, and alone in the stack with the stack's back button. Threshold history SHALL open the same way, drawn natively.

#### Scenario: A sport's zones beside the list
- **WHEN** on an iPad alice opens Training zones beside Settings' list and taps Edit beside her Run zones
- **THEN** Run's zones show beside the list with a back button, and back returns to Training zones beside the list

#### Scenario: Threshold history
- **WHEN** alice taps Threshold history on the native Training zones
- **THEN** the native threshold history opens, with its charts

### Requirement: Threshold history is native on iOS
On iOS threshold history SHALL be drawn with the system's own grouped list and navigation bar: the shared help, Expand all or Collapse all in the bar, then the defaults and each sport with history as a group whose header expands or collapses it and summarises its thresholds and values. Its loading and refresh SHALL follow `ui/content-loading`, and a failed removal SHALL show why over the list.

#### Scenario: Collapsing a group
- **WHEN** alice taps the header of her expanded defaults on the native threshold history
- **THEN** the defaults collapse to a tile per threshold, and Collapse all becomes Expand all once no group is expanded

#### Scenario: No history
- **WHEN** alice has no threshold history
- **THEN** the page says so

### Requirement: An expanded threshold on iOS shows its chart and values
In an expanded group on iOS each threshold SHALL show its value now and how it moved, a step chart of its dated values running on to today with a dot and its value over each (filled on the current value), its first date and Today under the chart with how many values have no date, and its values newest first with date, source and Current, the newest few unless all are shown.

#### Scenario: A threshold with five values
- **WHEN** alice's threshold power has five values
- **THEN** the newest three show with Show all (5), which shows every value, and then Show fewer

#### Scenario: A single value
- **WHEN** a threshold has one value
- **THEN** it says "Only value" and shows no chart

### Requirement: A collapsed group on iOS shows an overview
In a collapsed group on iOS each threshold SHALL show as a tile with its value now, a sparkline of its progress when it has two dated values, and how it moved.

#### Scenario: A collapsed sport
- **WHEN** alice's Ride history is collapsed
- **THEN** a tile per Ride threshold shows its value, its sparkline and its change

### Requirement: Removing a threshold value on iOS
On iOS each value SHALL offer Remove from a menu, after which it is removed as on the shared page; while one value is being removed no other can be, and the removed row shows progress.

#### Scenario: Removing the current value
- **WHEN** alice removes her current threshold power on the native page
- **THEN** the row shows progress, and the threshold goes back to the value before it

### Requirement: A settings page shows natively beside the iOS list
On a wide iOS window a settings page SHALL show in the detail pane beside the native list, drawn natively, with the same content and actions it has full width.

#### Scenario: Privacy zones beside the list
- **WHEN** on a wide iOS window an athlete chooses Privacy zones
- **THEN** Privacy zones shows beside the list with its title and no back button, and the list marks Privacy zones

#### Scenario: A deeper page
- **WHEN** on a wide iOS window an athlete opens Training zones and then a sport's zones
- **THEN** the sport's zones show in the detail pane with a back button that returns to Training zones, and the list still marks Training zones
