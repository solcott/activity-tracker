# feed/batch-import Specification

## Purpose

Lets an athlete import many activities at once from several files, compressed archives or a whole Strava or Garmin Connect account export: review them, decline any, and save the rest as one batch.

## Requirements

### Requirement: Several files in one pick
The import screen SHALL let the athlete pick several files at once on every platform. Every activity read from the picked files SHALL join one batch.

#### Scenario: A week of watch files
- **WHEN** an athlete picks five FIT files at once
- **THEN** the import screen shows one batch holding the five activities

#### Scenario: One file with one activity
- **WHEN** an athlete picks a single GPX file
- **THEN** the import screen shows that activity's preview form, as before

### Requirement: Archives are opened
A picked file SHALL be read as a zip archive when it is one, and each member SHALL be read as if it had been picked, including gzipped members and zips inside the zip. Members that are not activity files SHALL be skipped. A gzipped file SHALL keep importing as today.

#### Scenario: Zip of FIT files
- **WHEN** an athlete picks `rides.zip` holding three FIT files and a `readme.txt`
- **THEN** the batch holds the three rides, and the screen says one file was skipped

#### Scenario: Gzip inside a zip
- **WHEN** a picked zip holds `activities/123.fit.gz`
- **THEN** the batch holds the activity read from the decompressed FIT file

#### Scenario: Zip inside a zip
- **WHEN** a picked zip holds `UploadedFiles_0-_Part1.zip`, which holds FIT files
- **THEN** the batch holds the activities from those FIT files

### Requirement: Archive limits
Reading an archive SHALL stop at limits on each member's decompressed size, on the archive's total decompressed size and on its number of members. Members past a limit SHALL be skipped with a reason, and the rest of the batch SHALL still import.

#### Scenario: Decompression bomb
- **WHEN** a picked zip holds a member that decompresses past the per-member limit
- **THEN** that member is listed as skipped, too large, and the other members import

### Requirement: Unreadable files are reported
A picked file or archive member that looks like an activity file but can't be read SHALL be listed in the batch as skipped, with its name and whether it was unsupported, damaged or too large. Skipped files SHALL NOT stop the rest of the batch.

#### Scenario: One damaged file
- **WHEN** an athlete picks four FIT files, one of them truncated
- **THEN** the batch holds three activities and lists the truncated file as damaged

### Requirement: Large archives import without holding them in memory
An archive SHALL be read without loading it whole, and the batch SHALL keep only each activity's summary until the athlete previews or saves it. Saving SHALL hold one file's activities at a time and SHALL NOT keep an activity's route, streams or original file once it is queued. A batch of thousands of activities from an archive of several gigabytes SHALL be reviewable and savable on a phone and in the browser.

#### Scenario: Full account export on a phone
- **WHEN** an athlete picks a 3 GB Strava export holding 2,000 activities on an Android phone
- **THEN** the batch shows all 2,000 rows without the app running out of memory

#### Scenario: Saving a full account export on a phone
- **WHEN** the athlete saves all 2,000 activities of that export on an Android phone
- **THEN** all 2,000 are queued without the app running out of memory

### Requirement: Reading shows progress and can be cancelled
While picked files are read, the import screen SHALL show how many files and activities have been read so far. The athlete SHALL be able to cancel, which discards the batch and saves nothing.

#### Scenario: Cancel a long read
- **WHEN** an athlete cancels while a large export is still being read
- **THEN** the screen returns to picking a file, and nothing is saved

### Requirement: Strava exports are recognized
A picked archive holding Strava's `activities.csv` and its `activities/` folder SHALL be read as a Strava export. Each activity file the CSV names SHALL take that row's name as its title and its description, and its type where the CSV's type maps to one of ours. A file the CSV doesn't name SHALL import as from any archive.

#### Scenario: Titles from the export
- **WHEN** an athlete imports a Strava export whose CSV names `activities/123.fit.gz` "Lunch loop" with description "Windy"
- **THEN** that activity's row and saved title is "Lunch loop", with description "Windy"

#### Scenario: Unmapped type
- **WHEN** the CSV gives an activity a type the app doesn't know
- **THEN** the activity keeps the type read from its file

