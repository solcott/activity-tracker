# feed/activity-trim Specification

## Purpose

Lets an athlete cut the start or end of their own activity: what the recorder or the file caught before they set off or after they stopped. They choose the kept range by time or distance, see the cut on the map and in the totals, and save an activity rebuilt from only what's kept.

## Requirements

### Requirement: Only the owner can trim, and only activities that can be trimmed
The activity page's ⋯ menu SHALL offer "Trim start or end" only to the activity's owner. It SHALL NOT be offered for:
- a combined activity;
- an activity that is a member of a combined activity;
- a multisport activity;
- a dive;
- a workout with sets.

Every other activity SHALL be trimmable: one with GPS, one with distance but no GPS, and a manual entry.

#### Scenario: Owner's ride
- **WHEN** an athlete opens the ⋯ menu on their own GPS ride
- **THEN** the menu offers "Trim start or end"

#### Scenario: Someone else's activity
- **WHEN** an athlete opens the page of another athlete's activity
- **THEN** no trim action is offered

#### Scenario: Combined activity
- **WHEN** an athlete opens the ⋯ menu on their own combined activity, or on one of its members
- **THEN** the menu doesn't offer a trim

#### Scenario: Dive
- **WHEN** an athlete opens the ⋯ menu on their own dive
- **THEN** the menu doesn't offer a trim

### Requirement: The kept range is chosen on a profile, by time or distance
The trim screen SHALL show one profile of the activity with a handle at each end of the kept range:
- elevation when it has altitude;
- else speed or pace when it has distance;
- else a plain bar of its elapsed time.

The athlete SHALL be able to move either handle by dragging it, by tapping the profile (which moves the nearer handle), or with the keyboard. The handles SHALL NOT cross, and SHALL always keep at least one minute between them.

A Time | Distance choice SHALL set the profile's axis and the readouts. Distance SHALL be offered only when the activity has distance. In distance mode a handle placed at a distance SHALL also cut any standing time at that point.

The parts outside the kept range SHALL be shaded on the profile.

#### Scenario: Drag the end handle by time
- **WHEN** the athlete drags the end handle of a ride from 1:58:40 to 1:44:30
- **THEN** the end readout shows 1:44:30 and its clock time, and the profile shades everything after it

#### Scenario: Trim by distance
- **WHEN** the athlete chooses Distance and moves the start handle to 0.4 km
- **THEN** the start readout shows 0.40 km, and the kept range starts at the last point at or before 0.4 km

#### Scenario: Handles can't cross
- **WHEN** the athlete drags the start handle past the end handle
- **THEN** the start handle stops one minute before the end handle

#### Scenario: Manual entry
- **WHEN** an athlete trims a manual entry
- **THEN** Distance is shown disabled with the reason that no distance or GPS was recorded, and the handles move the start and end times

### Requirement: Readouts show where each end lands and accept exact values
Each end SHALL have a readout:
- in time mode, the time into the activity and the clock time;
- in distance mode, the distance from the start and the clock time.

Tapping a readout SHALL open an exact value for that end, which the athlete can type or step. A step SHALL be 10 seconds in time mode and 0.1 km (or 0.1 mi in imperial units) in distance mode. For a manual entry the value SHALL be a clock time. A value outside the activity, or one that would leave less than one minute, SHALL be clamped to the nearest allowed value.

The screen SHALL say what the trim cuts at each end, as a duration and, when the activity has distance, a distance.

#### Scenario: Type an exact start
- **WHEN** the athlete taps the start readout in distance mode and enters 1.2
- **THEN** the kept range starts at 1.2 km and the start readout shows 1.20 km

#### Scenario: Step the end back
- **WHEN** the athlete steps the end value back once in time mode
- **THEN** the kept range ends 10 seconds earlier

### Requirement: Each end can be nudged in small steps
Each end SHALL have buttons beside its readout that move it earlier or later by one small step, for corrections too small to drag. A step SHALL be 1 second in time mode and 0.01 km (or 0.01 mi in imperial units) in distance mode. For a manual entry, whose clock shows minutes, it SHALL be 1 minute. Holding a button SHALL repeat it, and the step SHALL grow while it is held: in time mode to 10 seconds from the 9th step and to 1 minute from the 21st; in distance mode to 0.1 and then 1 km or mi; for a manual entry to 5 and then 15 minutes. In distance mode a nudge SHALL always reach another sample when there is one that way, even when the samples are further apart than the step. A nudge SHALL be clamped like any other move. A screen reader SHALL name each button by its end and direction ("Start later").

#### Scenario: Nudge the start
- **WHEN** the athlete taps the start's later button once in time mode
- **THEN** the kept range starts 1 second later

