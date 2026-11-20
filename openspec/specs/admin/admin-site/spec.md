# admin-site Specification

## Purpose

Gives the AppView's operators a web site for watching and steering the AppView itself, with sign-in by atproto account, read-only and operator roles, an audit log of every action, and a guarantee that it never reveals athletes' private content.

## Requirements

### Requirement: Admins come from configuration
The AppView SHALL treat as admins exactly the DIDs its configuration lists, each with the role `viewer` or `operator`. It SHALL read the list from configuration only, never from the labeler or any record. With no admins configured, every `/admin` path SHALL answer `404`.

#### Scenario: Viewer and operator configured
- **WHEN** the configuration lists alice's DID as `operator` and bob's as `viewer`
- **THEN** both may sign in to the admin site, and only alice may run actions

#### Scenario: No admins configured
- **WHEN** the configuration lists no admins
- **THEN** a request for any `/admin` path answers `404`

#### Scenario: Labeler down
- **WHEN** the Ozone labeler can't be reached
- **THEN** a configured admin still signs in and uses the site

#### Scenario: Malformed entry
- **WHEN** an entry in the admin list isn't a DID with a known role
- **THEN** the AppView refuses to start and names the bad entry

### Requirement: Sign in with an atproto account
An admin SHALL sign in by entering their handle or DID and authorizing at their own PDS's authorization server through atproto OAuth, asking for identity only. The AppView SHALL accept the sign-in only for the DID the authorization server vouches for, and only if that DID is a configured admin.

#### Scenario: Admin signs in
- **WHEN** alice, a configured admin, enters her handle and approves at her PDS
- **THEN** she lands on the admin site's home page signed in as her DID

#### Scenario: Not an admin
- **WHEN** carol, who isn't a configured admin, completes the authorization
- **THEN** the AppView refuses the sign-in and creates no session

#### Scenario: Tampered callback
- **WHEN** the authorization callback's state or issuer doesn't match the sign-in that started it
- **THEN** the AppView refuses the sign-in

### Requirement: The AppView keeps no account tokens
After a sign-in, the AppView SHALL keep only the admin's DID. It SHALL revoke the tokens the authorization server issued and SHALL NOT store them, so the admin site can never act on the admin's account.

#### Scenario: Tokens given up
- **WHEN** alice's sign-in completes
- **THEN** the AppView revokes the issued tokens and stores no token, only her admin session

### Requirement: Admin sessions
A sign-in SHALL create an admin session that expires at most 12 hours later, with no renewal past that. Signing out SHALL end it at once. Sessions SHALL survive an AppView restart. The AppView SHALL store only a hash of the session secret.

#### Scenario: Session expires
- **WHEN** alice signed in 12 hours ago
- **THEN** her next request is sent to sign in again

#### Scenario: Sign out
- **WHEN** alice signs out
- **THEN** her session no longer admits any request, from any tab

#### Scenario: Restart
- **WHEN** the AppView restarts while alice's session is live
- **THEN** her next request is still signed in

### Requirement: Role checked on every request
The AppView SHALL check the session's DID against the configured admin list on every request, and use the role configured now. Removing an admin or lowering their role SHALL take effect at their next request.

#### Scenario: Admin removed
- **WHEN** alice's DID is taken out of the configuration and the AppView restarts
- **THEN** her next request is refused, though her session hasn't expired

#### Scenario: Role lowered
- **WHEN** alice is changed from `operator` to `viewer`
- **THEN** her next action is refused while her pages still load

### Requirement: Only operators run actions
Any request that changes state SHALL be a POST and SHALL be refused with `403` unless the session's admin is an `operator`. A `viewer` SHALL be able to load every admin page. Pages SHALL NOT offer viewers controls they can't use.

#### Scenario: Viewer tries an action
- **WHEN** bob, a `viewer`, posts to an action
- **THEN** the AppView answers `403`, changes nothing and records the refusal

#### Scenario: Viewer browses
- **WHEN** bob opens an admin page
- **THEN** it loads without its action controls

### Requirement: Actions are protected against forgery
Every state-changing request SHALL carry a token tied to the admin's session, and the AppView SHALL refuse one without a valid token. The session cookie SHALL be `HttpOnly`, `Secure` (except on a loopback development host) and `SameSite=Strict`, scoped to `/admin`.