### Requirement: Garmin Connect exports are recognized
A picked archive holding Garmin Connect's summarized activities JSON and its uploaded-files archives SHALL be read as a Garmin Connect export. Each activity file SHALL be matched to its summary by activity ID where the file name carries one, otherwise by start time and duration. A matched summary's name and type SHALL apply as for a Strava export.

#### Scenario: Named Garmin activity
- **WHEN** an athlete imports a Garmin export whose summary names activity 987654 "Track Tuesday" and whose uploaded files include that activity's FIT file
- **THEN** the activity's title is "Track Tuesday"

### Requirement: Export privacy never widens visibility
When an export says an activity is private or followers-only, that activity's visibility SHALL be the narrower of the export's setting (Only me for private, Followers for followers-only) and the batch's visibility. An export setting of everyone SHALL use the batch's visibility. Detail categories SHALL stay within the activity's visibility.

#### Scenario: Private Strava activity in a Public batch
- **WHEN** an athlete imports a Strava export with batch visibility Public and one activity marked Only You
- **THEN** that activity is saved as Only me, and the others as Public

#### Scenario: Export wider than the batch
- **WHEN** the batch's visibility is Followers and an activity's export setting is everyone
- **THEN** the activity is saved as Followers

#### Scenario: Export without privacy
- **WHEN** an athlete imports an export that gives no activity a privacy setting, as Strava's `activities.csv` doesn't
- **THEN** the batch form says the export doesn't say who could see each activity, and every activity saves at the batch's visibility

### Requirement: Combining never widens a narrowed activity
Combining SHALL NOT widen any activity's visibility. Copies combined within a batch SHALL all be saved at the narrowest of their visibilities. A copy narrower than the logged activity it repeats SHALL be saved on its own instead of combined, and the logged activity SHALL be left unchanged.

#### Scenario: Private copy combined with a public one
- **WHEN** a batch holds a public ride and an Only me copy of it from the export, and the athlete keeps Combine
- **THEN** both and their combined activity are saved as Only me

#### Scenario: Private copy of a public logged activity
- **WHEN** a batch's Only me activity repeats a public activity already logged, and the athlete keeps Combine
- **THEN** it is saved on its own as Only me, and the logged activity is not changed

### Requirement: The batch form lists every activity
An import of two or more activities SHALL show the batch form. It SHALL list one row per activity, oldest first and grouped by month. Each row SHALL give the activity's title or default title, type, start, duration and distance, or maximum depth for a dive, and its device. A count of selected, duplicate and skipped items SHALL be shown.

#### Scenario: Mixed batch
- **WHEN** an athlete picks a zip holding a run, a ride and a dive
- **THEN** the batch form lists the three with their types, the dive showing its depth

### Requirement: Activities can be declined
Each row SHALL have a checkbox, every non-duplicate selected at first. The athlete SHALL be able to select or deselect all and filter the list to all, new or duplicate activities. Only selected activities SHALL be saved, and select or deselect all SHALL apply to the activities the filter shows. A duplicate's row SHALL say whether it is an exact copy, and the summary SHALL count exact copies apart from other duplicates.

#### Scenario: Decline two
- **WHEN** an athlete deselects two of ten activities and saves
- **THEN** eight activities are saved, and the two declined aren't in their log

#### Scenario: Deselect duplicates only
- **WHEN** an athlete filters to duplicates and taps Deselect all
- **THEN** every duplicate is deselected and the new activities stay selected

#### Scenario: Exact copies counted
- **WHEN** a batch holds 900 exact copies and 40 other duplicates
- **THEN** the summary names both counts, and each exact copy's row says it is an exact copy

### Requirement: One action for many duplicates
With the list filtered to duplicates, the athlete SHALL be able to set Ignore, Import anyway or Combine for every duplicate the filter shows at once. Choosing Combine SHALL set Import anyway for any shown duplicate that can't be combined yet. Each duplicate's action SHALL still be changeable on its own afterwards.

#### Scenario: Ignore every duplicate
- **WHEN** an athlete filters a batch of 2,300 activities to its 1,050 duplicates and chooses Ignore for all
- **THEN** every duplicate shows Ignore, and saving saves only the other 1,250

#### Scenario: Combine where possible
- **WHEN** an athlete chooses Combine for all shown duplicates, and one repeats a logged activity that is still uploading
- **THEN** that one shows Import anyway, and the rest show Combine

