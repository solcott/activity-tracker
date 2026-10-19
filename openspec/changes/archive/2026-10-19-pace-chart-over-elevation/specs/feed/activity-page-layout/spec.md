# Spec Delta

## MODIFIED Requirements

### Requirement: Pace card
For a sport timed by pace (runs, walks, hikes and swims), the speed card SHALL be a Pace card. Its headline SHALL give the average and best pace, per kilometer or mile, or per 100 m or yd swimming, and its chart SHALL plot pace with the faster pace at the top. Where the page draws elevation behind charts, the Pace card's chart SHALL draw it as every other card does, higher ground at the top, and SHALL NOT flip it with the pace line.

#### Scenario: Pace card
- **WHEN** a pool swim with a speed stream is opened
- **THEN** it shows a Pace card with the average and best pace per 100 and a pace chart, and no speed card

#### Scenario: Hilly run
- **WHEN** a run with pace and elevation streams is shown 900dp wide
- **THEN** its pace chart shows the run's elevation faintly behind the pace line, with each climb drawn rising, and the line and its fill stay readable over it

#### Scenario: Hilly run on a phone
- **WHEN** the same run is shown 390dp wide
- **THEN** its pace chart shows no elevation behind it