#### Scenario: Cross-site post
- **WHEN** another site makes alice's browser post to an admin action
- **THEN** the AppView refuses it and changes nothing

#### Scenario: Missing token
- **WHEN** a request with a valid session cookie posts an action without the session's token
- **THEN** the AppView refuses it

### Requirement: The admin site is its own browser surface
The admin site SHALL NOT be readable cross-origin: `/admin` responses SHALL carry no permissive CORS headers. They SHALL forbid framing and SHALL send a content security policy that allows scripts and styles only from the AppView itself. The site SHALL load nothing from third-party hosts.

#### Scenario: Cross-origin read
- **WHEN** a page on another origin fetches an admin page with alice's credentials
- **THEN** the browser can't read the response

#### Scenario: Framing
- **WHEN** another site embeds an admin page in a frame
- **THEN** the browser refuses to show it

### Requirement: Every action is audited
The AppView SHALL record every operator action and every refused action attempt: the admin's DID, the action, its target, its parameters, the outcome and the time. The record SHALL be written whether the action succeeds or fails. Records SHALL be kept for at least one year and never hold private record content.

#### Scenario: Action recorded
- **WHEN** alice runs an action on a target
- **THEN** the audit log holds her DID, the action, the target, its parameters, the outcome and the time

#### Scenario: Failed action recorded
- **WHEN** alice's action fails
- **THEN** the audit log records it with the failure as its outcome

#### Scenario: Old records pruned
- **WHEN** a record is more than one year and one month old
- **THEN** the AppView may delete it, and keeps every younger one

### Requirement: Audit log page
The admin site SHALL show the audit log to every admin, newest first and paged, filterable by admin, action and target.

#### Scenario: Viewer reads the log
- **WHEN** bob, a `viewer`, opens the audit log filtered to alice
- **THEN** he sees alice's actions, newest first

### Requirement: Admin pages reveal no private content
Admin pages and their responses SHALL show only identifiers (DIDs, handles, record URIs, ids), statuses, counts, sizes and times. They SHALL NOT show the content of any record, blob, track, effort time, like, comment, private profile detail, privacy zone or anything else the AppView holds for an athlete beyond what it serves publicly. Reviewing non-public content SHALL stay with `moderation.getSubject`.

#### Scenario: Account with private activities
- **WHEN** an admin page concerns an athlete with Only me activities
- **THEN** it may show how many activities are indexed for them, but no activity's title, time, place, stats or photos

#### Scenario: Public content
- **WHEN** an admin page lists a public segment
- **THEN** it may show the segment's name and status, which the AppView already serves to anyone

#### Scenario: Home tiles
- **WHEN** an athlete with Only me activities has a failed reindex run and alice opens Home
- **THEN** Home's tiles show counts and times only, and name no athlete's record content

### Requirement: Pages share one site
Every admin page SHALL be reached from the site's navigation, show who is signed in with which role, and offer sign-out. A page added by a later change SHALL get these without changing other pages. The navigation SHALL mark the page being shown, SHALL flag each page whose summary has a `warn` or `bad` tile, and SHALL stay usable on a phone-width screen without scrolling the page sideways.

#### Scenario: Navigation
- **WHEN** alice opens any admin page
- **THEN** she sees the navigation to every admin page, her DID and role, and sign-out

#### Scenario: Current page marked
- **WHEN** alice opens the Queues page
- **THEN** the navigation marks Queues as the current page, both visibly and for assistive technology

#### Scenario: Page with a problem flagged
- **WHEN** the Health page's summary has a `bad` tile and alice opens the Segments page
- **THEN** the navigation flags Health with the worst status among its tiles

#### Scenario: Narrow screen
- **WHEN** alice opens an admin page on a 375 px wide screen
- **THEN** she can reach every page from the navigation and the page doesn't scroll sideways, though a wide table may scroll within itself

### Requirement: Pages summarise themselves
The site SHALL let each admin page offer a summary: a short list of tiles, each with a label, a value that is a count, size, time or duration, a status of `ok`, `warn`, `bad` or none, and a link to where the page shows the detail. A page added by a later change SHALL get its summary onto Home and into the navigation's flags without changing other pages.