### Requirement: Each activity can be previewed
Tapping a row SHALL open a preview of that activity showing its map, stats and charts, as the single-file preview shows them. The preview SHALL let the athlete change only its title and type. It SHALL NOT offer visibility, categories, gear or description. For a duplicate, the preview SHALL also show what it matched (the logged activity, or the earlier activity in the batch): its title, sport, start, length, distance and device beside the duplicate's own, and whether it is an exact copy.

#### Scenario: Check a ride before saving
- **WHEN** an athlete taps a ride's row
- **THEN** they see its map, distance, time and heart rate chart, and fields for its title and type only

#### Scenario: Compare a duplicate
- **WHEN** an athlete opens the preview of a ride flagged as repeating a logged ride
- **THEN** they see the logged ride's title, sport, start, length, distance and device beside the import's

### Requirement: Title and type overrides apply to one activity
A title or type changed in an activity's preview SHALL apply only to that activity, and its row SHALL show the change. Saving SHALL use it. A blank title SHALL save as the default title for its type and start.

#### Scenario: Fix a GPX with no sport
- **WHEN** an athlete changes a GPX activity's type from Run to Hike in its preview and saves the batch
- **THEN** that activity is saved as a hike, and the others keep their types

### Requirement: Batch-wide settings
Visibility, detail categories and gear SHALL be chosen once for the batch and apply to every selected activity, subject to export privacy. Categories SHALL be offered for any data any selected activity has. Each picked file whose times have no zone SHALL have its own zone choice, applied to every activity read from it.

#### Scenario: Two dive logs from different trips
- **WHEN** an athlete picks two zone-less dive logs in one batch
- **THEN** the batch offers a zone for each log, and changing one moves only that log's dives

### Requirement: Saving a batch
Saving SHALL publish every selected activity, oldest first, and handle each duplicate as the athlete chose. An activity whose data can't be read again at save SHALL be skipped and reported, and the rest SHALL still be saved. After saving, the screen SHALL say how many were saved and skipped.

#### Scenario: Batch saved
- **WHEN** an athlete saves a batch of 40 selected activities
- **THEN** 40 activities are queued for their log and the screen says 40 were saved

#### Scenario: Source vanished before save
- **WHEN** a picked file can no longer be read when the athlete saves
- **THEN** its activities are reported as skipped, and the others are saved

### Requirement: Batch identifier
Every activity a save publishes, combined ones within the batch included, SHALL name one batch identifier, new for each save. A copy combined into an activity already logged SHALL NOT give that activity the identifier.

#### Scenario: Activities share the batch identifier
- **WHEN** an athlete saves a batch with 38 activities on their own and two copies combined with each other
- **THEN** all 38 and the combined activity name the same batch identifier, and a later batch names a different one

#### Scenario: Combined into a logged activity
- **WHEN** a batch combines a copy into a ride the athlete logged before
- **THEN** that ride doesn't name the batch's identifier

### Requirement: A saved batch is one import in the upload queue
Each saved batch SHALL be listed in the upload queue as one import. It SHALL name what was imported, by the export recognized or else the picked files, and SHALL list the activities saved and the ones skipped at save.

#### Scenario: Strava export saved
- **WHEN** an athlete saves a Strava export's 1,362 activities with 3 skipped
- **THEN** the upload queue lists one Strava export import of 1,362 activities, 3 of them skipped

### Requirement: The saved screen opens the upload queue
After a batch is saved, the screen SHALL offer to open the upload queue.

#### Scenario: View uploads
- **WHEN** the athlete taps View uploads after saving a batch
- **THEN** the upload queue opens with that import listed

### Requirement: A failed save says what was saved
When saving a batch fails partway, the screen SHALL show the failure next to Save, with how many activities were saved. The activities already saved SHALL leave the selection, and those not yet saved SHALL stay selected.

#### Scenario: Save fails after 300 of 1,000
- **WHEN** saving a batch of 1,000 fails after 300 were queued
- **THEN** the screen says 300 of 1,000 were saved and why the rest weren't, next to Save, and 700 stay selected

### Requirement: Saving again continues the import
Saving again after a failed save SHALL save only the activities still selected, as part of the same import in the upload queue, so no activity is queued twice.

