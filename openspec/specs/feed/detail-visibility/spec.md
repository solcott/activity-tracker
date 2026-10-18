# feed/detail-visibility Specification

## Purpose

Lets an athlete choose, for each category of an activity's data (start time, heart rate, power, pace/speed, calories, map, physiology, cadence and running form, and dive), who may see it. Each category's choice holds everywhere that data appears: summary stats, charts, laps and legs.

## Requirements

### Requirement: Every piece of activity data has one category
Every summary field, stream channel, and lap or leg field of an activity SHALL belong to exactly one detail category (Start time, Heart rate, Power, Pace/Speed, Calories, Map, Physiology, Cadence & running form, or Dive) or be core. Core data SHALL always be at the activity's visibility. Data in no category SHALL be core.

#### Scenario: Heart rate everywhere it appears
- **WHEN** an activity has average and max heart rate, a heart rate channel, and laps with heart rate
- **THEN** all of them are in the Heart rate category

#### Scenario: Power family
- **WHEN** an activity has average, max and normalized power, work, and power, left/right balance, torque effectiveness, pedal smoothness, platform center offset and form power channels
- **THEN** all of them are in the Power category

#### Scenario: Physiology
- **WHEN** an activity has respiration rate, core temperature, SmO2 or total hemoglobin channels
- **THEN** they are in the Physiology category

#### Scenario: Cadence and running form
- **WHEN** an activity has average and max cadence, a cadence channel, laps with cadence, and vertical oscillation, stance time, step length, vertical ratio, stance time balance or leg spring stiffness channels
- **THEN** all of them are in the Cadence & running form category

#### Scenario: Dive
- **WHEN** a dive has depth, ppO2, CNS, N2 load, NDL, deco stop depth and time, TTS, gas switch, dive alert or tank pressure data
- **THEN** all of them are in the Dive category, and the dive summary (such as max depth and bottom time) is core

#### Scenario: Core fields aren't hidden
- **WHEN** an athlete sets every category of a Public activity to Only me
- **THEN** any viewer still sees its sport, title, date, distance, elapsed and moving time, elevation gain, and its distance, altitude and temperature charts

#### Scenario: Uncategorized data is core
- **WHEN** an activity has gears, e-bike, jumps, grit or flow data
- **THEN** it is stored and served at the activity's visibility

### Requirement: Each category has its own visibility
Each detail category of an activity SHALL have one visibility: Public, Followers, Mutuals or Only me. It SHALL be the activity's visibility or narrower, in the order Public, Followers, Mutuals, Only me. Narrowing the activity SHALL narrow every wider category to match. Widening the activity SHALL leave the categories as they are.

#### Scenario: Public run, private heart rate
- **WHEN** an athlete publishes a Public run with Heart rate set to Only me
- **THEN** any viewer sees the run and its pace, and only the athlete sees its average heart rate, max heart rate, heart rate chart and lap heart rates

#### Scenario: Wider not offered
- **WHEN** an athlete picks the Power visibility for a Mutuals activity
- **THEN** only Mutuals and Only me are offered

#### Scenario: Narrowing the activity drags categories
- **WHEN** a Public activity with Public Calories and Followers Power is changed to Mutuals
- **THEN** Calories and Power both become Mutuals

#### Scenario: Widening leaves categories
- **WHEN** a Followers activity with Followers Heart rate is changed to Public
- **THEN** Heart rate stays Followers

### Requirement: Summary fields stored at their category's tier
A summary field whose category is at the activity's visibility SHALL be on the `feed.activity` record. A field whose category is narrower SHALL NOT be on the `feed.activity` record. Instead it SHALL be on a `feed.activityDetails` record, with the activity's rkey, in the athlete's tier space for that category's visibility. There SHALL be at most one details record per tier, and none in the repo.

#### Scenario: Public activity, Followers power
- **WHEN** an athlete publishes a Public ride with Power set to Followers
- **THEN** the repo's `feed.activity` record has no power fields, and their `followers` space has a `feed.activityDetails` record with the ride's rkey holding average, max and normalized power and work

#### Scenario: Two hidden tiers
- **WHEN** a Public ride has Calories at Mutuals and Heart rate at Only me
- **THEN** the calories are on a details record in the `mutuals` space, the heart rate fields are on one in the `self` space, and the repo's record has neither

