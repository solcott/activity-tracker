# Spec Delta

## MODIFIED Requirements

### Requirement: Failed segment jobs are cleared
A failed segment job SHALL be removed when a job of the same kind and subject later succeeds, when the account whose record is its subject is deleted, when an operator requeues or drops it (admin/job-queues), and thirty days after it gave up.

#### Scenario: Later success
- **WHEN** a failed `segment` job's subject is enqueued again and that job succeeds
- **THEN** the failed job is gone

#### Scenario: Account deleted
- **WHEN** alice's account is deleted while a failed `track` job names her segment track
- **THEN** that failed job is gone

#### Scenario: Old failure
- **WHEN** a failed job gave up more than thirty days ago
- **THEN** it is removed

#### Scenario: Operator requeues it
- **WHEN** an operator requeues a failed `track` job
- **THEN** the failed job is gone and a `track` job for its subject is queued

#### Scenario: Operator drops it
- **WHEN** an operator drops a failed `group` job
- **THEN** the failed job is gone and nothing is queued for it
