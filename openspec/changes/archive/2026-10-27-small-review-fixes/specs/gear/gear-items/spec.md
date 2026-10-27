# Spec Delta

## ADDED Requirements

### Requirement: A retired date is the day in the device's zone
The retired date an item shows SHALL be the calendar day, in the device's time zone, of the moment it was retired. That's the same zone the app uses for the day a component was mounted, so one form never shows dates read in two different zones.

#### Scenario: Retired late in the evening
- **WHEN** an athlete whose device is set to a zone behind UTC retires their shoes at 23:30 local time
- **THEN** the shoes show that day as their retired date, not the next day

#### Scenario: Same zone as the mounted date
- **WHEN** an athlete opens a component that is both mounted and retired
- **THEN** its mounted date and its retired date are both read in the device's zone
