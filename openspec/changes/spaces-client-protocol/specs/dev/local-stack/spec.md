# Spec Delta

## ADDED Requirements

### Requirement: Local lexicon authority for space types
The local stack SHALL make the PDS resolve every space-type declaration in the repository's `lexicons/` directory, by publishing them from a local account the PDS treats as its lexicon authority. Bringing the stack up SHALL do this without manual steps.

#### Scenario: Fresh stack resolves the space type
- **WHEN** a developer brings up a fresh stack with `scripts/dev.sh`
- **THEN** the PDS resolves `app.activitytracker.space.athlete` to its `"type": "space"` declaration

#### Scenario: OAuth sign-in with the space scope
- **WHEN** any client signs in to a local account requesting the `space:app.activitytracker.space.athlete` scope
- **THEN** authorization completes and the consent screen lists access to permissioned spaces

#### Scenario: Declaration changes are republished
- **WHEN** a space declaration in `lexicons/` changes and the developer reruns `scripts/dev.sh`
- **THEN** the PDS serves the updated declaration once its lexicon cache refreshes

#### Scenario: Rerunning is safe
- **WHEN** `scripts/dev.sh` runs against a stack whose lexicon authority account already exists
- **THEN** it reuses that account and its DID, and the PDS keeps resolving the declarations
