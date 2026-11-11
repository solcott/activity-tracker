# Spec Delta

## ADDED Requirements

### Requirement: An activity card is native on iOS
A native page that lists activities SHALL draw each as the native activity card, which SHALL show what the shared card shows, in the same order and words: the author line, the moderation notices, the icon and title, a dive's site, the description, the stats, the owner's locks, the route and photos, and Like with the counts.

#### Scenario: A follower's ride
- **WHEN** a native page lists Bob's titled public ride with a route, 3 likes and 1 comment
- **THEN** its card shows Bob, its start, the ride's icon and title, its distance, time and speed, the route, and "3 likes · 1 comment" beside Like

#### Scenario: An untitled run
- **WHEN** a card shows a run with no title
- **THEN** its title reads the activity's type, and VoiceOver reads no separate label for its icon

#### Scenario: A dive
- **WHEN** a card shows a dive at "Blue Hole"
- **THEN** it shows the site under the title and the dive's depth, dive time and water temperature as its stats

### Requirement: The native card's taps do what the shared card's do
On iOS, tapping the card SHALL open the activity, its author line the athlete (unless the page is the athlete's own), a photo the gallery at that photo, and Like SHALL like or unlike it. Like SHALL be offered only on others' activities and SHALL show whether the viewer gave one.

#### Scenario: Liking
- **WHEN** the viewer taps Like on Bob's activity they haven't liked
- **THEN** the like is sent, as from the shared card, and Like shows as given once the listed activity has it

#### Scenario: Own activity
- **WHEN** a card shows the viewer's own activity
- **THEN** it offers no Like and shows only the counts

### Requirement: The native card's photos follow their labels
On iOS a card's photos SHALL be blurred under a media label with the label's name and Show until the viewer reveals them on that page, a tap on a covered photo revealing rather than opening it; the owner's taken-down activity SHALL show placeholders. The athlete's avatar SHALL be blurred under a media label on their account.

#### Scenario: Revealing
- **WHEN** the viewer taps a card's photo blurred as Graphic media
- **THEN** that card's photos show, and a second tap opens the gallery

#### Scenario: Taken down
- **WHEN** the owner sees their own taken-down activity
- **THEN** its card reads that it was taken down and its photos are placeholders

### Requirement: The native card's route is drawn on the device
On iOS a card with a route the viewer was served SHALL draw it as a line fitted to its tile with its breaks, dashed connectors and markers, as the shared line preview draws it, in the app's accent colour, and SHALL send the route nowhere. With photos the route SHALL be the first tile of a row the viewer scrolls sideways.

#### Scenario: Ride with a break
- **WHEN** a card shows a ride whose line breaks once
- **THEN** its route tile draws two lines joined by a dashed connector, with start, stop, resume and end markers

#### Scenario: No route served
- **WHEN** a card shows a run whose Map category the viewer wasn't served
- **THEN** it has no route tile

### Requirement: A paged list can group its items under headings
A native paged list SHALL be able to lay its loaded items out in sections that shared code chooses, each with an optional heading, in the order given, and SHALL keep asking for the next page once the last item is on screen.

#### Scenario: Months
- **WHEN** a paged list's items fall in two months
- **THEN** it shows each month's heading over its items, the newer first

#### Scenario: The last item of the last section
- **WHEN** the last loaded item is scrolled on screen and the list can load more
- **THEN** the next page is asked for
