# Spec Delta

## ADDED Requirements

### Requirement: The recording screen is native on iOS
On iOS the recording screen SHALL be drawn natively: the elapsed time with the distance, the sport's rate and the climb in the athlete's
units, the activity type (outdoor types only, fixed once recording starts), the title, the gear and who sees it, any failed finish, a
recording offered back after the app stopped, and a change not saved on the device.

#### Scenario: Offered back
- **WHEN** the app stopped during alice's ride and she opens Record again
- **THEN** the screen says the recording stopped when the app closed, with the ride's time and distance so far

### Requirement: The native recording screen's controls follow the recording
The native recording screen SHALL offer Start while idle, Pause while recording, and Resume, Finish & save and Discard while paused.
Discard SHALL ask before throwing the recording away. While finishing, the controls SHALL be held and progress shown.

#### Scenario: Pausing a ride
- **WHEN** alice taps Pause during a ride
- **THEN** the elapsed time stops, and Resume, Finish & save and Discard replace Pause

#### Scenario: Discarding
- **WHEN** alice taps Discard on a paused recording
- **THEN** she is asked first, and the recording is thrown away only when she confirms

### Requirement: The native recording screen asks for location access
The native recording screen SHALL ask for location access while in use when Start is tapped without it, and start recording once it is
given. When access was refused it SHALL say that location is off and offer to open the app's settings.

#### Scenario: First recording
- **WHEN** alice taps Start for the first time
- **THEN** iOS asks for location access while using the app, and recording starts when she allows it

#### Scenario: Access refused
- **WHEN** alice has refused location access and opens Record
- **THEN** the screen says location is off for the app and offers Open Settings
