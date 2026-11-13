## ADDED Requirements

### Requirement: Swipe back works on every native screen
An athlete SHALL be able to go back by swiping from the screen's leading edge on every screen that has one to go back to. The swipe SHALL follow the finger and SHALL be cancellable.

#### Scenario: An activity's page
- **WHEN** an athlete swipes from the left edge of an activity's page
- **THEN** the page follows the finger, the feed shows beneath it, and letting go past half way returns to the feed

#### Scenario: Cancelled
- **WHEN** an athlete starts the swipe and lets go near the edge
- **THEN** the activity's page settles back and stays open

#### Scenario: A tab's first screen
- **WHEN** an athlete swipes from the left edge of the feed
- **THEN** nothing closes and the app keeps responding

#### Scenario: A map
- **WHEN** an athlete swipes from the left edge of the full-screen route map
- **THEN** the map screen goes back and the map itself doesn't pan

### Requirement: Every native screen turns with the device
The iOS app SHALL show upright and on either side on an iPhone, and any way up on an iPad, turning with the device on every screen. Turning SHALL keep each tab's stack, the screen on top and what it shows.

#### Scenario: An iPhone turned on the feed
- **WHEN** an athlete turns an iPhone showing the feed onto its side
- **THEN** the feed and the tabs lay out across the wider screen, at the same place in the feed

#### Scenario: An activity turned
- **WHEN** an athlete turns an iPhone showing an activity onto its side
- **THEN** the activity lays out across the wider screen, and back returns to the screen it was opened from

#### Scenario: An iPad upside down
- **WHEN** an athlete turns an iPad upside down
- **THEN** the app turns to stay upright for them

#### Scenario: An iPhone upside down
- **WHEN** an athlete turns an iPhone upside down
- **THEN** the app stays as it was

### Requirement: The iOS app draws nothing with Compose
The iOS app SHALL draw every screen, sheet and sub-screen natively, and SHALL ship none of Compose UI, Compose Foundation, Compose Material, Skia or a library that only draws Compose UI. The presenters SHALL still run on the Compose runtime. `./gradlew check` SHALL fail when the iOS app's Kotlin would resolve any of those UI libraries.

#### Scenario: A UI library reaches the iOS build
- **WHEN** a module the iOS app links adds a dependency that brings Compose UI with it
- **THEN** `./gradlew check` fails and names the library and the module path that brought it

#### Scenario: The release app
- **WHEN** the Release app is built for iOS
- **THEN** its binary holds no Skia or Compose UI symbols and its bundle no Compose resources

#### Scenario: Other platforms
- **WHEN** the Android, desktop or web app is built
- **THEN** it draws its screens with the shared Compose UI exactly as before

## REMOVED Requirements

### Requirement: Screens without a native view are unchanged
**Reason**: Every screen the iOS app can show has a native view (held by "Every screen has a native view"), so the shared UI no longer draws any screen on iOS and the iOS app no longer contains it.
**Migration**: None. A new screen needs its native view before it can ship, which `check` already requires.

### Requirement: Swipe back works on every screen
**Reason**: It covered screens drawn by the shared UI, which no longer exist on iOS.
**Migration**: "Swipe back works on every native screen" holds the same behaviour.

### Requirement: The iOS app turns with the device
**Reason**: It covered screens drawn by the shared UI, which no longer exist on iOS.
**Migration**: "Every native screen turns with the device" holds the same behaviour.
