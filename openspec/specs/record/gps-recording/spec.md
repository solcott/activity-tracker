# record/gps-recording Specification

## Purpose

Recording a GPS activity on a phone: it keeps recording with the screen off or the app in the background, survives the app being killed, leaves pauses out of its totals, and is never lost on the way to becoming a published activity.

## Requirements

### Requirement: Recording continues in the background
While a recording is in progress on Android or iOS, the app SHALL keep receiving positions at the same rate with the screen off, the phone locked, or another app in front, until the athlete pauses, finishes or discards it. The app SHALL ask only for location access while in use, never for access all the time.

#### Scenario: Phone locked in a pocket
- **WHEN** an athlete starts a 45-minute run, locks the phone and puts it away
- **THEN** the recording has positions every second or so for the whole 45 minutes

#### Scenario: Switching apps
- **WHEN** the athlete opens a music app for 10 minutes during a ride
- **THEN** the recording has no gap for those 10 minutes

#### Scenario: Permission asked
- **WHEN** an athlete records for the first time
- **THEN** the app asks for location access while using the app, not all the time

### Requirement: The system shows that a recording is in progress
While a recording is in progress or paused, Android SHALL show an ongoing notification with the recording's elapsed time and distance, which opens the recording screen when tapped and offers Pause or Resume. iOS SHALL show its background location indicator. Recording SHALL NOT depend on the notification being allowed.

#### Scenario: Pausing from the notification
- **WHEN** the athlete taps Pause on the notification during a ride
- **THEN** the recording pauses, and the notification then offers Resume

#### Scenario: Notifications not allowed
- **WHEN** the athlete declined notification permission on Android 13 and starts a run
- **THEN** the run still records in the background

#### Scenario: Finishing needs the app
- **WHEN** the athlete looks at the notification
- **THEN** it offers no Finish or Discard, which are made on the recording screen

### Requirement: Pauses are left out of the totals
A recording's distance, moving time, elevation gain and top speed SHALL count only what was recorded while it wasn't paused. After a resume, they SHALL be measured from the first position after it. Its elapsed time SHALL leave out the time paused.

#### Scenario: Walking to a cafe while paused
- **WHEN** an athlete pauses, walks 400 m in 10 minutes, and resumes
- **THEN** the recording's distance and moving time grow by nothing for that walk

#### Scenario: Top speed across a pause
- **WHEN** an athlete pauses, is driven 2 km in 3 minutes, and resumes
- **THEN** the recording's top speed is unchanged by the drive

### Requirement: Elevation gain counts each climb once
A recording's elevation gain SHALL be the sum of its climbs between accepted positions, counted once each whatever the athlete taps meanwhile, and SHALL ignore changes of 3 m or less.

#### Scenario: Pausing during a climb
- **WHEN** the athlete taps Pause just as a position 5 m higher than the last arrives
- **THEN** the recording's gain grows by 5 m, not 0 m or 10 m

### Requirement: Recordings are saved as they happen
Every position a recording accepts, and every start, pause and resume, SHALL be saved on the device as it happens, for the account that started the recording. A recording SHALL be removed from the device only once it is queued for publishing or the athlete discards it.

#### Scenario: Battery dies
- **WHEN** the phone switches off 50 minutes into a run
- **THEN** the run's first 50 minutes are still on the device when it starts again

#### Scenario: Discarded
- **WHEN** the athlete discards a recording
- **THEN** nothing of it is left on the device

### Requirement: An unfinished recording is offered back
When the app starts and finds an unfinished recording of the signed-in account that it isn't recording, it SHALL offer the athlete to resume it, finish and save it, or discard it, before a new recording can start. Another account's unfinished recording SHALL NOT be shown.

#### Scenario: App killed mid-ride
- **WHEN** the system kills the app 30 minutes into a ride and the athlete opens it again
- **THEN** the app offers to resume, save or discard the 30-minute ride

#### Scenario: Another account
- **WHEN** a different account signs in on the phone that holds an unfinished recording
- **THEN** that account is offered nothing, and the recording waits for its own account

### Requirement: A recovered recording resumes as from a pause
Resuming a recovered recording SHALL count the time between its last saved position and the resume as paused, and SHALL measure from the first position after it, as any resume does. Its map line SHALL break there as `feed/map-breaks` decides for any gap.

#### Scenario: Resumed after a crash
- **WHEN** a ride's last saved position was 8 minutes before the athlete resumes it, 3 km on
- **THEN** its elapsed and moving time leave out the 8 minutes, its distance leaves out the 3 km, and its map breaks between the two positions

### Requirement: Signing out keeps the recording for its account
Signing out during a recording SHALL stop recording and keep the recording on the device as unfinished, to be offered back when the same account signs in again. A session that ends because it expired SHALL NOT stop the recording: it keeps recording, and signing in again to the same account returns to it as it is. When a different account signs in while another account's recording is running, that recording SHALL stop and be kept for its own account, as if its account had signed out.

#### Scenario: Sign out and back in
- **WHEN** an athlete signs out 20 minutes into a run and signs in to the same account later
- **THEN** the app offers to resume, save or discard the 20-minute run

#### Scenario: Session expires during a run
- **WHEN** an athlete's session expires 20 minutes into a run, and they sign in to the same account 5 minutes later
- **THEN** the run kept recording throughout, and the record screen shows it still running with all 25 minutes

#### Scenario: Another account signs in after an expiry
- **WHEN** alice's session expires during a run, and bob signs in on the same device
- **THEN** alice's run stops and is kept for her, bob's record screen shows no recording, and alice is offered her run when she signs in again

### Requirement: Finishing never loses the recording
Finishing a recording SHALL keep it on the device until its activity is queued for publishing. If publishing is refused, the athlete SHALL see why and be able to try again, change what was refused, or discard the recording.

#### Scenario: Private recording on a session without Spaces
- **WHEN** an athlete finishes a run set to Followers, and publishing is refused because the session can't write to spaces
- **THEN** the run is still on the recording screen with the reason, and choosing Public and finishing again publishes it

#### Scenario: Offline finish
- **WHEN** an athlete finishes a ride with no connection
- **THEN** the ride is queued, removed from the recording journal, and sent when the phone is back online
