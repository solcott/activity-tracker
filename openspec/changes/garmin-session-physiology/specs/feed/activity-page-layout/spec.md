# Spec Delta

## MODIFIED Requirements

### Requirement: Training load card
When the viewer was served the activity's training load, the page SHALL show it in its own card above the metric cards: FTP, intensity factor, training stress score, aerobic and anaerobic training effect, exercise load, stamina, sweat loss and RMSSD, each where recorded. Stamina SHALL read as its starting and ending potential with its minimum. Sweat loss SHALL be in ml, or in fl oz for imperial units. Viewers not served it SHALL see no such card.

#### Scenario: Owner with training load
- **WHEN** an athlete opens their own ride with a training stress score and training effects
- **THEN** a training load card above the metric cards shows them

#### Scenario: Garmin physiology
- **WHEN** an athlete using metric units opens their own ride with exercise load 187, stamina from 100% to 70% with a minimum of 48%, and sweat loss of 764 ml
- **THEN** the training load card shows exercise load 187, stamina "100% → 70%" with minimum 48%, and sweat loss 764 ml

#### Scenario: Imperial sweat loss
- **WHEN** an athlete using imperial units opens the same ride
- **THEN** its sweat loss reads 25.8 fl oz

#### Scenario: Viewer without zones
- **WHEN** a viewer not admitted to the activity's zones opens it
- **THEN** there is no training load card
