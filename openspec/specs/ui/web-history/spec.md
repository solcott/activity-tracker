# ui/web-history Specification

## Purpose
How the web app's address bar and the browser's Back and Forward follow the app's own navigation, so a URL always names the page shown and the browser's history walks the app's stack.

## Requirements

### Requirement: The address bar names the page shown
While a session is open in the web app, the address bar SHALL show the link path of the page on top of the shown tab (`ui/deep-links`). Opening a page SHALL add a browser history entry. Replacing the page on top without going deeper SHALL replace the current entry instead, as when one settings page replaces another beside the list.

#### Scenario: Opening an activity
- **WHEN** an athlete on the feed opens an activity
- **THEN** the address bar shows `/profile/{did}/activity/{rkey}`, and the browser has one more history entry

#### Scenario: Settings page replaced
- **WHEN** an athlete with Activity defaults open beside Settings chooses Privacy zones
- **THEN** the address bar shows `/settings/privacy-zones` and the history has no extra entry

### Requirement: Back and Forward walk the app's stack
The browser's Back SHALL do what the app's back does: close the top page, or return to the tab shown before a tab switch. Forward SHALL reopen what Back closed. Going back in the app SHALL also move the browser's history back, so Back and Forward never disagree with the app.

#### Scenario: Browser Back
- **WHEN** an athlete on an activity's map presses the browser's Back
- **THEN** the activity page shows and the address bar names it

#### Scenario: Forward after Back
- **WHEN** the athlete then presses Forward
- **THEN** the map opens again

#### Scenario: In-app back
- **WHEN** the athlete presses the page's own back button on the map
- **THEN** the activity page shows, and the browser's Forward would reopen the map

### Requirement: Switching tabs is a history entry
Choosing another tab SHALL add a history entry naming that tab's top page. Back SHALL return to the tab shown before, with its stack as it was.

#### Scenario: Back after a tab switch
- **WHEN** an athlete on an activity in Feed switches to Search and presses the browser's Back
- **THEN** the Feed tab shows the activity again

### Requirement: A loaded URL rebuilds its stack
Loading or reloading the web app on a link path SHALL open that page as a link (`ui/deep-links`). Each page under it SHALL be a history entry of its own, so Back walks down to the tab's first page.

#### Scenario: Reload on a deep page
- **WHEN** an athlete reloads on `/settings/training-zones/history`
- **THEN** the You tab shows their profile, Settings, Training zones and History, and pressing Back three times reaches their profile

### Requirement: Signing out leaves the URL at the start
When the session closes (sign-out, account switch, leaving), the address bar SHALL show `/`. Back in a later session in the same tab SHALL stop at that session's first entry and never open an earlier session's pages.

#### Scenario: Sign out
- **WHEN** an athlete on a segment's page signs out
- **THEN** the address bar shows `/` and sign-in shows

#### Scenario: Back after switching account
- **WHEN** an athlete opens Settings, switches to another account without reloading, and presses Back on the new account's feed
- **THEN** the feed stays shown and the first account's Settings doesn't open
