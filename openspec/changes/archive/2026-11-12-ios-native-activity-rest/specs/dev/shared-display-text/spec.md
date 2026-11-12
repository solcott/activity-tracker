# Spec Delta

## ADDED Requirements

### Requirement: The structure tables read the same in both UIs
What the legs, lengths and sets tables say SHALL be chosen once in shared code: their headers, each row's cells (a sport's name, a stroke's or exercise's name, a time, a distance, a pace or speed, a weight), a rest's or transition's row, and each row's spoken sentence.

#### Scenario: An unknown exercise
- **WHEN** a set's exercise is a token the app has no name for, "single_arm_cable_row"
- **THEN** both UIs read it as "Single arm cable row"

### Requirement: A card's extra charts read the same in both UIs
What the power curve, the gears chart, a dive's marks and a card's source caption say SHALL be chosen once in shared code: their titles, axis labels, readouts and descriptions, each mark's label, and the caption's devices in time order.

#### Scenario: The power curve's labels
- **WHEN** a ride's power curve includes 5 seconds, 1 minute and 20 minutes
- **THEN** both UIs label those durations "5s", "1m" and "20m"

### Requirement: The combination sheet reads the same in both UIs
What the owner's combination sheet says SHALL be chosen once in shared code: its title, the members heading, each member's name and remove button, the removal confirmation, the data sources heading, each kind's name, where it comes from or that it's left out, and its switch's spoken label.

#### Scenario: A kind's source
- **WHEN** a combined ride's power comes from an Edge 840
- **THEN** both UIs read its row as Power, from Edge 840
