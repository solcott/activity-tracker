# Spec Delta

## ADDED Requirements

### Requirement: The follow lists read the same in both UIs
Each follow list's title, the handle under it, its empty message and what failed when it can't load, and when an account's Remove can be used, SHALL be chosen by shared code, and both UIs SHALL show that choice.

#### Scenario: The following list
- **WHEN** bob's Following list is shown
- **THEN** both UIs title it "Following" with "@bob.test" under it, and an empty one reads "Not following anyone yet."

#### Scenario: A removal in flight
- **WHEN** bob's removal from alice's followers is being written
- **THEN** both UIs show his Remove and neither lets it be used

### Requirement: Follow requests read the same in both UIs
The Follow requests page's tab labels, each tab's empty message, which accounts each tab lists, when Decline is offered, when an account's answers can be used, and when the declined and truncated notes show SHALL be decided by shared code that both UIs ask.

#### Scenario: An account that can't be answered
- **WHEN** a request comes from an account whose DID isn't a valid record key
- **THEN** both UIs show its Decline and Approve and neither lets them be used

#### Scenario: The truncated note on Declined
- **WHEN** there were more than 500 requests and the athlete chooses Declined
- **THEN** neither UI shows "Showing the first 500 requests."

### Requirement: The sign-in-again notice is decided once
Whether a list shows that an action failed or that the session must sign in again, which of the two it says, and whether it offers Sign in again SHALL be decided by shared code that every list showing the notice asks, in both UIs.

#### Scenario: A session that must sign in again
- **WHEN** the session can't read or write declines and no answer has failed
- **THEN** both UIs read "Sign in again to share activities or details with Followers, Mutuals or Only me, …" with Sign in again

#### Scenario: A failed answer
- **WHEN** approving a request fails because the server can't be reached and the session can read declines
- **THEN** both UIs show the failure and offer no Sign in again

#### Scenario: Neither
- **WHEN** the session can read declines and no answer has failed
- **THEN** neither UI shows the notice
