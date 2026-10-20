# feed/combine-suggestions Specification

## Purpose

Spots an athlete's activities that likely belong in one combined activity, either recorded at the same time on different devices or one after another across a device change, a shuttle or a break, and asks the athlete whether to combine them.

## Requirements

### Requirement: Overlapping activities are suggested
After an athlete saves an activity, the app SHALL suggest combining it with another of their activities whose time overlaps it by at least half of the shorter one, unless the save itself already combined it.

#### Scenario: App recording and watch
- **WHEN** an athlete saves an app recording of a run from 7:00 to 7:45 and already has a watch's run from 7:01 to 7:46
- **THEN** the app asks whether to combine the two

### Requirement: Continuations are suggested
After an athlete saves an activity, the app SHALL suggest combining it with another of their activities of the same sport where one starts no more than 2 hours after the other ends. Dives SHALL NOT be suggested as continuations.

#### Scenario: Ski lunch
- **WHEN** an athlete saves a ski activity starting at 13:00 and has one that ended at 11:45
- **THEN** the app asks whether to combine them

#### Scenario: Too long a gap
- **WHEN** an athlete saves a run starting at 18:00 and has a run that ended at 12:30
- **THEN** no suggestion is made

#### Scenario: Repeat dives
- **WHEN** an athlete imports two dives an hour apart
- **THEN** no continuation is suggested

#### Scenario: Different sports
- **WHEN** an athlete saves a ride that starts 10 minutes after a run ended
- **THEN** no suggestion is made

### Requirement: Suggestions say why
A suggestion SHALL describe what it looks like, from where and when the athlete's own data shows the activities ended and started:
- **"Switched devices?"** when the later activity starts within 5 minutes and 300 m of the earlier one's end;
- **"Shuttle or lift between runs?"** when it starts at least 100 m higher than the earlier one ended;
- **"Continue after a break?"** otherwise;
- **"Same activity on another device?"** when the two overlap.

#### Scenario: Watch died
- **WHEN** an athlete's phone ride starts 2 minutes after and 50 m from where their watch ride ended
- **THEN** the suggestion asks whether they switched devices

#### Scenario: Shuttle
- **WHEN** an athlete's second MTB run starts 400 m higher than their first ended, 25 minutes later
- **THEN** the suggestion asks about a shuttle or lift between runs

### Requirement: Asked right after saving
Right after a save that has a suggestion, the app SHALL ask the athlete to combine the saved activity with the suggested one, offering Combine and Not now. Combine SHALL combine them as in `feed/combined-activities`, starting from the earlier activity's title, description and visibility. If the suggested activity is a member of a combined activity, Combine SHALL add the saved one to that combined activity.

#### Scenario: Combine after lunch
- **WHEN** an athlete taps Combine on the suggestion after saving their afternoon ski
- **THEN** a combined ski day of morning and afternoon replaces both in their feed, titled as the morning one

#### Scenario: Add to a combined day
- **WHEN** an athlete saves a third ski run and the suggested activity is already part of a combined ski day
- **THEN** Combine adds the run to that combined day

### Requirement: Banner until the athlete decides
While a suggestion is neither accepted nor dismissed, the athlete's own page of each of its activities SHALL show it as a banner with Combine and Don't combine. Don't combine SHALL stop suggesting that pair, both after saving and on either page. Other viewers SHALL NOT see the banner.

#### Scenario: Not now, then later
- **WHEN** an athlete taps Not now after saving, and later opens either activity
- **THEN** its page shows the suggestion banner

#### Scenario: Don't combine
- **WHEN** an athlete taps Don't combine on the banner
- **THEN** neither activity's page shows that suggestion again

### Requirement: Only recent, uncombined activities are suggested
Suggestions SHALL only involve activities that started in the last 7 days. They SHALL NOT involve:
- a pair already in the same combined activity;
- two different combined activities;
- an activity that is a member of a combined activity, paired with another member of one.

#### Scenario: Old activities
- **WHEN** an athlete opens a ride from last month that has a continuation 30 minutes later
- **THEN** no banner is shown

#### Scenario: Already combined
- **WHEN** an athlete opens a member of a combined ski day
- **THEN** no suggestion to combine it with another member of that day is shown