#### Scenario: New page with a summary
- **WHEN** a later change adds a page that offers a summary
- **THEN** its tiles appear on Home and its flag in the navigation, and no other page's code changes

#### Scenario: Page without a summary
- **WHEN** a page offers no summary, as the audit log may not
- **THEN** it adds no tiles to Home and is never flagged

#### Scenario: Tile links to its detail
- **WHEN** alice selects the Health page's failing-spaces tile on Home
- **THEN** she lands on the Health page's space-syncing section

### Requirement: Home triages the AppView
Home SHALL show every page's summary tiles to every admin. Tiles with status `bad` SHALL come first, then `warn`, then the rest, keeping navigation order within each group. When no tile is `warn` or `bad`, Home SHALL say that nothing needs attention.

#### Scenario: Problems first
- **WHEN** Queues has a `bad` tile and Health a `warn` tile
- **THEN** Home shows Queues' `bad` tile first, then Health's `warn` tile, then every other tile

#### Scenario: All clear
- **WHEN** no page has a `warn` or `bad` tile
- **THEN** Home says nothing needs attention and still shows every tile

#### Scenario: Viewer
- **WHEN** bob, a `viewer`, opens Home
- **THEN** he sees the same tiles as an operator, with no action controls

### Requirement: Home stays current
While Home is open, its tiles SHALL redraw every 30 seconds without reloading the page. When a summary can't be read, Home SHALL show that page's tiles as unavailable and still draw every other page's.

#### Scenario: Refresh
- **WHEN** alice keeps Home open and a job gives up
- **THEN** within about 30 seconds the Queues tile for jobs that gave up shows it, with no reload

#### Scenario: One summary fails
- **WHEN** reading the Segments page's summary throws
- **THEN** Home shows Segments' tiles as unavailable and draws every other page's tiles

### Requirement: Navigation flags never hold up a page
A page SHALL draw in full without waiting for any summary. The navigation's flags SHALL load after the page. When they can't load, the page SHALL work as before, unflagged.

#### Scenario: Slow summary
- **WHEN** the Health page's summary takes several seconds and alice opens the Audit log
- **THEN** the Audit log draws at once and its navigation flags appear once they are read

#### Scenario: Flags fail
- **WHEN** the navigation's flags fail to load
- **THEN** the page and its navigation still work, with no flags shown

### Requirement: States, times and numbers look the same everywhere
Every admin page SHALL show a state with a fixed meaning (a worker's, a stream's, a reindex run's, a segment's status) as a badge whose colour gives that meaning, with the state's name written in it. Times SHALL show as relative to now, with the exact UTC time available on the same element. Counts and sizes in tables SHALL align right.

#### Scenario: Worker state
- **WHEN** alice opens the Health page while a worker is restarting
- **THEN** its state shows as a `bad` badge reading "restarting", and a running worker's as an `ok` badge reading "running"

#### Scenario: Exact time
- **WHEN** alice looks at a worker's last success, shown as "3 min ago"
- **THEN** she can read its exact UTC time without leaving the page

#### Scenario: No colour alone
- **WHEN** someone who can't tell the badge colours apart reads a state
- **THEN** its written name still gives the state

### Requirement: Destructive actions are marked and confirmed
An action that discards or rewinds state (dropping a job, requeueing in bulk, rewinding Jetstream, unpinning) SHALL be styled apart from other actions and SHALL ask the operator to confirm before it posts. This SHALL add to, not replace, any confirmation the action's own requirements ask for.

#### Scenario: Drop a job
- **WHEN** alice selects Drop on a dead letter
- **THEN** she is asked to confirm, and nothing is posted unless she does

#### Scenario: Ordinary action
- **WHEN** alice selects Retry now on a retrying job
- **THEN** it posts without an extra confirmation

### Requirement: The site's look loads from the AppView alone
The site's styles and scripts SHALL keep coming only from the AppView under its content security policy. No page SHALL use an inline style or script, and no page SHALL load fonts, styles or scripts from another host. The site SHALL follow the browser's light or dark preference.

#### Scenario: Dark preference
- **WHEN** alice's browser prefers a dark appearance
- **THEN** every admin page and its badges draw in the dark palette, with their meanings still told apart

#### Scenario: Content security policy
- **WHEN** an admin page loads under its content security policy
- **THEN** the browser refuses nothing on it