#### Scenario: Hold to move further
- **WHEN** the athlete holds the end's earlier button
- **THEN** the end moves back a second at a time, then 10 seconds, then a minute at a time

#### Scenario: Samples further apart than a nudge
- **WHEN** the athlete nudges the start 0.01 km later in distance mode where samples are 80 m apart
- **THEN** the kept range starts at the next sample

### Requirement: A handle slows when dragged below the track
While the athlete drags a handle, moving the finger or pointer below the track SHALL slow how far the handle moves for the same sideways travel: full speed within 24 points of the track's centre, half to 64, a quarter to 104, and a tenth beyond. A change of speed SHALL start from where the handle is, so it never jumps. While a handle moves slower than full speed the screen SHALL say how fast ("Half speed", "Quarter speed", "Fine scrubbing"), and otherwise SHALL show a hint that dragging down slows the handle, in the same place, so the screen doesn't shift. A drag that starts away from both handles and goes mostly up or down SHALL scroll the page instead.

#### Scenario: Fine-tune the end by dragging
- **WHEN** the athlete drags the end handle, moves the finger well below the track, and moves it sideways
- **THEN** the end moves a tenth as far as the finger and the screen shows "Fine scrubbing"

### Requirement: The map previews the cut
For an activity with GPS, the trim screen SHALL show the athlete's full-resolution route. The kept part SHALL be drawn in the route colour, and the cut parts dashed in a muted colour. Start and end markers SHALL sit at the kept range's ends and follow the handles as they move. The map SHALL frame the whole recorded route. An activity without GPS SHALL show no map.

The preview SHALL use the full track, which only its owner can read (`feed/activity-track`). It SHALL NOT use privacy-zone trimming: the athlete sees their own whole route.

#### Scenario: Cut the drive home
- **WHEN** the athlete moves the end handle back to where the ride returned to the car park
- **THEN** the drive is drawn dashed and muted, and the end marker sits at the car park

### Requirement: Totals show before and after
The trim screen SHALL show the activity's totals over the kept range, each next to its value before the trim:
- distance;
- moving time;
- elapsed time;
- elevation gain when it has altitude, else average speed or pace.

For a manual entry the screen SHALL show elapsed time, and the entered distance unchanged.

When the kept range would cut part of a segment effort the activity has, the screen SHALL name the segment and say its effort will be removed.

#### Scenario: Totals update while dragging
- **WHEN** the athlete drags the end handle back to 1:44:30 on a 45.1 km ride
- **THEN** distance shows the kept distance with "was 45.1 km" beneath it, and elapsed time shows 1:44:30

#### Scenario: Effort in the cut
- **WHEN** the kept range ends before an effort on Ridgeline Climb ends
- **THEN** the screen says the effort on Ridgeline Climb will be removed

### Requirement: Saving asks first and can't be undone
Saving a trim SHALL first ask for confirmation, saying how much is cut at each end and that the cut data can't be brought back. Saving SHALL be available only when the kept range differs from the whole activity. Reset SHALL return both handles to the activity's ends. Leaving the screen without saving SHALL change nothing.

#### Scenario: Confirm
- **WHEN** the athlete taps Trim, having cut 6 minutes from the start
- **THEN** a confirmation says 6 min will be cut from the start and can't be brought back, and nothing is written until they confirm

#### Scenario: Nothing cut
- **WHEN** both handles are at the activity's ends
- **THEN** Trim is disabled

### Requirement: A trim rebuilds the activity from what's kept
Confirming a trim SHALL rewrite the activity under the same record key, at the same visibility, as if it had been recorded over the kept range only:
- **Start time:** it SHALL be the kept range's first point. Every stream and point time SHALL be re-based to that start.
- **Dropped data:** every track point, stream sample, length and lap outside the kept range SHALL be dropped. A lap crossing a boundary SHALL be cut at it.
- **Recomputed totals:** distance, moving time, elapsed time, elevation gain and loss, maximum and average speed, heart rate, power and cadence figures, time in zones, the route line and its breaks SHALL be computed from what's kept.
- **Device totals:** totals a file supplied that can't be computed from the kept data, such as calories and training load, SHALL be scaled by the kept share of moving time.
- **What doesn't change:** the title, description, photos, gear, sport, devices, visibility and each detail category's visibility.

Each tier's details and streams, the full track and the segment track SHALL be rewritten together through the outbox, offline or online, as an activity's other writes are.

#### Scenario: Trimmed ride
- **WHEN** an athlete confirms a trim of a ride's first 6 minutes and last 3.1 km
- **THEN** its start time is 6 minutes later, its distance is 3.1 km less, and its streams, track and route start and end at the kept range

