# feed/activity-invites Specification

## Purpose

Lets an athlete add people who follow them to an activity they recorded, so a friend who didn't record can accept and get their own copy, linked to the original as a group activity.

## Requirements

### Requirement: Only followers can be added
An athlete SHALL be able to add another athlete to one of their own activities only while the other athlete effectively follows them and neither has blocked the other. An athlete SHALL NOT add themselves. The client SHALL offer only such athletes, and the AppView SHALL ignore an invite that names anyone else.

#### Scenario: Follower
- **WHEN** Bob effectively follows Alice and Alice adds Bob to her ride
- **THEN** Bob is invited to the ride

#### Scenario: Pending follow request
- **WHEN** Bob's follow of Alice awaits her approval and an invite naming Bob is written
- **THEN** Bob is served no invite, and Alice's page doesn't list him

#### Scenario: Someone Alice follows
- **WHEN** Alice follows Carol and Carol doesn't follow Alice
- **THEN** Alice's add list doesn't offer Carol

#### Scenario: Block
- **WHEN** Bob follows Alice and Alice has blocked Bob
- **THEN** Alice can't add Bob, and Bob is served no invite

### Requirement: At most 20 invitees per activity
An activity SHALL have at most 20 invitees, pending and accepted together. The client SHALL NOT offer more, and the AppView SHALL ignore invites beyond the first 20 by creation time.

#### Scenario: Twenty-first
- **WHEN** an activity already has 20 invites and a 21st is written
- **THEN** the AppView ignores the 21st

### Requirement: The invite is private
An invite SHALL be an `feed.activityInvite` record in the inviter's `self` space, naming the activity and the invitee, never in a repo or other space. The AppView SHALL serve it only to the inviter and the invitee. No view served to anyone else SHALL reveal that the invite exists.

#### Scenario: Stored in self
- **WHEN** Alice adds Bob to a Public ride
- **THEN** Alice's `self` space holds an invite naming the ride and Bob, and her repo holds no record of it

#### Scenario: Third viewer
- **WHEN** Carol opens Alice's ride that has a pending invite to Bob
- **THEN** nothing Carol is served names Bob

### Requirement: Adding is not queued
Adding and cancelling invitees SHALL write to the PDS at once and report a failure, not queue the write. A failed write SHALL leave the earlier invitees in force. A session whose `space:` scope lacks the invite collection SHALL NOT offer adding, and SHALL tell the athlete to sign in again.

#### Scenario: Offline add
- **WHEN** Alice adds Bob while offline
- **THEN** the app reports that it couldn't add him, and Bob isn't invited

#### Scenario: Old scope
- **WHEN** a session's `space:` scope doesn't include `feed.activityInvite`
- **THEN** the activity page doesn't offer Add people and says to sign in again

### Requirement: The owner manages invitees
On their own activity's page, the athlete SHALL see an Add people action that lists their effective followers they may add, and SHALL see each invitee as Pending or Joined. They SHALL be able to cancel a pending invite, which deletes it. The page SHALL say that people added can see the activity's start time, route and speed.

#### Scenario: Cancel
- **WHEN** Alice cancels her pending invite to Bob
- **THEN** the invite is deleted, and Bob no longer sees it

#### Scenario: Joined
- **WHEN** Bob accepted Alice's invite
- **THEN** Alice's page lists Bob as Joined

#### Scenario: Declined stays pending
- **WHEN** Bob declined Alice's invite
- **THEN** Alice's page still lists Bob as Pending

### Requirement: Invites lapse
An invite SHALL lapse, and no longer be served to anyone as pending, when the invitee stops effectively following the inviter, either blocks the other, or the activity is deleted. An invite that lapsed SHALL be served again if the condition ends while the invite still exists.

#### Scenario: Unfollow
- **WHEN** Bob unfollows Alice while her invite is pending
- **THEN** Bob's invites no longer list it, and Alice's page no longer lists Bob

#### Scenario: Activity deleted
- **WHEN** Alice deletes a ride with a pending invite to Bob
- **THEN** Bob's invites no longer list it

### Requirement: Invitees see their pending invites
The AppView SHALL serve a signed-in athlete the invites naming them that are pending and not lapsed, newest first, with paging, each with the inviter and a summary of the activity. The invitee's own profile SHALL show an Activity invites entry with the number of those they haven't declined, and no entry when there are none.

#### Scenario: Inbox
- **WHEN** Alice and Dan each invite Bob to a ride
- **THEN** Bob's profile shows Activity invites with 2, and the list shows both rides, newest first

#### Scenario: Nothing pending
- **WHEN** Bob has no pending invites
- **THEN** his profile shows no Activity invites entry

### Requirement: An invite grants the invitee what they copy
For a pending invite, the AppView SHALL serve the invitee the activity's core data, its Start time, Map and Pace/Speed data and the charts in those categories, from every tier, even ones the invitee isn't admitted to. It SHALL serve the trimmed polyline only, never a full track. It SHALL serve nothing in any other category, and SHALL grant nothing outside the invite query.

