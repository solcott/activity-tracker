# Spec Delta

## ADDED Requirements

### Requirement: Overlapping efforts are grouped on the activity page
The activity page SHALL group two efforts whose time spans on the activity overlap by more than 2 seconds and by more than a quarter of the shorter one's span, together with any effort grouped with either, set apart from other groups. Efforts whose spans only meet end to end, or overlap by less, SHALL NOT be grouped. Groups SHALL be in the order they began. An effort that overlaps no other SHALL be shown as on its own, with no span bar.

#### Scenario: Whole trail and its stretches
- **WHEN** a ride has an effort on a whole trail from 0:00 to 15:08 and efforts on its two stretches from 0:00 to 8:54 and 8:54 to 15:08
- **THEN** the three efforts are shown as one group

#### Scenario: Stretches end to end
- **WHEN** a run has efforts on two stretches, one ending at 10:00 and the next starting at 10:00, and no effort spanning both
- **THEN** they are shown as two separate groups

#### Scenario: Segments meeting at a junction
- **WHEN** "Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road" ends 3 seconds after "Corner Canyon Road: Ghost Falls Trail to Canyon Hollow Trail" (3:34) begins, because each is timed from its own start line to its own end line
- **THEN** they are shown as two separate groups

#### Scenario: Sharing a stretch of trail
- **WHEN** one effort runs from 0:00 to 10:00 and another from 5:00 to 15:00
- **THEN** they are shown as one group

#### Scenario: No overlaps
- **WHEN** an activity's efforts don't overlap one another
- **THEN** each is listed on its own, as before

### Requirement: An effort within another is nested under it
Within a group, an effort whose span lies within a longer effort's span, to within 2 seconds at each end, SHALL be shown indented under the shortest such effort, joined to it by a guide line. Efforts with equal spans, or that overlap without one containing the other, SHALL be shown at the same depth.

#### Scenario: Stretches nested under their trail
- **WHEN** a ride's whole-trail effort spans its two stretch efforts
- **THEN** both stretches are shown indented under the whole trail, in the order they began

#### Scenario: Rounded ends still nest
- **WHEN** a stretch effort ends 1 second after the whole-trail effort containing it, because each time is rounded to whole seconds
- **THEN** the stretch is still nested under the whole trail

#### Scenario: Overlapping siblings
- **WHEN** one effort runs from 0:00 to 10:00 and another from 5:00 to 15:00
- **THEN** they are shown at the same depth in one group, in the order they began

#### Scenario: Shorter effort starting together
- **WHEN** "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail" (8:12) and "Rush Trail via Exit Stage Left Section to Bonneville Shoreline Trail" (9:34) start together
- **THEN** the Canyon Hollow effort is nested under the Bonneville one

### Requirement: A group lists each effort before those nested under it
A group SHALL list each effort directly above the efforts nested under it, whatever order they began in, and efforts at the same depth under one effort (or at the top of the group) in the order they began, the longer first when they began together.

#### Scenario: Stretch timed a second before its trail
- **WHEN** "Rush Trail - Limelight Section: Bonneville Shoreline Trail - Draper Section to Creek View Foot Path" (1:11) began 1 second before "Rush Trail - Limelight Section" (3:12), and "Rush Trail - Limelight Section: Creek View Foot Path to Lower Corner Canyon Trail" (2:00) began after it
- **THEN** the group lists "Rush Trail - Limelight Section" first, then under it "Bonneville Shoreline Trail - Draper Section to Creek View Foot Path" and "Creek View Foot Path to Lower Corner Canyon Trail", in that order

### Requirement: Grouped efforts show where they fall in their group
Each effort in a group of more than one SHALL show a bar marking its span's position within the span of the whole group.

#### Scenario: Second stretch
- **WHEN** a group's whole-trail effort runs from 0:00 to 15:08 and a stretch effort from 8:54 to 15:08
- **THEN** the trail's bar fills the width and the stretch's bar fills its last 41%

### Requirement: Nested efforts drop their parent's trail name
A nested effort SHALL show its segment's name without the trail name it shares with the effort it's nested under, when its name is that trail name followed by ": " or " - ". The trail name of the effort above SHALL be its name up to the first ": ", " (", " via " or " from ". Every other effort SHALL show its full name, and the segment screen SHALL always show the full name.

#### Scenario: Stretch under its trail
- **WHEN** "Rattler Trail: Upper Rattler Connector Trail to Ghost Falls Trail" is nested under "Rattler Trail (eastbound)"
- **THEN** it is shown as "Upper Rattler Connector Trail to Ghost Falls Trail"

#### Scenario: Section under a parent trail
- **WHEN** "Rush Trail - Exit Stage Left Section" is nested under "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail"
- **THEN** it is shown as "Exit Stage Left Section"

#### Scenario: Different trail
- **WHEN** "Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road" is nested under "Rattler Trail (eastbound)"
- **THEN** it is shown with its full name