#### Scenario: Followers activity with an Only me heart rate
- **WHEN** an athlete trims a Followers activity whose heart rate is Only me
- **THEN** the trimmed heart rate is still only in the Only me space, and the rest stays at Followers

#### Scenario: Trim offline
- **WHEN** an athlete confirms a trim without a connection, then reconnects
- **THEN** the trimmed activity, details, streams, track and segment track are all written

#### Scenario: Pool swim
- **WHEN** an athlete trims the last 4 minutes of a pool swim
- **THEN** lengths that ended after the new end are dropped, and the swim's distance is the kept lengths' distance

### Requirement: Efforts and pairs follow the trimmed track
After a trim, the activity's segment efforts and group pairs SHALL be found again from the trimmed track. An effort that no longer lies within the kept range SHALL be removed, and a pair that no longer overlaps SHALL be dropped. The segment track's leaderboard exclusion and group-left marks SHALL be kept.

#### Scenario: Effort cut off
- **WHEN** an athlete trims a ride so that it ends halfway up Ridgeline Climb
- **THEN** the ride's effort on Ridgeline Climb no longer appears on the segment or the activity

### Requirement: A trim waits for the activity's queued writes
An athlete SHALL NOT be able to start a trim while writes for that activity are still queued to be sent. The app SHALL say the activity is still being sent and that the trim can be made once it has been. If the activity's track or streams can't be read, the trim screen SHALL show an error with a retry, and SHALL NOT open the editor on partial data.

#### Scenario: Still sending
- **WHEN** an athlete chooses Trim on an activity recorded offline that hasn't been sent yet
- **THEN** the app says it can be trimmed once it's sent, and opens no editor

### Requirement: A suggested trim is offered for likely junk
The app SHALL suggest a trim when a recording or file has either of these:
- **At the start:** standing time before the athlete first moved for a sustained stretch, when it is at least one minute.
- **At the end:** standing time of at least two minutes after the athlete last moved, or a stop of at least three minutes followed by a short stretch above the sport's plausible speed, such as a drive.

The suggestion SHALL say what it cuts in words ("Started 6 min early and drove 3.1 km after you stopped"). One tap SHALL apply it as the kept range, and it SHALL be undoable until saved. With nothing to suggest, no suggestion SHALL be shown.

#### Scenario: Car-park start and drive home
- **WHEN** a file starts with 6 minutes standing and ends with a 9-minute stop then 3.1 km at 49 km/h on a ride
- **THEN** the suggestion offers to cut 6 min at the start and the stop and drive at the end

#### Scenario: Clean recording
- **WHEN** a run starts moving within 20 seconds and stops within 30 seconds of the end
- **THEN** no suggestion is shown

### Requirement: Recording and import can be trimmed before saving
The recording screen's finish form and the import preview SHALL each offer "Trim start or end" for an activity that can be trimmed. When there is a suggestion, the offer SHALL summarise it. Choosing the offer SHALL open the trim screen on the unsaved activity, and the kept range SHALL apply to what is then saved, so the activity is published already trimmed. On the import preview the map SHALL show the chosen cut the same way the trim screen does. An imported activity trimmed this way SHALL keep its original file unchanged (`feed/activity-source-file`). Only a single-file import SHALL offer it: a batch import's activities SHALL NOT, and are trimmed from their pages once saved.

#### Scenario: Finish a recording with a suggestion
- **WHEN** an athlete finishes a recording that ends with a 12-minute stop
- **THEN** the finish form's trim offer says it suggests cutting 12 min at the end, and saving after applying it publishes the activity without those 12 minutes

#### Scenario: Import a file with a drive home
- **WHEN** an athlete previews a FIT file that ends with a drive
- **THEN** the preview's trim offer summarises the suggestion, and after it's applied the preview map shows the drive dashed

#### Scenario: A batch import offers no trim
- **WHEN** an athlete imports several activities at once
- **THEN** no activity in the batch offers a trim before saving, and each can be trimmed from its page after it's saved

### Requirement: Trim on every client
The trim screen SHALL be available on Android, iOS, desktop and web, each in its platform's idiom. Wide windows SHALL show the map beside the profile, readouts and totals. The handles SHALL have accessible labels and values, and SHALL be operable without dragging.

#### Scenario: Web
- **WHEN** an athlete opens Trim on the web app in a wide window
- **THEN** the map fills the left and the profile, readouts and totals sit in a panel on the right

#### Scenario: Screen reader
- **WHEN** a screen reader user focuses the start handle
- **THEN** it reads as "Trim start" with its time in and clock time, and the volume or arrow keys move it
