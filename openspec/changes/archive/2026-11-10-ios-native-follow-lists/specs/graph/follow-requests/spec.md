# Spec Delta

## ADDED Requirements

### Requirement: The follow lists are native on iOS
On iOS the Followers and Following lists SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared lists show, in the same words: the list's name as its title with the athlete's handle under it once known, and each account with its picture, name and handle, opening its profile when tapped; the empty list's message. They SHALL load page by page following `ui/content-loading`, asking for the next page only once the last account loaded is on screen.

#### Scenario: Alice's followers
- **WHEN** a viewer opens alice's Followers, and bob and carol follow her
- **THEN** the page is titled "Followers" with "@alice.test" under it, and lists Bob with "@bob.test" and Carol with "@carol.test"

#### Scenario: Following no one
- **WHEN** a viewer opens the Following list of an athlete who follows no one
- **THEN** the page is titled "Following" and reads "Not following anyone yet."

#### Scenario: Before the first page answers
- **WHEN** a viewer opens a follow list whose first page hasn't answered
- **THEN** the page shows its progress and no handle under the title

#### Scenario: Opening an account
- **WHEN** a viewer taps Bob in a follow list
- **THEN** Bob's profile opens, as from the shared list

### Requirement: Removing a follower on iOS
On iOS the athlete's own Followers list SHALL offer Remove beside each account exactly when the shared list does (policy `approve`), unavailable while that account's removal is written. A failed removal SHALL show above the accounts, which stay.

#### Scenario: A removal in flight
- **WHEN** alice, with policy `approve`, has tapped Remove beside bob and the removal is being written
- **THEN** bob's Remove can't be tapped and carol's can

#### Scenario: A failed removal
- **WHEN** removing a follower fails because the server can't be reached
- **THEN** the failure shows above the accounts and the accounts stay

### Requirement: Follow requests is native on iOS
On iOS the Follow requests page SHALL be drawn with the system's own list, segmented control and navigation bar, and SHALL show and offer what the shared page does, in the same words: a choice of Requests and Declined, each tab's accounts with their pictures, names and handles and its answers, the notes the shared page shows, and each tab's empty message. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Two requests
- **WHEN** alice has requests from bob and carol
- **THEN** Requests lists Bob and Carol, each with Decline and Approve

#### Scenario: The Declined tab
- **WHEN** alice chooses Declined and she has declined dave
- **THEN** the page reads "Only you can see who you declined. To them, their request still shows as Requested." and lists Dave with Approve and no Decline

#### Scenario: No requests
- **WHEN** alice has no requests
- **THEN** Requests reads "No follow requests." and Declined reads "No declined requests."

#### Scenario: Opening a request
- **WHEN** alice taps Bob's request outside its buttons
- **THEN** Bob's profile opens, as from the shared page

### Requirement: Answering requests on iOS
On iOS each request SHALL offer Decline and Approve, and each declined account Approve alone, as the shared page does. An account's answers SHALL be unavailable while its answer is written and when it can't be answered. A failed answer, or that the session must sign in again before it can decline, SHALL show above the accounts, with Sign in again where the shared page offers it.

#### Scenario: An answer in flight
- **WHEN** alice has tapped Approve on bob's request and the approval is being written
- **THEN** neither of bob's answers can be tapped, and carol's can

#### Scenario: Sign in again to decline
- **WHEN** the session can't read or write declines
- **THEN** the page says to sign in again, with Sign in again, and lists everyone as a request
