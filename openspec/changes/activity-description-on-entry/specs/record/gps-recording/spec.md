# Spec Delta

## ADDED Requirements

### Requirement: A recording is saved with its description
The recording screen SHALL offer the details form's description field under the title, on every platform. Finishing SHALL save what is typed there, trimmed, as the activity's description; a blank one SHALL save none.

#### Scenario: Described ride
- **WHEN** alice types "Dusty climbs, then a tailwind home." on the recording screen and finishes her ride
- **THEN** the saved ride's description is "Dusty climbs, then a tailwind home."

#### Scenario: Nothing written
- **WHEN** alice finishes a ride with only spaces in the description
- **THEN** the saved ride has no description

## MODIFIED Requirements

### Requirement: The recording screen is native on iOS
On iOS the recording screen SHALL be drawn natively: the elapsed time with the distance, the sport's rate and the climb in the athlete's
units, the activity type (outdoor types only, fixed once recording starts), the title, the description, the gear and who sees it, any
failed finish, a recording offered back after the app stopped, and a change not saved on the device.

#### Scenario: Offered back
- **WHEN** the app stopped during alice's ride and she opens Record again
- **THEN** the screen says the recording stopped when the app closed, with the ride's time and distance so far
