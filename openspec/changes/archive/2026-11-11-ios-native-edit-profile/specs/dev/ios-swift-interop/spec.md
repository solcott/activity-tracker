# Spec Delta

## ADDED Requirements

### Requirement: A natively picked profile photo reaches its presenter as shared code prepares it
A profile photo the iOS app picks natively SHALL be handed to shared code, which SHALL scale and re-encode it exactly as it does for the shared UI's pick, and the result SHALL reach the presenter through the event the shared UI sends, with no contract change for iOS.

#### Scenario: A large photo
- **WHEN** Swift hands shared code a 2,000 × 1,500 photo
- **THEN** the presenter receives a JPEG no more than 512 pixels on its long edge

#### Scenario: A HEIC
- **WHEN** the photo picked is a HEIC
- **THEN** the presenter receives it as a JPEG, as for any other photo

#### Scenario: Not a picture
- **WHEN** the bytes picked can't be read as a picture
- **THEN** nothing reaches the presenter and the form is as it was

### Requirement: A date held as text crosses to Swift as the same day
A calendar date a contract holds as `YYYY-MM-DD` text SHALL reach a native date control as that day, a day chosen there SHALL reach the presenter as that day's text, and empty or malformed text SHALL reach Swift as no date, in any time zone the device is in.

#### Scenario: A birthdate west of UTC
- **WHEN** alice's birthdate is 1990-04-12 and the device is in a time zone west of UTC
- **THEN** the native calendar shows 12 April 1990, and choosing 13 April sends "1990-04-13"

### Requirement: A choice among an enum's members reaches Swift one member at a time
Where a contract holds a list of an enum's members that a native view needs (which detail groups are now hidden), the native view SHALL ask a shared condition per member rather than read the list, since such a list can't cross to Swift.

#### Scenario: Now hidden, read per group
- **WHEN** the Location group is now hidden
- **THEN** the native view learns it by asking about Location, and reads no list of groups
