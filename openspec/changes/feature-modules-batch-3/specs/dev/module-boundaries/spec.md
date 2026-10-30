## MODIFIED Requirements

### Requirement: Feature module kinds
Every feature SHALL be split into three modules:
- an API module: its screen keys, states and events;
- a presenter module;
- a UI module.

No shared module and no app module SHALL hold a feature's screens, states, events, presenters or UIs. Each module's kind SHALL be recognizable from its name alone.

#### Scenario: Moved feature
- **WHEN** a feature has been moved into feature modules
- **THEN** its screens, states and events are in its API module, its presenters in its presenter module and its UIs in its UI module, and each module's name ends in `-api`, `-presenter` or `-ui`

#### Scenario: A new screen
- **WHEN** a screen is added to the app
- **THEN** it is added to a feature's API, presenter and UI modules, not to a shared module or the app module
