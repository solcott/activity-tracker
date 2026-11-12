# Spec Delta

## ADDED Requirements

### Requirement: A native route map highlights a stretch
A native route map SHALL be able to draw a highlighted stretch of the route over it, smoothed as the route is, wider than the route and in the colour the shared map highlights with, on the live map and in the line preview alike. Moving the stretch SHALL NOT reframe the map.

#### Scenario: A new segment's stretch
- **WHEN** the create-segment form shows a ride with its middle kilometre chosen
- **THEN** the map draws that kilometre highlighted over the route, framed on the whole route

### Requirement: A native range slider
On iOS a native form SHALL be able to choose a range with one slider of two thumbs, the span between them in the accent colour, neither thumb passing the other. Each thumb SHALL be adjustable on its own by assistive technology, read with the form's own description of the range.

#### Scenario: VoiceOver
- **WHEN** a VoiceOver user swipes up on the slider's end thumb
- **THEN** the end moves along by a step and the new length is read
