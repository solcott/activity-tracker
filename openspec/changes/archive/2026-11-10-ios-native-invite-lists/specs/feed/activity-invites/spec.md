# Spec Delta

## ADDED Requirements

### Requirement: The invites list is native on iOS
On iOS the Activity invites page SHALL be drawn with the system's own list, segmented control and navigation bar, and SHALL show what the shared page shows, in the same words: a choice of Invites and Declined, each invite with the activity's title (its type when untitled), who added the athlete and the activity's distance and time, the Declined note and each tab's empty message. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Two invites
- **WHEN** bob invited alice to his "Gravel loop" and carol to an untitled ride
- **THEN** Invites lists "Gravel loop" with "Bob added you", and the ride's type with "Carol added you", each with its distance and time

#### Scenario: The Declined tab
- **WHEN** alice chooses Declined and she declined bob's invite
- **THEN** the page reads "Only you know you declined these. You can still accept them." and lists bob's invite

#### Scenario: No invites
- **WHEN** alice has no invites
- **THEN** Invites reads "No invites" and Declined reads "No declined invites"

### Requirement: Answering invites on iOS
On iOS each pending invite SHALL offer View and, while the session can decline, Decline, and each declined invite View alone, as the shared page does. An invite's buttons SHALL be unavailable while its decline is written. View SHALL open the invite's accept page. A failed decline, or that the session must sign in again before it can decline, SHALL show above the invites, with Sign in again where the shared page offers it.

#### Scenario: A decline in flight
- **WHEN** alice has tapped Decline on bob's invite and the decline is being written
- **THEN** neither of its buttons can be tapped, and the other invite's can

#### Scenario: Sign in again to decline
- **WHEN** the session can't read or write declines
- **THEN** the page says to sign in again, with Sign in again, and offers View alone on every invite

#### Scenario: Viewing an invite
- **WHEN** alice taps View on bob's invite
- **THEN** its accept page opens, as from the shared page

### Requirement: Add people is native on iOS
On iOS the Add people page SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared page shows, in the same words: the note on what people added can see, the people added with their pictures, names, handles and Pending or Joined, the athlete's followers not yet added, and the notes that the activity is full or that only followers can be added. Its loading SHALL follow `ui/content-loading`.

#### Scenario: One joined, one to add
- **WHEN** alice added bob, who joined, and carol follows her
- **THEN** Added lists Bob as Joined with Remove, and Your followers lists Carol with Add

#### Scenario: No followers
- **WHEN** no one follows alice
- **THEN** Your followers reads "Only people who follow you can be added."

### Requirement: Adding and removing people on iOS
On iOS a pending invitee SHALL offer Cancel and a joined one Remove, and each follower Add, as the shared page does. An account's button SHALL be unavailable while it is added or removed, and every button when the session can't invite; Add SHALL also be unavailable once the activity is full. A failed add or removal, or that the session must sign in again, SHALL show above the lists, with Sign in again where the shared page offers it.

#### Scenario: A full activity
- **WHEN** alice's ride has 20 people added
- **THEN** the page reads "This activity has as many people as it can." and no follower's Add can be tapped

#### Scenario: A session that can't invite
- **WHEN** the session was signed in before invites were in its grant
- **THEN** the page says to sign in again, with Sign in again, and none of its buttons can be tapped
