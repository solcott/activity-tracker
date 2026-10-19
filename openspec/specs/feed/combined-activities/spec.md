# feed/combined-activities Specification

## Purpose

Lets an athlete turn several of their own recordings of what was one activity into a single combined activity. The recordings may be at the same time on different devices, or one after another across a device change or a break. The combined activity counts once and is built from its members, and each member can still be opened on its own.

## Requirements

### Requirement: Combined activity lists its members
A combined activity SHALL be an activity of its own whose record lists between 2 and 10 members, each another activity by the same athlete. Combining SHALL NOT rewrite any member's record, streams, track, original file or devices.

#### Scenario: Combine two rides
- **WHEN** an athlete combines their watch's ride with their bike computer's recording of it
- **THEN** a new combined activity lists both as members, and each member's record and streams are unchanged

#### Scenario: Too many members
- **WHEN** an athlete tries to add an eleventh activity to a combined activity
- **THEN** the app refuses and the combined activity keeps its ten members

### Requirement: Membership links are checked
The AppView SHALL treat a combined activity's members as untrusted. It SHALL ignore a member that:
- belongs to another athlete;
- is itself a combined activity;
- is already a member of an earlier-created combined activity;
- has a different visibility from the combined activity.

An ignored member SHALL show as an activity in its own right.

#### Scenario: Someone else's activity
- **WHEN** a combined activity's record lists another athlete's activity as a member
- **THEN** that activity still shows in its athlete's feeds, and the combined activity's page doesn't list it

#### Scenario: Claimed twice
- **WHEN** two of an athlete's combined activities list the same member
- **THEN** it counts as a member of the one created first only

### Requirement: Feeds show the combined activity once
Feeds, the athlete's activity list and their activity count SHALL show a combined activity in place of its members. They SHALL NOT show a member on its own while it is a member. Queries by start time, which look for duplicates, SHALL still return members, each naming the combined activity it is part of.

#### Scenario: Timeline
- **WHEN** a follower's timeline covers a day on which the athlete combined two recordings of one ride
- **THEN** it shows one card, the combined activity's

#### Scenario: Activity count
- **WHEN** an athlete with three activities combines two of them
- **THEN** their profile counts two activities

#### Scenario: Looking for duplicates
- **WHEN** a client queries the athlete's activities by start time over the day of a combined ride
- **THEN** it gets the combined ride and both members, each member naming the combined ride

### Requirement: Members open on their own
A combined activity's page SHALL list its members, oldest first, each named by its device or start time, and each SHALL open on its own page with its own figures and charts. A member's page SHALL link to the combined activity.

#### Scenario: See the bike computer's recording
- **WHEN** an athlete opens the combined ride and taps its Edge 840 member
- **THEN** the Edge 840 recording's page shows its own figures and charts, with a link back to the combined ride

### Requirement: Each channel comes from its top-ranked member
For each channel, at each moment, the combined activity SHALL take the sample of the highest-ranked member with a sample then. With no ranking from the athlete, members SHALL rank by how much of the channel they recorded, most first.

#### Scenario: Strap heart rate and meter power
- **WHEN** an athlete combines a watch recording with heart rate only and a bike computer recording with power and heart rate that has more heart rate samples
- **THEN** the combined activity's heart rate is the bike computer's and its power is the bike computer's

#### Scenario: Device dies
- **WHEN** an athlete combines a watch ride that stops at 10:40 with a phone ride from 10:38 to 12:00
- **THEN** the combined activity's heart rate comes from the watch until 10:40, and from the phone after that

### Requirement: A member counts only while it recorded
A member SHALL count as recording only where it has samples (its streams, else its track), in stretches with no gap longer than two minutes. A stretch shorter than a minute, apart from the rest, SHALL be left out. Grouping members, legs, figures, charts and the map SHALL follow these stretches, not the span the member's file states. A member with no samples SHALL count for its whole span.

#### Scenario: Bike computer dies, watch finishes the ride
- **WHEN** an athlete combines an Edge ride whose file spans 19:31 to 21:04 but which recorded only until 20:05, plus a few points at 21:04, with a watch ride from 20:28 to 20:57
- **THEN** the combined ride runs 19:31 to 20:57, has two legs (the Edge's, then the watch's), and its moving time, distance and elevation gain add both up

#### Scenario: Both devices paused together
- **WHEN** an athlete combines a watch and a bike computer recording of one ride that both auto-paused for 10 minutes at a café
- **THEN** the combined ride has no legs, since the same devices recorded before and after the pause

### Requirement: Back-to-back members join end to end
Members that follow one another SHALL join into one timeline from the first start to the last end. Each member's distance SHALL continue from the combined distance at the moment it starts. The time between members SHALL hold no samples.

#### Scenario: Ski day with lunch
- **WHEN** an athlete combines a ski morning of 9:00 to 11:45 covering 22 km with an afternoon of 13:00 to 15:30 covering 18 km
- **THEN** the combined activity spans 9:00 to 15:30, its distance chart reaches 40 km, and no samples fall between 11:45 and 13:00

