# Spec Delta

## MODIFIED Requirements

### Requirement: The login screen says the session expired
After a session ends because its refresh was refused, the login screen SHALL say that the session expired and that signing in again continues, and SHALL fill in that account's handle. While a recording is running, it SHALL also say the recording is still going. After the athlete chose Sign in again on the Account page, the login screen SHALL fill in that account's handle and say that signing in again updates the app's permissions, with no expiry message. After a sign-out the athlete chose, the login screen SHALL show neither message nor the handle.

#### Scenario: Expired without a recording
- **WHEN** alice's session expires
- **THEN** the login screen says her session expired, with `alice.test` filled in

#### Scenario: Expired during a recording
- **WHEN** alice's session expires 20 minutes into a run
- **THEN** the login screen also says her recording is still running

#### Scenario: Chosen sign-out
- **WHEN** alice signs out from the Account page
- **THEN** the login screen shows no expiry message and an empty handle field

#### Scenario: Chosen sign in again
- **WHEN** bob chooses Sign in again on the Account page
- **THEN** the login screen shows `bob.test` filled in and says signing in again updates the app's permissions, with no expiry message

#### Scenario: Sign in again then cancelled
- **WHEN** bob chooses Sign in again, starts signing in and cancels in the browser
- **THEN** the login screen still shows `bob.test` filled in
