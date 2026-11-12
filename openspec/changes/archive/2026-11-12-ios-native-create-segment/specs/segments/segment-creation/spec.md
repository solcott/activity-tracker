# Spec Delta

## ADDED Requirements

### Requirement: Creating a segment is native on iOS
On iOS creating a segment SHALL be drawn natively: the route as the athlete's privacy zones leave it on a map with the chosen stretch highlighted, a slider with two ends choosing the stretch, what the stretch is or why it can't be a segment, the name, what will be public, any duplicates to open, the allowance reached and a failed check or publish. When no segment can be made from the activity it SHALL say why instead. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Moving the stretch
- **WHEN** alice drags the slider's end along her ride's route
- **THEN** the highlighted stretch on the map follows it, the map stays framed on the whole route, and the length below the slider changes

#### Scenario: Too short
- **WHEN** alice narrows the stretch below her sport group's minimum
- **THEN** the line below the slider says so in red and Publish is disabled

#### Scenario: A duplicate
- **WHEN** Publish finds that the stretch duplicates an existing segment
- **THEN** the form lists it at the top, and tapping it opens that segment

### Requirement: The native form publishes from its bar
The native create-segment form SHALL offer Publish in its bar, enabled only when the stretch can be a segment, it is named and nothing is pending, and SHALL show progress there while publishing.

#### Scenario: Unnamed
- **WHEN** alice has chosen a valid stretch but typed no name
- **THEN** Publish is disabled until she types one
