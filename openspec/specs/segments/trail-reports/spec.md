# segments/trail-reports Specification

## Purpose

Lets riders who were just on a trail say what it is like now, with condition tags, a short note and photos, and shows the latest of those on the Segment and Trail pages to everyone choosing where to go.

## Requirements

### Requirement: A trail report is a public record
A trail report SHALL be a `feed.trailReport` in its author's repo naming a segment by strong reference, with any of: condition tags, a note of at most 300 graphemes, and at most 4 photos. The AppView SHALL ignore one in any space, one naming a segment it hasn't indexed, and one with neither a known tag nor a photo.

#### Scenario: Report written
- **WHEN** alice reports Muddy and Downed tree on Wiggly Woods with a note and one photo
- **THEN** her repo gains a `feed.trailReport` naming the segment, the tags `muddy` and `downedTree`, the note and the photo

#### Scenario: Photos only
- **WHEN** alice posts a report on Wiggly Woods with two photos and no tags
- **THEN** the AppView accepts it as a report with photos and no conditions

#### Scenario: Nothing to report
- **WHEN** a report names only the unknown tag `sunny` and has no photo
- **THEN** the AppView ignores it

#### Scenario: Report in a space
- **WHEN** a `feed.trailReport` appears in an athlete's `interactions` space
- **THEN** the AppView ignores it

### Requirement: Condition tags
A report's tags SHALL come from: Dry, Tacky, Wet, Muddy, Snow and Ice (surface), and Downed tree, Overgrown, Washed out and Closed (on the trail). The AppView SHALL ignore unknown tags, keeping the report's known tags and photos.

#### Scenario: Unknown tag beside a known one
- **WHEN** a report names `muddy` and `foggy`
- **THEN** the AppView records Muddy and ignores Foggy

### Requirement: Only recent riders may report
The AppView SHALL accept a report only if, when it's indexed, the segment is `active` and not a lift, and its author has an effort on it that began in the previous 30 days, counting efforts of every visibility. It SHALL keep an accepted report when its author later loses that effort.

#### Scenario: Rode it last week
- **WHEN** alice, whose Only me activity gave an effort on Wiggly Woods 6 days ago, posts a report
- **THEN** the AppView accepts it

#### Scenario: Last ride two months ago
- **WHEN** bob, whose latest effort on Wiggly Woods began 60 days ago, posts a report
- **THEN** the AppView ignores it

#### Scenario: Lift
- **WHEN** a rider with an effort on a ski lift segment posts a report on it
- **THEN** the AppView ignores it

#### Scenario: Effort deleted afterwards
- **WHEN** alice deletes the activity whose effort let her report
- **THEN** her report is still served

### Requirement: A rider's reports are limited per day
The AppView SHALL accept at most 5 reports from one author on one segment indexed in the same UTC day, ignoring later ones that day.

#### Scenario: Sixth report in a day
- **WHEN** alice's sixth report on Wiggly Woods today is indexed
- **THEN** the AppView ignores it

### Requirement: Report times come from indexing
The AppView SHALL date a report by when it first indexed it, and SHALL NOT use the record's own timestamp for windows or order. A rewrite of the same record SHALL keep that date.

#### Scenario: Backdated record
- **WHEN** a report whose record says it was created a month ago is first indexed today
- **THEN** it counts as today's report

#### Scenario: Edited note
- **WHEN** alice rewrites her report to fix the note's spelling
- **THEN** the report keeps the date it was first indexed

### Requirement: Conditions count for 14 days
A report's tags SHALL count toward a segment's current conditions for 14 days after it was indexed. Within that window only each rider's latest report with tags SHALL count.

#### Scenario: Old report
- **WHEN** Wiggly Woods' only report with tags is 15 days old
- **THEN** the segment has no current conditions

#### Scenario: Rider changes their mind
- **WHEN** alice reported Muddy on Monday and Dry on Wednesday
- **THEN** her Wednesday report counts and her Monday one doesn't

#### Scenario: Photo-only report after a tagged one
- **WHEN** alice reported Muddy on Monday and posted a photo-only report on Wednesday
- **THEN** her Monday report's Muddy still counts

### Requirement: The conditions summary
The AppView SHALL summarise current conditions as: a lead tag, the riders behind it and the newest report's date; every other current tag with its rider count; and the newest report with a note. The lead SHALL be Closed when the newest counted report names it, otherwise the surface tag counted by the most riders, the newest breaking a tie, or none.

#### Scenario: Muddy wins
- **WHEN** three riders' counted reports say Muddy, one says Wet, and two also say Downed tree
- **THEN** the summary leads with Muddy from 3 riders, and lists Wet · 1 and Downed tree · 2

