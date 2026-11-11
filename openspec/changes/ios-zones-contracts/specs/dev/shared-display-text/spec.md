# Spec Delta

## ADDED Requirements

### Requirement: Zones are named the same in both UIs
A computed zone's label and a privacy zone's name SHALL be chosen by shared code, and both UIs SHALL show that choice wherever the zone is listed.

#### Scenario: A computed zone
- **WHEN** a heart rate zone's label is "5a"
- **THEN** both UIs label it "Zone 5a"

#### Scenario: A labelled privacy zone
- **WHEN** a privacy zone is labelled "Home"
- **THEN** both UIs name it "Home"

#### Scenario: A privacy zone with no label
- **WHEN** the second privacy zone in the list has no label
- **THEN** both UIs name it "Zone 2"
