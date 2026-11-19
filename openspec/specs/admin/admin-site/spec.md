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

### Requirement: Pages share one site
Every admin page SHALL be reached from the site's navigation, show who is signed in with which role, and offer sign-out. A page added by a later change SHALL get these without changing other pages.

#### Scenario: Navigation
- **WHEN** alice opens any admin page
- **THEN** she sees the navigation to every admin page, her DID and role, and sign-out