#### Scenario: Map narrower than the follower sees
- **WHEN** Alice's Followers ride has its Map at Only me and she invites Bob, who follows her
- **THEN** Bob's invite shows the ride's trimmed route, and Bob's view of the ride itself still has no map

#### Scenario: Heart rate not granted
- **WHEN** Alice's ride has heart rate at Followers and she invites Bob
- **THEN** Bob's invite holds no heart rate

#### Scenario: Grant ends
- **WHEN** Alice cancels the invite
- **THEN** Bob is served none of the granted data

### Requirement: Declining is private
Declining SHALL write an `feed.activityInviteDecline` naming the invite to the invitee's `self` space and nowhere else, and the AppView SHALL NOT store it. A declined invite SHALL leave the invitee's pending list and count. The invitee SHALL be able to accept a declined invite later from a Declined list while it hasn't lapsed.

#### Scenario: Decline
- **WHEN** Bob declines Alice's invite
- **THEN** his Activity invites count drops by one, the invite shows under Declined, and nothing Alice is served says he declined

#### Scenario: Accept after declining
- **WHEN** Bob opens Declined and accepts Alice's invite
- **THEN** his copy is created as for any accept

### Requirement: Accepting creates the invitee's own activity
Accepting SHALL create a new activity owned by the invitee, written like their other activities, whose record names the original in `copyOf`. It SHALL hold the original's sport, sub-sport, start and UTC offset, elapsed and moving time, distance, elevation gain and loss, title, route polyline, and the Pace/Speed and core charts the invite served.

#### Scenario: Accept
- **WHEN** Bob accepts Alice's invite to a 42 km ride
- **THEN** Bob has a 42 km ride starting when Alice's did, with her route and speed chart, naming her ride in `copyOf`

### Requirement: The copy leaves out the inviter's personal data
The copy SHALL NOT hold the original's heart rate, power, calories, cadence and running form, physiology, temperature, dive data, gear, devices, sensors, photos, description, laps, source file or full track.

#### Scenario: Ride with heart rate and photos
- **WHEN** Bob accepts an invite to a ride with Alice's heart rate, power and photos
- **THEN** Bob's copy holds none of them

### Requirement: The invitee chooses the copy's visibility
Before accepting, the invitee SHALL choose the copy's visibility and each copied category's visibility, prefilled from their own activity defaults, as on manual entry. The copy's title SHALL be prefilled from the original's and editable.

#### Scenario: Defaults
- **WHEN** Bob's default activity visibility is Followers and Alice's ride is Public
- **THEN** the accept screen offers Followers, and accepting stores Bob's copy at Followers

### Requirement: The invitee's privacy zones apply to the copy
The copy's polyline SHALL be the served route trimmed again by the invitee's own privacy zones and hide-start/end distance. With no zones read this session, the copy SHALL be written with no polyline and get a trimmed one once they are read.

#### Scenario: Ride from the invitee's house
- **WHEN** Bob's privacy zone covers where the ride started
- **THEN** Bob's copy's route leaves out the part inside his zone

### Requirement: Overlap warning on accept
Before accepting, the accept screen SHALL search the invitee's own activities for one whose time overlaps the original's by at least half of the shorter one, as import does. When it finds one, it SHALL warn that the invitee may already have this activity, and still allow accepting.

#### Scenario: Already recorded
- **WHEN** Bob recorded the first hour of the ride before his watch died and opens Alice's invite
- **THEN** the accept screen warns that he may already have it

### Requirement: An invite is accepted once
An invitee SHALL be able to accept an invite once. While a copy naming the original exists, the invite SHALL show as Joined and SHALL NOT be offered to the invitee again. Only the first copy indexed for an invite SHALL be paired. Any later copy SHALL be an ordinary activity with no pair.

#### Scenario: Accept on two devices
- **WHEN** Bob accepts the same invite on his phone and, before it syncs, on his laptop
- **THEN** the first copy indexed is paired with Alice's ride, and the second is an activity with no pair

### Requirement: The copy is independent
Once created, the copy SHALL be the invitee's own activity: they SHALL be able to edit and delete it like any other. Editing or deleting the original SHALL NOT change the copy, and editing the copy SHALL NOT change the original. Deleting either SHALL only drop their pair. Deleting the invite SHALL NOT delete the copy.

#### Scenario: Original edited
- **WHEN** Alice retitles her ride after Bob accepted
- **THEN** Bob's copy keeps its title

#### Scenario: Original deleted
- **WHEN** Alice deletes her ride
- **THEN** Bob's copy stays, listing no partner

### Requirement: copyOf is untrusted
The AppView SHALL treat a `copyOf` link as an invite pair only when the copy's author is named by an invite for the linked activity that existed and wasn't lapsed when the copy was indexed. Any other `copyOf` SHALL be ignored, and the activity shown as an ordinary activity.

#### Scenario: Forged copy
- **WHEN** Carol, who was never invited, writes an activity whose `copyOf` names Alice's ride
- **THEN** Carol's activity isn't paired with Alice's, and nothing about Alice's ride is served to Carol beyond what she may see

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
