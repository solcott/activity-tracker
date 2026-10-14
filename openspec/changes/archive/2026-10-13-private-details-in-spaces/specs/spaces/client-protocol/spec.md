# Spec Delta

## MODIFIED Requirements

### Requirement: Owner writes
The signed-in athlete SHALL be able to create one of their spaces, look it up, update its policies, and put and delete records in it, all authenticated with their session.

#### Scenario: Create a space
- **WHEN** the athlete creates a space of type `app.activitytracker.space.athlete` with a given key and the AppView as managing app
- **THEN** the space exists at `at://{did}/space/app.activitytracker.space.athlete/{key}`

#### Scenario: Create an existing space
- **WHEN** the athlete creates a space that already exists
- **THEN** the client reports that it exists rather than failing, so callers can create spaces lazily

#### Scenario: Update a space's policies
- **WHEN** the athlete updates their space's read policy to name a different managing app
- **THEN** a later lookup of the space returns the new read policy

#### Scenario: Put is idempotent
- **WHEN** the athlete puts the same record at the same collection and rkey twice
- **THEN** both calls succeed and the space holds one record with that value

#### Scenario: Delete a record
- **WHEN** the athlete deletes a record from their space
- **THEN** a later owner read of it returns not found
