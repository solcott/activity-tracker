## MODIFIED Requirements

### Requirement: Browse shows why segments couldn't load
When the segments for an area or a name query can't be loaded, the browse screen SHALL keep any segments it was showing, say why through the app's error messages, and offer to try again.

#### Scenario: Offline while panning
- **WHEN** an athlete pans the map while the device is offline
- **THEN** the lines already drawn stay, and the list says it couldn't load segments because the server couldn't be reached, with a way to try again
