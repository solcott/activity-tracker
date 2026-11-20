# Spec Delta

## ADDED Requirements

### Requirement: Queues page summary
The queues page SHALL offer a summary with three tiles over every job kind together. Oldest due job: `ok` when none is due or the oldest has been due under 5 minutes, `warn` up to 1 hour, `bad` beyond. Retrying jobs: `warn` when any, else `ok`. Jobs that gave up: `bad` when any, else `ok`. Each tile SHALL link to the part of the page that lists them.

#### Scenario: Backlog building
- **WHEN** the oldest due `track` job has been due for twenty minutes and no job is retrying or gave up
- **THEN** the oldest due tile shows about twenty minutes and is `warn`, and the other two are `ok`

#### Scenario: Long backlog
- **WHEN** the oldest due job has been due for two hours
- **THEN** the oldest due tile is `bad`

#### Scenario: Jobs gave up
- **WHEN** one `group` job gave up
- **THEN** the gave-up tile shows 1, is `bad` and links to the dead letters list

#### Scenario: Idle queue
- **WHEN** no job is queued and none gave up
- **THEN** all three tiles are `ok`