### Requirement: Back-to-back members show as legs
When members follow one another, the combined activity SHALL show each as a leg with its own times and distance. Its map line SHALL break between members instead of joining them.

#### Scenario: Shuttle runs
- **WHEN** an athlete combines three MTB runs taken by shuttle
- **THEN** the combined activity shows three legs, and its map draws three lines with no line along the shuttle road

### Requirement: Summary figures come from the combined data
The combined activity's summary figures SHALL reflect what it combines:
- **Elapsed time:** from when the first member started recording to when the last stopped.
- **Moving time:** the members' moving time added up, without counting overlapping time twice.
- **Distance and elevation gain:** each time counted once, taken from the member each comes from.
- **Averages and maxima:** worked out from the combined channels.

#### Scenario: Lunch counts in elapsed time
- **WHEN** an athlete combines a 2 h 45 min morning and a 2 h 30 min afternoon with a 1 h 15 min lunch between
- **THEN** the combined activity's elapsed time is 6 h 30 min, and its moving time is at most 5 h 15 min

#### Scenario: Two devices don't double distance
- **WHEN** an athlete combines a watch and a bike computer recording of the same 50 km ride
- **THEN** the combined distance is about 50 km, not 100 km

### Requirement: Laps, sets and dive data come from one member
For laps, legs, sets, pool lengths and dive data:
- When members overlap in time, the combined activity SHALL take each kind from the highest-ranked member that has it.
- When members are back to back, it SHALL take each member's in turn.

Dive data here covers alerts, gas switches, tanks and settings.

#### Scenario: Laps from the bike computer
- **WHEN** an athlete combines a watch ride without laps and a bike computer ride with five laps
- **THEN** the combined activity has the bike computer's five laps

### Requirement: Combined activity starts from the activity combined into
When the athlete combines another activity into one of theirs, the new combined activity SHALL take that activity's title, description, photos, sport and visibility. Editing them later SHALL change the combined activity only, not its members.

#### Scenario: Title kept
- **WHEN** an athlete, on their ride titled "Lunch loop", combines it with a bike computer recording titled "Morning Ride"
- **THEN** the combined activity is titled "Lunch loop", and the bike computer recording keeps "Morning Ride"

### Requirement: Members share the combined activity's visibility
Combining SHALL move each member to the combined activity's visibility. Changing a combined activity's visibility SHALL move its members with it. Each member's detail categories SHALL be capped at the combined activity's visibility.

#### Scenario: Combine a Public recording into a Followers activity
- **WHEN** an athlete combines a Public recording into their Followers activity
- **THEN** the recording becomes Followers

### Requirement: Combined detail categories never widen a member's
Each detail category of a new combined activity SHALL default to the narrowest visibility any member has for it.

#### Scenario: Heart rate kept private
- **WHEN** an athlete combines a ride whose heart rate is Only me with a ride whose heart rate is Public
- **THEN** the combined activity's heart rate is Only me

### Requirement: Likes and comments on members count
A combined activity's like count, comment count and comment list SHALL include those on its members, under the same rules for who may see them as on the combined activity.

#### Scenario: Like given before combining
- **WHEN** a follower liked a ride that the athlete later combines with another recording
- **THEN** the combined activity's like count includes that like

### Requirement: Combine with an activity from the same day
On their own activity's page, an athlete SHALL be able to combine it with another of their activities that started within 12 hours of it. They pick from a list of those activities, each showing its sport, start and device. An activity that is already a member of another combined activity, or is a combined activity itself, SHALL NOT be offered. On a combined activity, combining SHALL add the picked activity as a member.

#### Scenario: Combine after lunch
- **WHEN** an athlete opens their morning ski activity and picks their afternoon one from the list
- **THEN** a combined activity of the two replaces both in their feed

#### Scenario: Add a third
- **WHEN** an athlete adds a third recording to a combined activity of two
- **THEN** the combined activity lists three members and is rebuilt from all three

### Requirement: Remove a member or split
The athlete SHALL be able to remove one member from a combined activity of three or more members, which is rebuilt without it. They SHALL also be able to split a combined activity, which deletes it and leaves every member as an activity of its own. Removing a member from a combined activity of two SHALL split it.

#### Scenario: Split
- **WHEN** an athlete splits a combined activity of two rides
- **THEN** the combined activity is gone, and both rides show in their feed again with their own figures

### Requirement: Delete with or without members
Deleting a combined activity SHALL ask whether to delete its members too. Deleting with members SHALL delete each member with its streams, track, original file and devices. Deleting without members SHALL leave each as an activity of its own. Deleting a member SHALL rebuild the combined activity without it, or split it if one member would be left.

#### Scenario: Delete everything
- **WHEN** an athlete deletes a combined activity together with its members
- **THEN** the combined activity and every member are gone, with their streams and original files

#### Scenario: Delete one of two members
- **WHEN** an athlete deletes one member of a combined activity of two
- **THEN** the combined activity is gone, and the other member stands on its own
