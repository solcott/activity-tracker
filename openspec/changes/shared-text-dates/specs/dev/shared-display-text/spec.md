## MODIFIED Requirements

### Requirement: A written-out date reads the same in both UIs
A calendar date a UI shows, as a board entry, a segment effort, a threshold history row, the gear form's acquired, mounted-on and retired dates, the gear page's mount dates, the feed filter's custom range, the upload queue's unnamed activities and edit profile's birth date show it, SHALL be chosen by shared code: the short month name, the day and the year, never grouped. A `YYYY-MM-DD` text that doesn't parse SHALL read as itself. No UI SHALL write a date as `YYYY-MM-DD`.

#### Scenario: An effort's day
- **WHEN** an effort's date is `2026-01-05`
- **THEN** both UIs read "Jan 5, 2026"

#### Scenario: A date that doesn't parse
- **WHEN** an entry's date is `soon`
- **THEN** both UIs read "soon"

#### Scenario: A gear item's dates
- **WHEN** a chain was acquired on 2026-07-01, has been mounted on a gravel bike since 2026-07-01, and was retired on 2026-09-01
- **THEN** both UIs read "Jul 1, 2026", "Mounted on Gravel bike since Jul 1, 2026" and "Retired Sep 1, 2026"

#### Scenario: A component's mounts on the gear page
- **WHEN** a component was mounted from 2026-03-01 to 2026-06-01 and again since 2026-06-01, in the viewer's time zone
- **THEN** both UIs read "Mar 1, 2026 – Jun 1, 2026" and "Since Jun 1, 2026"

#### Scenario: A custom range in the feed filter
- **WHEN** the athlete limits the feed to 2026-03-01 through 2026-03-15
- **THEN** the date chip reads "Mar 1, 2026 – Mar 15, 2026" in both UIs

#### Scenario: An unnamed activity in the upload queue
- **WHEN** an untitled ride that started on 2026-10-26 in the viewer's time zone waits in the queue
- **THEN** both UIs read "Ride, Oct 26, 2026"

#### Scenario: A birth date being edited
- **WHEN** edit profile holds the birth date `1990-04-12`
- **THEN** both UIs read "Apr 12, 1990"

## ADDED Requirements

### Requirement: Times and lengths of time are the shared texts in both UIs
Where a page shows an activity's start with how long it took (the combine picker's rows), a lift's rides and time on the activity page, or a segment leaderboard entry's time, both UIs SHALL show the text shared code chooses for it. Neither UI SHALL choose the field or the format itself.

#### Scenario: A combine candidate
- **WHEN** a candidate started today at 7:05 AM on its own clock and took 1:02:03
- **THEN** both UIs read "Today at 7:05 AM · 1:02:03"

#### Scenario: A lift
- **WHEN** an activity rode a lift 3 times for 12 minutes 5 seconds in all
- **THEN** both UIs read "3 rides" and "12:05"

### Requirement: A date picker stops at today on the shown clock
A date picker that offers only past days (birth date, a gear item's acquired day, the feed's custom range) SHALL end at today on the clock the UI shows, which previews and snapshots fix. It SHALL NOT end at today on the device's own clock.

#### Scenario: A preview's fixed clock
- **WHEN** a preview fixes the clock at 2026-11-12 and opens the birth date picker
- **THEN** the last day it offers is 2026-11-12 in both UIs, whatever the device's date
