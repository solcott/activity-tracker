# Spec Delta

## ADDED Requirements

### Requirement: Activity forms count the title and description
The edit, import, manual-entry and Record forms SHALL count the title against 100 characters and the description against 1000, as each would be saved (trimmed), on every platform. Once either reaches nine tenths of its limit its field SHALL show how long it is of how long it may be, in the error colour past the limit, and while either is past it Save (Record's Finish & save) SHALL be held back. No form SHALL refuse typing past a limit.

#### Scenario: Near the limit
- **WHEN** alice's description on manual entry reaches 900 characters
- **THEN** "900/1,000" shows under it, and Save is still enabled

#### Scenario: Past the limit
- **WHEN** alice's description reaches 1,005 characters
- **THEN** the counter shows in the error colour and Save is disabled, until she shortens it to 1,000

#### Scenario: An imported file's long notes
- **WHEN** alice imports a GPX file whose description is 1,200 characters
- **THEN** the import form shows it whole, past its limit, and she can shorten it to save

### Requirement: Published titles and descriptions keep to the lexicon
Every activity record a client publishes or rewrites SHALL trim its title and description, leave out one that is blank, and cut one past its limit to its first 100 or 1000 characters, never splitting a character. Characters SHALL be counted as code points, so text within a limit is within the lexicon's `maxGraphemes` and `maxLength`.

#### Scenario: Batch import from an export
- **WHEN** alice imports a Strava export whose CSV gives one activity a 150-character name
- **THEN** that activity is saved with its first 100 characters as its title

### Requirement: The AppView keeps titles and descriptions to their limits
The AppView SHALL cut an indexed activity's title to 100 characters and its description to 1000, never splitting a character, before storing and serving it, whatever client wrote the record.

#### Scenario: A record from another client
- **WHEN** another client publishes an activity with a 1,200-emoji description
- **THEN** the AppView serves its first 1,000 emoji as its description