#### Scenario: Closed by the newest report
- **WHEN** two riders said Muddy last week and carol's report today says Closed
- **THEN** the summary leads with Closed

#### Scenario: Reopened
- **WHEN** carol said Closed on Monday and dave said Dry on Tuesday
- **THEN** the summary leads with Dry, and lists Closed · 1

#### Scenario: Only trail tags
- **WHEN** the only counted report says Downed tree
- **THEN** the summary has no lead and lists Downed tree · 1

### Requirement: The segment view carries conditions
Every segment view SHALL carry the segment's conditions summary, its photo count and its 4 newest photos, as the viewer may see them. `getSegment` SHALL also say whether the viewer may report on the segment now, and when the viewer's newest effort on it began.

#### Scenario: Recent rider
- **WHEN** alice, who rode Wiggly Woods 3 days ago, opens it
- **THEN** the view says she may report, with her ride's date

#### Scenario: Signed out
- **WHEN** a signed-out viewer opens Wiggly Woods
- **THEN** the view carries the summary and photos and says they may not report

### Requirement: Reports are listed newest first
`getTrailReports` SHALL list a segment's reports the viewer may see, newest first and paged, each with its author, tags, note, photos, date and labels, and whether it is still within 14 days.

#### Scenario: All reports
- **WHEN** a viewer lists Wiggly Woods' reports
- **THEN** the reports of the last 14 days come first, marked current, followed by older ones

### Requirement: Photos are listed per segment or trail
`getTrailPhotos` SHALL list, newest first and paged, the photos of the reports the viewer may see on one segment, or on every segment of one trail, each with its author, date, alt text, aspect ratio and report.

#### Scenario: Photos of a trail
- **WHEN** a viewer lists the photos of Kingdom Loop
- **THEN** the AppView returns the photos of reports on all of its segments, newest first

### Requirement: A trail's conditions are rolled up
`getPathConditions` SHALL return, for a trail, each of its listed segments that has current conditions, with its summary, Closed ones first and then newest first, together with the trail's photo count and its 5 newest photos.

#### Scenario: Kingdom Loop
- **WHEN** a viewer opens Kingdom Loop, where Ridge Run's newest report says Closed and Wiggly Woods and Lower Loop have current reports
- **THEN** the roll-up lists Ridge Run first, then the other two by their newest report

### Requirement: Blocks and mutes apply to reports
The AppView SHALL leave a report and its photos out of every list, summary and count for a viewer in a block with its author, either way. For a viewer who mutes its author, it SHALL leave the report out of lists and photos, and still count its tags.

#### Scenario: Blocked rider
- **WHEN** bob blocks alice
- **THEN** alice's reports, tags and photos are absent from everything bob is served, and bob's from alice's

#### Scenario: Muted rider
- **WHEN** carol mutes alice, whose report says Muddy
- **THEN** carol's list omits alice's report while the summary still counts her Muddy

### Requirement: Labels remove or blur reports
A report whose record or author carries `!hide` or `!takedown` SHALL be left out of lists, summaries and counts for everyone but its author. A takedown SHALL stop its photos being served. Negating the label SHALL restore it.

#### Scenario: Hidden report
- **WHEN** alice's report carries `!hide`
- **THEN** its tags stop counting and it isn't listed, and alice still sees it

#### Scenario: Taken-down photos
- **WHEN** alice's report carries `!takedown`
- **THEN** the AppView refuses to serve or redirect its photos

### Requirement: Authors delete reports and photos
An author SHALL be able to delete their report, and to remove one photo from it. Removing the last photo of a report with no tags SHALL delete the report. The AppView SHALL drop a deleted report with its tags and photos, and update a rewritten one.

#### Scenario: Remove a photo
- **WHEN** alice removes one of the two photos on her report
- **THEN** her record is rewritten with the other photo, and the removed one leaves the trail's photos

#### Scenario: Delete a report
- **WHEN** alice deletes her Muddy report
- **THEN** its tags stop counting and its photos leave the strip

### Requirement: The Segment page shows a Conditions card
The Segment page SHALL show a Conditions card when the segment has reports or the viewer may report. It SHALL show the lead tag and its riders and date, the other current tags with counts, the newest note with its author, and a strip of the newest photos with the total. Tapping the card SHALL open the reports list; a photo SHALL open the viewer.

#### Scenario: Muddy segment
- **WHEN** a viewer opens Wiggly Woods with the Muddy summary and 16 photos
- **THEN** the card reads "Muddy", "3 riders · latest 2 days ago", "Downed tree · 2", Maya's note, and shows three photos and "+13"

