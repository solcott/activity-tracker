# Spec Delta

## Purpose

Lets an athlete add people who follow them to an activity they recorded, so a friend who didn't record can accept and get their own copy, linked to the original as a group activity.

## ADDED Requirements

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
