# Spec Delta

## Purpose

How a URL opens a page of the app on Android, iOS and the web: which URLs are links, the path each linkable page has, the stack a link builds, and what happens when a link arrives before the athlete is signed in.

## ADDED Requirements

### Requirement: Links come in three forms
The app SHALL treat a URL as a link in three cases: when it is https on the AppView's public host, when it uses the `activitytracker` scheme (where the URL's host is the path's first segment), and, on the web, when it is a path on the web app's own origin. All three forms SHALL open the same page. A path under `/oauth/`, `/xrpc/` or `/.well-known/` SHALL never be a link.

#### Scenario: https link
- **WHEN** the app is given `https://<appview host>/segment/did:plc:abc/3kx`
- **THEN** it opens that segment's page

#### Scenario: Custom scheme
- **WHEN** the app is given `activitytracker://segment/did:plc:abc/3kx`
- **THEN** it opens the same segment's page

#### Scenario: Another host
- **WHEN** the app is given `https://example.com/segment/did:plc:abc/3kx`
- **THEN** it is not a link and nothing opens

#### Scenario: OAuth callback
- **WHEN** the app is given its OAuth callback URL
- **THEN** sign-in handles it and no page opens as a link

### Requirement: Every linkable page has one path
Every page that can be opened with its arguments taken from a URL SHALL have exactly one canonical path. Writing a page's path and reading it back SHALL give the same page. Paths SHALL be written with DIDs and read with either DIDs or handles. Arguments that only describe how a page was reached SHALL NOT be part of its path, such as a name to show before it loads or a hint to suggest after saving.

#### Scenario: Activity
- **WHEN** the activity `at://did:plc:abc/app.activitytracker.feed.activity/3kx` is written as a link
- **THEN** its path is `/profile/did:plc:abc/activity/3kx`, and reading that path opens the same activity

#### Scenario: Leaderboard with a board chosen
- **WHEN** a segment's leaderboard showing a chosen board is written as a link
- **THEN** its path is `/segment/{did}/{rkey}/leaderboard?board=<board>`, and it carries no segment name

#### Scenario: DID with a port
- **WHEN** a profile whose DID is `did:web:localhost%3A8081` is written as a link and read back
- **THEN** the same profile opens

#### Scenario: Trail for a sport
- **WHEN** a trail page shown for one sport group is written as a link
- **THEN** its path is `/trail/{did}/{rkey}?sport=<group>`

### Requirement: Pages not reached by link are named
Pages that are never on a tab's stack SHALL have no path: sign-in, leaving, the choice after leaving elsewhere, and the segment and dive site browse lists shown inside Search, which `/search` covers. Adding a new page that has neither a path nor a place on this list SHALL fail the build.

#### Scenario: New screen without a path
- **WHEN** a new screen is added to a feature's contracts with no link path and no exclusion
- **THEN** `./gradlew check` fails and names the screen

### Requirement: A link opens on the tab it belongs to
The app SHALL open the viewer's own profile on the You tab with nothing above it. Pages under the viewer's profile and Settings (Settings and its pages, follow requests, invites, starred segments, edit profile) SHALL also open on the You tab. `/` SHALL open the Feed tab and `/search` the Search tab. Any other link SHALL open on the tab being shown, or on Feed when the app is starting.

#### Scenario: Own settings page
- **WHEN** an athlete on the Search tab opens `/settings/training-zones/history`
- **THEN** the You tab shows their profile, Settings, Training zones and History, with History on top

#### Scenario: Someone else's activity at start
- **WHEN** the app starts from a link to another athlete's activity
- **THEN** the Feed tab shows the feed with the activity on top

#### Scenario: Own profile
- **WHEN** an athlete opens a link to their own profile
- **THEN** the You tab shows its first screen alone

### Requirement: A link puts the page's parents under it
A linked page SHALL open with the pages it is reached through under it (a segment under its leaderboard, an activity under its map), so Back goes where it would have gone had the athlete walked there. When a page of that chain is already in the tab's stack, the stack SHALL be kept up to that page and the rest pushed above it, rather than opening it twice.

#### Scenario: Leaderboard from a link
- **WHEN** a link to a segment's leaderboard opens over the feed
- **THEN** the stack is the feed, the segment and its leaderboard, and Back shows the segment