#### Scenario: Retry after a failure
- **WHEN** the athlete taps Save again after 300 of 1,000 were saved
- **THEN** the other 700 are saved, and the upload queue lists one import of 1,000

### Requirement: Saving outlives the screen and the app
A batch save SHALL continue after the athlete leaves the import screen or the app, and SHALL resume on its own after the app is frozen or killed, until every selected activity is saved or skipped, the athlete cancels the rest, or they sign out. No activity SHALL be queued twice by resuming. Opening import while a save runs SHALL show its progress.

#### Scenario: Back during a save
- **WHEN** the athlete goes Back while a batch of 500 is saving, and opens import again
- **THEN** the save has gone on, and the screen shows its progress

#### Scenario: App killed mid-save on Android
- **WHEN** Android kills the app after 120 of a batch's 400 activities were saved
- **THEN** the save resumes in the background without the athlete opening the app, saves the other 280, and the upload queue lists one import of 400

#### Scenario: App swiped away on iOS
- **WHEN** the athlete swipes the app away after 120 of 400 were saved on iOS
- **THEN** the save resumes when iOS next runs the app's background task, or when the athlete next opens the app, and saves the other 280

#### Scenario: Desktop app quit
- **WHEN** the athlete quits the desktop app mid-save and opens it again
- **THEN** the save resumes and saves the rest

#### Scenario: Sign out
- **WHEN** the athlete signs out while a batch is saving
- **THEN** the save stops, and its unsaved activities are dropped

### Requirement: Saving shows its progress in the background
On Android, while a batch is being saved, the app SHALL show an ongoing notification with how many of the batch's activities are saved out of all selected, which goes when the save ends.

#### Scenario: Leave during a save
- **WHEN** the athlete leaves the app while a batch of 500 is being saved on Android
- **THEN** a notification shows the save's progress until all 500 are saved, then goes

### Requirement: An import that can't resume is reported
When a save can't resume because a picked file can no longer be opened, or because a web tab closed mid-save, the import SHALL say how many activities were saved out of how many were selected, and that picking the files again imports the rest. Picking the same files again SHALL flag the saved ones as already in the athlete's log.

#### Scenario: Web tab closed mid-save
- **WHEN** the browser tab closes after 120 of 400 activities were saved, and the athlete opens the app again
- **THEN** the import says it stopped after 120 of 400 and to pick the files again, and picking them flags those 120 as duplicates

#### Scenario: File access revoked
- **WHEN** a picked file was deleted before an interrupted save could resume
- **THEN** that file's unsaved activities are reported as skipped, and the save goes on with the rest

### Requirement: Picked files are held only while saving
Access the app keeps to the athlete's picked files so a save can resume, and any copies made of them, SHALL be released when the save ends, is cancelled, or the athlete signs out.

#### Scenario: Save finished
- **WHEN** a save of 400 activities from 400 picked files finishes on Android
- **THEN** the app keeps no access grant to those files and no copy of them

### Requirement: Importing is native on iOS
On iOS the import screen SHALL be drawn natively with the same content as the shared screen: one activity's preview and form with Save in the bar; several activities' summary, filters, rows by month and batch settings with Save; an opened row in a sheet; and the saved and after-import panels.

#### Scenario: Opening a row
- **WHEN** alice taps a batch row
- **THEN** a sheet shows its type, title and page as it would publish, and Done or a swipe down closes it

### Requirement: Picking files natively on iOS
On iOS the import screen SHALL pick files with the system's document picker, several at once and of any type, and pass them on as sources read a range at a time, never read whole. It SHALL show reading's progress with Cancel, a batch save under way, and each stopped import with Dismiss.

#### Scenario: Picking several files
- **WHEN** alice taps Choose files and picks two GPX files
- **THEN** the screen shows that it is reading, then the batch's two activities by month with both selected and Save 2 activities

#### Scenario: Cancelling a read
- **WHEN** alice taps Cancel while the picked files are read
- **THEN** reading stops and the screen shows Choose files again

#### Scenario: A stopped import
- **WHEN** alice opens Import after an import's save stopped at 120 of 400 and taps Dismiss on its notice
- **THEN** the notice goes away
