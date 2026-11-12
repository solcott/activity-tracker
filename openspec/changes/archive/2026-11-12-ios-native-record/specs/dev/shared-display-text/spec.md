# Spec Delta

## ADDED Requirements

### Requirement: The recording screen's text is chosen in shared code
What the recording screen says and offers that follows a rule (the elapsed time, the distance, the sport's rate and the climb in the
athlete's units, its notices, which controls the recording's status offers and their labels, and whether the type can still change)
SHALL be chosen in shared code that both UIs ask.

#### Scenario: A run in miles
- **WHEN** an athlete who reads miles records a run
- **THEN** both UIs show its distance in miles, its pace per mile and its climb in feet the same way

#### Scenario: Paused
- **WHEN** a recording is paused
- **THEN** both UIs offer Resume, Finish & save and Discard, in that order
