# Spec Delta

## ADDED Requirements

### Requirement: An activity card reads the same in both UIs
An activity card's words SHALL be chosen by shared code that both UIs ask: its author, visibility badge, whom it was done with, moderation notices and photo labels, title and icon's spoken label, stats, locks and counts. Whether Like is offered and given, and whether photos are blurred or placeholders, SHALL be decided there too.

#### Scenario: Done with three others
- **WHEN** a card's activity lists Bob, Carol and Dan as partners
- **THEN** both UIs read "with Bob and 2 others"

#### Scenario: Own warned activity
- **WHEN** the owner sees their activity labelled Spam
- **THEN** both UIs show the Spam notice and no Like

#### Scenario: A strength workout
- **WHEN** a card shows a strength workout with no distance
- **THEN** both UIs show only its time

### Requirement: A start time reads on the activity's clock in both UIs
An activity's start SHALL be chosen by shared code on the activity's own clock, its time of day formatted by each UI on the device's 12- or 24-hour clock in the activity's UTC offset, and its day beside the current day in that offset, so both UIs read the same start for the same activity.

#### Scenario: Started in another zone
- **WHEN** a viewer in UTC sees a run started at 9:00 AM local time at UTC-7, the same day
- **THEN** both UIs read "Today at 9:00 AM"

#### Scenario: Day only
- **WHEN** a viewer isn't served a ride's start time
- **THEN** both UIs read only its day

### Requirement: The batch page's months read the same in both UIs
The batch page's month headings SHALL be chosen by shared code, from each activity's start in the viewer's time zone, and both UIs SHALL show them over the same activities.

#### Scenario: A month heading
- **WHEN** a batch's newest activity started on November 5, 2025 in the viewer's zone
- **THEN** both UIs head it "November 2025"
