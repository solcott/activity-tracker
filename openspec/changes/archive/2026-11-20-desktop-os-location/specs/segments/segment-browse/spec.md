# Spec Delta

## MODIFIED Requirements

### Requirement: Browse opens at the athlete's location
On Android, iOS and desktop, the browse screen SHALL first centre the map on the athlete's current location: on Android and iOS asking for location permission if it isn't granted, and on desktop asking the operating system's location service once, which shows its own permission prompt. When no location is available, because permission is refused, the OS's location is off or has no source, or no fix arrives, it SHALL centre on the start of the athlete's latest activity with a route, and failing that, show a prompt to move the map or search by name. Coming back to the screen SHALL keep the area the athlete last looked at.

#### Scenario: Permission granted
- **WHEN** an athlete with location permission opens Trails & roads mode for the first time
- **THEN** the map centres on their location and lists the trails and roads around it

#### Scenario: Permission refused
- **WHEN** an athlete refuses location permission and their latest activity started in Stowe
- **THEN** the map centres on Stowe

#### Scenario: Desktop
- **WHEN** an athlete whose computer's location service places them in Burlington opens Trails & roads mode in the desktop app
- **THEN** the map centres on Burlington and lists the trails and roads around it

#### Scenario: Desktop without location
- **WHEN** an athlete whose computer's location service is off opens Trails & roads mode in the desktop app, and their latest activity started in Stowe
- **THEN** the app shows no prompt of its own and the map centres on Stowe

## ADDED Requirements

### Requirement: A late location moves only an untouched map
When the desktop app's location arrives after the browse screen has already opened at the latest activity's start or at the prompt, the map SHALL move to the location, unless the athlete has moved the map since it opened, in which case it SHALL stay where they put it. The desktop app SHALL wait for that location no longer than half a minute from opening.

#### Scenario: Answering the permission prompt
- **WHEN** a desktop athlete opens Trails & roads mode, the map opens at their latest activity's start in Stowe, and they then allow location in the OS prompt that places them in Burlington
- **THEN** the map moves to Burlington

#### Scenario: Moved before the answer
- **WHEN** a desktop athlete opens Trails & roads mode, pans the map to Montpelier, and only then allows location in the OS prompt
- **THEN** the map stays on Montpelier

### Requirement: Desktop location is used only to open browse
The desktop app SHALL read the athlete's location only to choose where map browse opens, once per opening of the screen, and SHALL NOT store it, record it, or send it anywhere; only the map's visible area goes to the AppView, as on phones. The desktop app SHALL still offer no GPS recording.

#### Scenario: Feed after allowing location
- **WHEN** a desktop athlete has allowed location for browse and opens the feed
- **THEN** the feed offers no Record action
