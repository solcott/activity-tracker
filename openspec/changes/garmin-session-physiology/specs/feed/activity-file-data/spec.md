# Spec Delta

## MODIFIED Requirements

### Requirement: Training load is stored with the zones
An imported activity's FTP, intensity factor, training stress score, aerobic and anaerobic training effect, exercise load, beginning and ending stamina potential, minimum stamina and estimated sweat loss SHALL NOT be on the activity record. They SHALL be stored with the activity's zones, at the narrower of its charts visibility and the athlete's training visibility.

#### Scenario: Public ride, Only-me training
- **WHEN** an athlete whose training is Only me imports a Public FIT ride with an FTP, IF and TSS
- **THEN** the activity record has none of them, and they are stored with its zones in the athlete's `self` space

#### Scenario: Garmin physiology
- **WHEN** an athlete imports a FIT ride whose session records exercise load 186.8, stamina from 100% to 70% with a minimum of 48%, and sweat loss of 764 ml
- **THEN** its training load holds exercise load 187, stamina start 100, end 70 and minimum 48, and sweat loss 764 ml, and the activity record holds none of them

## ADDED Requirements

### Requirement: Garmin physiology read on every platform
FIT import SHALL read exercise load, stamina and sweat loss the same way on Android, iOS, desktop and web, including the session fields the public FIT profile doesn't name. A file without them SHALL import with none stored, not zeros.

#### Scenario: Same file, every platform
- **WHEN** the same Garmin Edge 840 FIT ride is imported on the web app and on iOS
- **THEN** both store the same exercise load, stamina and sweat loss

#### Scenario: Older device
- **WHEN** an athlete imports a FIT ride from a device that records no stamina or sweat loss
- **THEN** its training load holds neither

### Requirement: Exported FIT keeps Garmin physiology
A FIT export SHALL write the activity's exercise load, stamina and sweat loss into its session, where the exporter may see them, in the same fields import reads.

#### Scenario: Round trip
- **WHEN** the owner exports a ride with exercise load 187 and sweat loss 764 ml to FIT and imports that file again
- **THEN** the new activity's training load holds exercise load 187 and sweat loss 764 ml