#### Scenario: Nothing hidden
- **WHEN** every category of an activity is at the activity's visibility
- **THEN** the activity has no details record anywhere

### Requirement: Hidden start time keeps only the date
When an activity's Start time category is narrower than the activity, the `feed.activity` record's `startedAt` SHALL be the start of the activity's local day (by its `utcOffset`). The exact start SHALL be on the details record for the Start time tier. A viewer admitted to that tier SHALL be shown the exact start, and every other viewer SHALL be shown only the date.

#### Scenario: Morning run with hidden start
- **WHEN** an athlete publishes a Public run that started at 06:12 local time with Start time set to Only me
- **THEN** the repo record's `startedAt` is that day's local midnight, and other viewers see the date with no time of day

#### Scenario: Owner sees the time
- **WHEN** the athlete opens that run
- **THEN** it shows the 06:12 start

#### Scenario: Date filters
- **WHEN** a viewer filters an athlete's activities by a start date range
- **THEN** an activity with a hidden start time matches by its date, and the filter reveals nothing finer

### Requirement: Hidden map leaves no polyline
When an activity's Map category is narrower than the activity, the `feed.activity` record SHALL carry no `polyline`. The trimmed polyline SHALL be on the details record for the Map tier. Privacy-zone trimming SHALL apply to it as it does to every polyline.

#### Scenario: Public run with map Only me
- **WHEN** an athlete publishes a Public run with Map set to Only me
- **THEN** other viewers see the run without a map, and the athlete sees the trimmed map on their own feed card

#### Scenario: Elevation chart is core
- **WHEN** a Public run has Map set to Only me
- **THEN** any viewer sees its elevation chart

### Requirement: Streams split by tier
An activity's channels SHALL be stored in one `feed.activityStreams` record per tier that holds any of them. Each record, with the activity's rkey, SHALL be in the repo for Public or in the athlete's tier space otherwise, and SHALL hold only the channels whose category is at that tier, plus the time array. Core channels SHALL be in the activity's tier record. Where a streams record is stored SHALL be its only visibility.

#### Scenario: Public speed, Only-me heart rate
- **WHEN** a Public run has Pace/Speed Public and Heart rate Only me
- **THEN** the repo's streams record holds speed and no heart rate, and the `self` space's streams record holds heart rate and no speed

#### Scenario: All channels at one tier
- **WHEN** every category with channels is at the same tier
- **THEN** the activity has a single streams record, at that tier's place

### Requirement: Laps and legs split by tier
Each lap and leg field SHALL be stored in the streams record for its category's tier. Each streams record that holds any field of a lap or leg SHALL also hold that lap's or leg's start and elapsed time, so viewers can line them up. The lap and leg structure (start, elapsed and moving time, distance, ascent and descent, intensity, trigger, sport), lengths and sets are core, and SHALL be in the activity's tier record.

#### Scenario: Lap heart rate hidden
- **WHEN** a Public run with laps has Heart rate Only me
- **THEN** the laps in the repo's streams record have no heart rate fields, and the `self` record's laps hold each lap's start, elapsed time, and average and max heart rate

### Requirement: Viewers get the tiers they're admitted to
For each activity a viewer may see, the AppView SHALL serve the union of its data from every tier the viewer is admitted to: the activity record, details records and streams records. It SHALL serve nothing from a tier the viewer isn't admitted to, and each field, channel and lap SHALL be served at most once. When the same field is in two tiers the viewer is admitted to, the narrower tier's value SHALL win.

#### Scenario: Follower sees Followers power
- **WHEN** an effective follower opens a Public ride whose Power is Followers and Heart rate Only me
- **THEN** they get the ride's power summary and power chart, and no heart rate

#### Scenario: Stranger
- **WHEN** a signed-out viewer opens that ride
- **THEN** they get neither power nor heart rate

#### Scenario: Details in the repo ignored
- **WHEN** an athlete's repo holds a `feed.activityDetails` record
- **THEN** no viewer is served anything from it

#### Scenario: Details for an activity the viewer can't see
- **WHEN** a viewer may not see a Followers activity but is admitted to the `followers` tier of another of the athlete's activities
- **THEN** they get nothing of the hidden activity's details

