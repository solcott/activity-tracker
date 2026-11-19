# Spec Delta

## ADDED Requirements

### Requirement: A worker can be restarted on request
The AppView SHALL be able to restart a running or backing-off loop or stream worker on request: end its current run, optionally run a step while it is stopped, then start a new run at once with no pause. Such a restart SHALL be counted in the worker's status apart from crash restarts, SHALL NOT lengthen the pause before a later crash restart, and SHALL NOT stop work the worker started in the shared worker scope.

#### Scenario: Restart a loop
- **WHEN** the handle sweep is restarted on request
- **THEN** its current run ends, a new run starts without a pause, its requested restart count is one and its crash restart count is unchanged

#### Scenario: Step while stopped
- **WHEN** the Jetstream worker is restarted with a step that writes its cursor
- **THEN** the step runs after the current run has ended and before the next begins

#### Scenario: Syncs already running
- **WHEN** the space syncer is restarted while it has syncs of single spaces in progress
- **THEN** those syncs carry on to completion

#### Scenario: Not restartable
- **WHEN** a restart is requested for a disabled, stopped or one-off worker, or one whose restart is already in progress
- **THEN** it is refused and the worker's status is unchanged
