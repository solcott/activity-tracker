# feed/activity-start-time Specification

## Purpose

How an activity's start date and time read wherever the activity is shown, on the feed card and the activity page: a relative day when it is recent, a written-out date otherwise, always on the clock of the place where the activity happened.

## Requirements

### Requirement: Feed cards show the start
Every activity card in a feed or an athlete's activity list SHALL show the activity's start, below the author. Cards SHALL stay in their feed's order, newest uploaded first; the start shown SHALL NOT reorder them.

#### Scenario: Card in the feed
- **WHEN** a viewer opens the feed holding a ride that started at 7:12 AM today
- **THEN** the ride's card shows "Today at 7:12 AM" below the athlete's name

#### Scenario: Older activity uploaded later
- **WHEN** an athlete uploads today a run that started on October 3, after uploading a ride that started yesterday
- **THEN** the run's card is above the ride's, and shows "October 3, 2026 at …"

### Requirement: The page shows the start in its header
The activity page SHALL show the start in its header, below the author and above the title, and SHALL NOT list it again in the stats list. On the owner's page, a Start time narrower than the activity SHALL be marked with its visibility beside the start. The import preview SHALL show the start the same way, above the hero.

#### Scenario: Someone else's ride
- **WHEN** a viewer opens a ride that started at 7:12 AM today
- **THEN** "Today at 7:12 AM" shows under the athlete's name, and the stats list's Timing group has no start time row

#### Scenario: Owner with Start time Only me
- **WHEN** the athlete opens their Public run whose Start time is Only me
- **THEN** the header's start is marked Only me

#### Scenario: Import preview
- **WHEN** the athlete picks a FIT file to import
- **THEN** the preview shows the file's start above the hero

### Requirement: Start wording
The start SHALL read as a day, " at ", and the time of day: "Today at 2:00 PM", "Yesterday at 2:00 PM", or the month's full name, the day of the month and the year, as in "October 26, 2026 at 2:00 PM". The feed card and the activity page SHALL use the same wording. The wording SHALL NOT be configurable.

#### Scenario: Earlier this year
- **WHEN** a viewer sees an activity that started at 2:00 PM on October 26, 2026, on October 28, 2026
- **THEN** it reads "October 26, 2026 at 2:00 PM" on the card and on the page

#### Scenario: Yesterday
- **WHEN** a viewer sees an activity that started at 6:30 PM the day before
- **THEN** it reads "Yesterday at 6:30 PM"

#### Scenario: Today
- **WHEN** a viewer sees an activity that started at 7:12 AM the same day
- **THEN** it reads "Today at 7:12 AM"

### Requirement: Time follows the device's clock style
The time of day SHALL follow the viewer's 12- or 24-hour clock setting.

#### Scenario: 24-hour clock
- **WHEN** a viewer whose device uses the 24-hour clock sees an activity that started at 2:05 PM today
- **THEN** it reads "Today at 14:05"

### Requirement: The activity's own clock
The day and time SHALL be those where the activity happened, by its stored UTC offset, whatever the viewer's time zone. "Today" and "Yesterday" SHALL compare the activity's local date with the current date at that same offset. An activity with no stored UTC offset SHALL be read in the viewer's time zone.

#### Scenario: Viewer in another zone
- **WHEN** a viewer in UTC−5 sees a ride with a UTC offset of +2:00 that started at 07:00 UTC
- **THEN** it reads "… at 9:00 AM", not "… at 2:00 AM"

#### Scenario: Still today where it happened
- **WHEN** a run with a UTC offset of −7:00 started at 11:30 PM local time, and a viewer in UTC sees it at 07:00 UTC the next day, which is midnight at −7:00
- **THEN** it reads "Yesterday at 11:30 PM"; seen at 06:59 UTC, it reads "Today at 11:30 PM"

#### Scenario: No stored offset
- **WHEN** a viewer in UTC−6 sees a GPX import with no UTC offset that started at 15:10 UTC
- **THEN** it reads "… at 9:10 AM"

### Requirement: Hidden start shows the day only
A viewer who isn't shown an activity's start time SHALL see only the day, in the same wording: "Today", "Yesterday" or the date, with no time of day. The day SHALL be the activity's local day, as the record holds it.

#### Scenario: Start time Only me
- **WHEN** a follower sees a Public run from today whose Start time is Only me
- **THEN** its card and page read "Today", with no time

#### Scenario: Owner sees the time
- **WHEN** the athlete sees that run
- **THEN** it reads "Today at 6:12 AM"

#### Scenario: Older hidden start
- **WHEN** a viewer not shown the start sees a ride from October 26, 2026, two days later
- **THEN** it reads "October 26, 2026"