#### Scenario: No reports, no ride
- **WHEN** a viewer who hasn't ridden a segment opens it, and it has no reports
- **THEN** the page shows no Conditions card

### Requirement: The card states empty and closed conditions
A card with no current conditions SHALL say there are no reports in the last 14 days, and give the newest older report's tags and age when there is one. A Closed lead SHALL be drawn in the error colour with a closed icon and the word Closed.

#### Scenario: Only older reports
- **WHEN** a segment's newest report said Dry 5 weeks ago
- **THEN** the card reads "No reports in the last 14 days" and "Last report: Dry, 5 weeks ago"

#### Scenario: Closed
- **WHEN** a segment's summary leads with Closed
- **THEN** the card shows Closed in the error colour with its icon

### Requirement: Recent riders are asked to report
For a viewer who may report, the card SHALL offer Report conditions and Add photos. With no current conditions it SHALL say when the viewer last rode the segment and ask how it was.

#### Scenario: Rode it, nothing reported
- **WHEN** alice rode Wiggly Woods 3 days ago and it has no current reports
- **THEN** the card reads "You rode it 3 days ago. How was it?" with Report conditions

#### Scenario: Viewer who may not report
- **WHEN** a viewer who hasn't ridden a segment opens it while it has reports
- **THEN** the card shows the conditions with no Report conditions or Add photos

### Requirement: The report sheet
Report conditions SHALL open a sheet titled "How's the trail?" with the segment and when the viewer rode it, the tags as toggles grouped Surface and On the trail, an optional note with its count, the photos to add, and Post report. It SHALL say the report is public with the rider's name, and that conditions show for 14 days.

#### Scenario: Posting
- **WHEN** alice picks Muddy and Downed tree, writes a note and taps Post report
- **THEN** her report is written through the outbox and the card shows it at once

#### Scenario: Nothing chosen
- **WHEN** alice has picked no tag and added no photo
- **THEN** Post report is disabled

### Requirement: Adding photos
Add photos SHALL offer Take photo and Choose from library, and the photos of the viewer's own activities that passed the segment, grouped by activity with its visibility, newest first. At most 4 photos SHALL be picked per report. A picked activity photo SHALL keep its alt text, which the rider may edit.

#### Scenario: Photos from a ride
- **WHEN** alice opens Add photos on Wiggly Woods
- **THEN** she sees her rides there with their photos, and picks two

#### Scenario: Fifth photo
- **WHEN** alice has picked 4 photos
- **THEN** the rest can't be picked

### Requirement: Sharing a non-public photo asks first
When a picked photo comes from an activity that isn't public, the client SHALL say the copy on the trail will be public while the activity stays as it is, and post only after the rider confirms, every time.

#### Scenario: Followers ride
- **WHEN** alice posts a report with a photo from her Followers ride
- **THEN** she is asked to confirm, and the activity's visibility doesn't change

### Requirement: Photos are posted without location
The client SHALL re-encode every report photo, new or shared, dropping its metadata and location, and upload it as a new public blob in the rider's repo.

#### Scenario: Photo with GPS
- **WHEN** alice adds a library photo carrying GPS metadata
- **THEN** the uploaded blob carries no location

### Requirement: The reports list
The reports list SHALL show the segment's reports newest first under "Last 14 days", then "Older" with older reports shown muted. Each report SHALL show its author, age, tags, note and photos, and a menu offering Report and Block for others' reports and Delete for the viewer's own.

#### Scenario: Own report
- **WHEN** alice opens the menu on her report
- **THEN** it offers Delete and not Report

### Requirement: The photo viewer
A photo SHALL open full screen with its author, age, alt text and tags, and links to its report and its segment, paging through the list it was opened from. Its menu SHALL offer Report and Block for others' photos and Remove from trail for the viewer's own.

#### Scenario: Opening from the strip
- **WHEN** a viewer taps the first photo in Wiggly Woods' strip
- **THEN** the viewer opens "1 of 16" with Maya Chen's name and the photo's alt text

### Requirement: Native iOS draws the same flows
On iOS the Conditions section, report sheet, add-photos screen, reports list and photo viewer SHALL be native: a grouped list section, a form sheet with checkmark rows for tags, Cancel and Post, and the system photo picker. They SHALL use the shared words and rules.

#### Scenario: Reporting on iOS
- **WHEN** alice taps Report Conditions on Wiggly Woods on her iPhone
- **THEN** a native sheet opens with Surface and On the Trail sections of checkmark rows, a note and photos, and Post
