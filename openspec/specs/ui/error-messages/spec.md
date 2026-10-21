# ui/error-messages Specification

## Purpose

What an error the app shows says, on every screen, for every action and at sign-in: what failed, why when the reason is one the athlete can act on or wait out, and never the raw technical text of the failure.

## Requirements

### Requirement: Errors say what failed
Every error the app shows SHALL name what failed in the athlete's language, such as couldn't save the profile, couldn't save privacy zones or couldn't load the profile.

#### Scenario: Refused save
- **WHEN** saving privacy zones fails because the athlete's PDS refused the write
- **THEN** the app says it couldn't save privacy zones

### Requirement: Errors say why when the reason is known
When a failure is known to be the device being offline, a problem on the server, or a reply the app couldn't read, the error SHALL say so after what failed. When the reason is anything else, the error SHALL say only what failed.

#### Scenario: Saving offline
- **WHEN** an athlete saves their profile while the device has no connection
- **THEN** the app says it couldn't save the profile because the device is offline

#### Scenario: Server error on an action
- **WHEN** changing an activity's visibility fails because the server answered with a server error
- **THEN** the app says it couldn't save the activity because of a problem on the server

#### Scenario: Unknown reason
- **WHEN** saving the profile fails for a reason the app doesn't classify
- **THEN** the app says only that it couldn't save the profile

### Requirement: Errors never show raw technical text
No error the app shows SHALL include an exception's message, an HTTP status code, an XRPC error name, a URL or a DID.

#### Scenario: Web fetch failure on an unmigrated screen
- **WHEN** the activity page fails to load in the web app because the request never reached the server
- **THEN** the page says it couldn't load the activity because the device is offline, and doesn't show "Fail to fetch"

### Requirement: Sign-in tells an unknown handle apart from being offline
When every way of resolving the handle an athlete typed answers that it doesn't exist, sign-in SHALL say that no account was found for that handle. When the handle couldn't be resolved because the device is offline, sign-in SHALL say it couldn't start because the device is offline, not that the account doesn't exist.

#### Scenario: Mistyped handle
- **WHEN** an athlete signs in as a handle that no lookup resolves
- **THEN** sign-in says no account was found for that handle

#### Scenario: Offline at sign-in
- **WHEN** an athlete signs in while the device has no connection
- **THEN** sign-in says it couldn't start because the device is offline

### Requirement: Sign-in failures say what failed and why
A sign-in that fails after the handle resolved SHALL say whether starting or completing sign-in failed and, where the reason is known, why, under the same rules as other errors.

#### Scenario: Server error completing sign-in
- **WHEN** completing sign-in fails because the authorization server answered with a server error
- **THEN** the app says sign-in failed because of a problem on the server

### Requirement: A mistyped moderation subject is reported as such
When a moderator opens a subject whose typed text isn't an AT-URI, the moderation screen SHALL say the text isn't a valid record address, without asking the server.

#### Scenario: Not an AT-URI
- **WHEN** a moderator types "not a uri" and opens it
- **THEN** the screen says it isn't a valid record address, and no subject is requested
