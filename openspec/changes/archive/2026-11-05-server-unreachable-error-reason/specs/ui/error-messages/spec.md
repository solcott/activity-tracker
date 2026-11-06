## MODIFIED Requirements

### Requirement: Errors say why when the reason is known
When a failure is known to be that the server couldn't be reached (no response arrived, because the device is offline or the server is down), a problem on the server, or a reply the app couldn't read, the error SHALL say so after what failed. An error SHALL NOT say the device is offline, because the app can't tell that from a server that is down. When the reason is anything else, the error SHALL say only what failed.

#### Scenario: Saving offline
- **WHEN** an athlete saves their profile while the device has no connection
- **THEN** the app says it couldn't save the profile because the server couldn't be reached

#### Scenario: Server down while the device is online
- **WHEN** an athlete opens the feed while the device has a connection but the AppView isn't accepting connections
- **THEN** the app says it couldn't load the feed because the server couldn't be reached, and doesn't say the device is offline

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
- **THEN** the page says it couldn't load the activity because the server couldn't be reached, and doesn't show "Fail to fetch"

### Requirement: Sign-in tells an unknown handle apart from being offline
When every way of resolving the handle an athlete typed answers that it doesn't exist, sign-in SHALL say that no account was found for that handle. When the handle couldn't be resolved because no lookup got a response, sign-in SHALL say it couldn't start because the server couldn't be reached, not that the account doesn't exist.

#### Scenario: Mistyped handle
- **WHEN** an athlete signs in as a handle that no lookup resolves
- **THEN** sign-in says no account was found for that handle

#### Scenario: Offline at sign-in
- **WHEN** an athlete signs in while the device has no connection
- **THEN** sign-in says it couldn't start because the server couldn't be reached
