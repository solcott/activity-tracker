# Spec Delta

## ADDED Requirements

### Requirement: Training zones is native on iOS
On iOS the Training zones page SHALL be drawn with the system's own grouped list and navigation bar, and SHALL list the default zones and then each sport with its own, each with its heading, what it applies to, Edit, its thresholds, and each kind of zone it offers with its model and its zones, or what it needs to work them out, in the shared page's words. Its loading SHALL follow `ui/content-loading`.

#### Scenario: The defaults
- **WHEN** alice's threshold heart rate is 170 bpm, her max 190, her resting 55 and her threshold power 250 W, and she opens Training zones
- **THEN** the Default zones read "Threshold heart rate 170 bpm", "Max heart rate 190 bpm", "Resting heart rate 55 bpm" and "Threshold power 250 W", then her heart rate zones and her power zones, each with its model

#### Scenario: A sport's threshold from the defaults
- **WHEN** alice's running zones set only a threshold heart rate of 172 bpm
- **THEN** her Run zones read "Threshold heart rate 172 bpm" and "Max heart rate 190 bpm (default)"

#### Scenario: A threshold not set
- **WHEN** alice has no threshold power
- **THEN** it reads "Not set", and her power zones read "Enter your threshold power to see these zones."

#### Scenario: Opening the defaults
- **WHEN** alice taps Edit beside Default zones
- **THEN** the default zones' form opens

### Requirement: Training zones' visibility, history and sports on iOS
On iOS the Training zones page SHALL also offer, once any threshold or zone model is saved, who can see the zones, as a menu saved as soon as it is chosen, with the Followers note as shared; Threshold history when any threshold has a value; and, while any sport has no zones of its own, Add a sport, offering those sports.

#### Scenario: Who can see them
- **WHEN** alice chooses Followers for her zones
- **THEN** it is saved for every set of zones, and the page says that anyone who follows her can see them, since she doesn't approve followers

#### Scenario: Nothing saved yet
- **WHEN** alice has saved no threshold or zone model
- **THEN** the page offers no visibility and no Threshold history

#### Scenario: Adding a sport
- **WHEN** alice chooses Swim under Add a sport
- **THEN** Swim's zones open, with nothing of its own yet

### Requirement: The default zones are native on iOS
On iOS the default zones' form SHALL be drawn with the system's own grouped form and navigation bar, and SHALL offer the shared form's fields in its words: the threshold heart rate, max heart rate, resting heart rate and threshold power, each with whether imported activities may update it; and the heart rate and power zone models as menus, each with what it is based on and the zones it gives, or what it needs to work them out. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Digits only
- **WHEN** alice types "17a0" into an empty threshold heart rate
- **THEN** the field reads "170"

#### Scenario: Zones as the model gives them
- **WHEN** alice chooses Andy Coggan (5) for her heart rate zones
- **THEN** the form lists Zone 1 to Zone 5 with their names and ranges in bpm, the last open-ended

#### Scenario: Updating from imports
- **WHEN** alice turns on Update from imported activities under her max heart rate
- **THEN** the change is kept with the form and saved with it

### Requirement: Saving the iOS zones forms
On iOS the default zones' form and a sport's zones form SHALL have Save in the bar, unavailable until the saved values have loaded, while saving, and while any field holds a value out of its range, which SHALL show in the error colour. A failed save SHALL show at the top of the form and keep what was entered. A save that succeeds SHALL return to Training zones.

#### Scenario: A value out of range
- **WHEN** alice types 300 as her max heart rate
- **THEN** the field shows in the error colour and Save is unavailable

#### Scenario: A failed save
- **WHEN** saving fails because the server can't be reached
- **THEN** the failure shows at the top of the form and what alice entered stays

### Requirement: A sport's zones are native on iOS
On iOS a sport's zones form SHALL be drawn with the system's own grouped form and navigation bar, titled by the sport, and SHALL offer the shared form's fields in its words: the thresholds the sport's zone kinds use, the threshold pace as `m:ss` in the sport's pace unit, each blank one showing the default it falls back to; each kind's zone model as a menu with its zones, pace zones as paces; and, for a sport that has its own zones, Remove, which returns it to the defaults.

#### Scenario: A blank threshold
- **WHEN** alice opens Run's zones with no max heart rate of its own and a default of 190
- **THEN** the max heart rate row shows "Default: 190"

#### Scenario: Pace zones
- **WHEN** alice's running threshold pace is 4:30 per kilometer with Joe Friel Running (7)
- **THEN** the pace zones read as paces per kilometer, the first "Slower than" a pace and the last "or faster"

#### Scenario: Removing a sport's zones
- **WHEN** alice taps Remove Run zones
- **THEN** Run uses the default zones again and the form returns to Training zones

### Requirement: Privacy zones is native on iOS
On iOS the Privacy zones page SHALL be drawn with the system's own grouped form and navigation bar, in the shared page's words: each zone by its name with its radius as a menu in the athlete's units, and Remove; Hide start and end as a menu with Off; and the shared notes on what zones do, hiding an activity's start or end, and other apps' copies. A zone's centre SHALL NOT be shown. Its loading SHALL follow `ui/content-loading`.

#### Scenario: A zone's radius
- **WHEN** alice, whose units are metric, chooses 600 m for Home
- **THEN** the change is saved, and Home's radius reads "0.60 km"

#### Scenario: Removing a zone
- **WHEN** alice removes Home, her only zone
- **THEN** Home is removed, and the page reads "No privacy zones yet."

#### Scenario: Hiding the ends
- **WHEN** alice chooses Off under Hide start and end
- **THEN** her routes' starts and ends are no longer hidden outside her zones

### Requirement: Adding a privacy zone here on iOS
On an iPhone the Privacy zones page SHALL offer an optional label and Add a zone at my location, which SHALL read "Finding your location…" while it finds it, the system asking for the location first if the app may not use it yet. The zone SHALL be added with a 400 m radius and the label.

#### Scenario: Adding a zone here
- **WHEN** on an iPhone alice types "Work" and taps Add a zone at my location
- **THEN** the button reads "Finding your location…" until the zone is added, as "Work" with a 400 m radius

### Requirement: When privacy zones can't change on iOS
On iOS, while the session must sign in again, the Privacy zones page SHALL say so at the top and nothing on it SHALL change; while a change saves, nothing else SHALL change; and a failed change SHALL show at the top.

#### Scenario: Signed in before the zones' scope
- **WHEN** alice's session was granted before the zones' scope
- **THEN** the page says to sign in again, and no zone, radius or hidden distance can be changed

### Requirement: The iOS zones pages open as the shared pages do
On iOS, Training zones and Privacy zones SHALL open as Settings' pages, beside the list on an iPad as wide as Settings needs. The default zones and a sport's zones opened from Training zones SHALL show beside the list too, as the column's second page with a back button to Training zones, and alone in the stack with the stack's back button. Threshold history SHALL open as before, drawn by the shared UI.

#### Scenario: A sport's zones beside the list
- **WHEN** on an iPad alice opens Training zones beside Settings' list and taps Edit beside her Run zones
- **THEN** Run's zones show beside the list with a back button, and back returns to Training zones beside the list

#### Scenario: Threshold history
- **WHEN** alice taps Threshold history on the native Training zones
- **THEN** the shared threshold history opens, with its charts