#### Scenario: Segment already open
- **WHEN** a link to a segment's Mayors opens while that segment's page is on top
- **THEN** Mayors opens above the existing segment page, and the segment is not opened again

### Requirement: Handles in links are resolved
A link that names an athlete by handle SHALL open the same page as the link with their DID. When the handle can't be resolved, a link to a record SHALL open the profile page for that handle, which says whether the athlete was found.

#### Scenario: Handle link
- **WHEN** the app opens `/profile/alice.test/activity/3kx`
- **THEN** it opens the activity `at://<alice's DID>/app.activitytracker.feed.activity/3kx`

#### Scenario: Unknown handle
- **WHEN** a link names a handle that doesn't resolve
- **THEN** that handle's profile page opens and says the athlete wasn't found

### Requirement: Owner-only pages fall back to the activity
A link to an activity's edit form, Add people or combine picker SHALL open that page only for the activity's owner. For anyone else it SHALL open the activity page.

#### Scenario: Someone else's edit link
- **WHEN** an athlete opens a link to edit another athlete's activity
- **THEN** that activity's page opens and no edit form shows

### Requirement: A link waits for sign-in
A link that arrives while no session is open SHALL be kept and opened once a session opens. This covers being signed out, restoring, leaving, and the choice after leaving elsewhere. On the web the kept link SHALL survive sign-in's full-page redirect. When a newer link arrives before the first is opened, only the newer one SHALL open.

#### Scenario: Signed out
- **WHEN** a signed-out athlete opens a link to a segment and then signs in
- **THEN** the segment's page opens after sign-in

#### Scenario: Web sign-in redirect
- **WHEN** a signed-out athlete loads `/trail/{did}/{rkey}` on the web and signs in through the authorization server's page
- **THEN** the trail page opens when the app loads again after the redirect

### Requirement: Links that aren't the app's are ignored
A link on the app's host or scheme whose path matches no page SHALL leave the app where it is, with no error shown.

#### Scenario: Unknown path
- **WHEN** the app is given `activitytracker://nowhere`
- **THEN** the app keeps showing what it showed

### Requirement: Pages opened by link load their own names
The leaderboard, Mayors, dive site regulars and import batch pages SHALL show their title once loaded when opened with no name. The title is the segment's current name, the dive site's name, or the batch's athlete. Opened from inside the app with a name, they SHALL show that name at once as today.

#### Scenario: Leaderboard by link
- **WHEN** a segment's leaderboard opens from a link
- **THEN** its title shows the segment's current name once the segment has loaded

### Requirement: The AppView proves the app's links
The AppView SHALL serve `/.well-known/assetlinks.json`, naming the Android package and its signing certificates' SHA-256 fingerprints, and `/.well-known/apple-app-site-association`, naming the iOS app IDs and leaving out `/oauth/*`, `/xrpc/*` and `/.well-known/*`. Both SHALL be JSON served directly with no redirect. Each SHALL answer 404 while its values aren't configured.

#### Scenario: Configured Android
- **WHEN** the AppView is configured with the package and a certificate fingerprint
- **THEN** `/.well-known/assetlinks.json` lists them for `delegate_permission/common.handle_all_urls`

#### Scenario: Unconfigured Apple IDs
- **WHEN** no Apple app IDs are configured
- **THEN** `/.well-known/apple-app-site-association` answers 404

### Requirement: Universal Links don't need a team to build
The iOS app SHALL build and run with the ad-hoc signature and no development team. Without a team it SHALL open `activitytracker` links. The https links of the AppView host SHALL open it only when a team and link host are configured.

#### Scenario: Default build
- **WHEN** the iOS app is built with the ad-hoc signature and no local configuration
- **THEN** it builds, and `activitytracker://` links open their pages

### Requirement: Android verifies only its own paths
On Android the app SHALL claim https URLs on the link host only for path prefixes that are links, so that the OAuth client metadata and AppView endpoints still open in the browser.

#### Scenario: Client metadata
- **WHEN** `https://<appview host>/oauth/client-metadata.json` is opened on a device with the app installed
- **THEN** it opens in the browser, not the app