### Requirement: Clients show only what they're given
The feed card, activity page, charts and lap table SHALL show a hidden category's data only when the viewer was served it. They SHALL leave it out without a placeholder value. On their own activity, the athlete SHALL see every category, each marked with its visibility where narrower than the activity.

#### Scenario: Hidden heart rate on the feed card
- **WHEN** a viewer not admitted to a run's Heart rate tier sees it in their feed
- **THEN** the card shows no heart rate stat and no gap where one would be

#### Scenario: Athlete's own view
- **WHEN** the athlete opens their Public run with Heart rate Only me
- **THEN** they see the heart rate stats and chart, marked as Only me

### Requirement: Changing a category's visibility never loses data
When an activity's category visibilities change, the client SHALL write each place the data moves to before removing it from the place it leaves. If the change fails part way, every field and channel SHALL still be stored in at least one place, and a retry SHALL finish it.

#### Scenario: Heart rate narrowed from Public to Only me
- **WHEN** an athlete narrows Heart rate on a Public run and the rewrite of the repo record fails after the `self` details and streams were written
- **THEN** the heart rate data is in the `self` space and still in the repo, and retrying the save removes it from the repo

#### Scenario: Category widened
- **WHEN** an athlete widens Calories on a Public run from Only me to Public
- **THEN** the calories end up on the repo's activity record, and the `self` details record no longer has them

### Requirement: Default visibility per category
An athlete SHALL be able to set a default visibility for each category in Settings, stored on their `actor.activityDefaults` record in their `self` space. A category default wider than the default activity visibility SHALL be read as the activity default. A category never set SHALL start at the default activity visibility. There SHALL be no separate charts default.

#### Scenario: Never set
- **WHEN** an athlete who never set any defaults starts a new activity
- **THEN** the activity and every category start as Public

#### Scenario: Only the activity default set
- **WHEN** an athlete whose defaults record sets only the activity visibility, Followers, starts a new activity
- **THEN** every category starts as Followers

#### Scenario: Old charts default ignored
- **WHEN** an athlete's defaults record still has a `chartsVisibility` of Only me and no category defaults, with activity Public
- **THEN** every category of a new activity starts as Public

#### Scenario: Category default capped
- **WHEN** an athlete sets the default activity visibility to Mutuals while the Calories default is Public
- **THEN** the Calories default becomes Mutuals

#### Scenario: Changing defaults leaves past activities
- **WHEN** an athlete changes the Heart rate default to Only me
- **THEN** their past activities keep their heart rate visibility

### Requirement: Categories chosen for each activity
Recording, manual entry, import and batch import SHALL each let the athlete set every category's visibility, prefilled from the defaults, on every platform. Changing them SHALL affect only that activity or batch. Editing an activity SHALL let the athlete change them, within the cap. A category with no data on the activity SHALL NOT be offered when editing.

#### Scenario: Override for one activity
- **WHEN** an athlete whose Power default is Only me sets one import's Power to Public
- **THEN** that activity's power is Public, and the default stays Only me

#### Scenario: Batch import
- **WHEN** an athlete imports several dives at once with Heart rate Followers
- **THEN** every activity in the batch has Heart rate Followers

#### Scenario: Edit after publishing
- **WHEN** an athlete opens edit for a Public ride and sets Power to Mutuals
- **THEN** after saving, only the athlete and their mutuals get its power data

### Requirement: Activities from before categories
An activity with no details records and a single streams record SHALL be served as before: its record's fields at the activity's visibility, and its channels at the streams record's tier. Editing one SHALL show each category at the visibility its data is served at now. Saving SHALL store it by category, after warning if core charts would become more visible.

#### Scenario: Old Public run with Only-me charts
- **WHEN** a viewer opens a Public run published before this change with Only-me charts
- **THEN** they see its summary stats and no charts, as before

#### Scenario: Editing an old run
- **WHEN** the athlete opens edit for that run
- **THEN** every category with channels shows Only me and the others Public

#### Scenario: Core charts widened by the first edit
- **WHEN** the athlete saves that run's edit
- **THEN** the client first says its elevation and distance charts will become Public, and saves only if they confirm

### Requirement: Own exports hold every category
Exporting their own activity SHALL give the athlete a file with every field and channel the activity has, whatever each category's visibility.

#### Scenario: Export with hidden heart rate
- **WHEN** an athlete exports a Public run whose Heart rate is Only me
- **THEN** the exported file holds its heart rate samples
