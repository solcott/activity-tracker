# Spec Delta

## ADDED Requirements

### Requirement: The create-segment form's text is chosen in shared code
What the create-segment form says that follows a rule (the selection's length and sport group or why it can't be a segment, whether that is a failure, the reasons no segment can be made, the public note and its link to the activity, and a duplicate's line) SHALL be chosen in shared code that both UIs ask.

#### Scenario: Too short in imperial
- **WHEN** an athlete who reads miles chooses a ride stretch shorter than the ride group's minimum
- **THEN** both UIs say Ride segments must be at least that minimum, in miles or feet the same way
