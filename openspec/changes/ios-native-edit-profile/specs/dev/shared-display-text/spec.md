# Spec Delta

## ADDED Requirements

### Requirement: Edit profile reads the same in both UIs
Edit profile's counters, its units' labels, the warning for a detail now hidden, the gender choices with which one shows as chosen, who may see each detail group, whether a field holds a value the lexicon allows, and when Save is offered SHALL be chosen by shared code that both UIs ask.

#### Scenario: A counter
- **WHEN** the bio holds 31 characters
- **THEN** both UIs read "31/256" under it

#### Scenario: A saved self-description
- **WHEN** alice's saved gender is "Agender"
- **THEN** both UIs show Self-describe as chosen, with "Agender" in the description field

#### Scenario: While saving
- **WHEN** the form is saving
- **THEN** neither UI offers Save

#### Scenario: No group chosen
- **WHEN** a group has no visibility chosen
- **THEN** both UIs show it as Only me

### Requirement: Countries are searched the same in both UIs
Which countries the edit profile country choice lists for what was typed, and in what order, SHALL be decided by shared code that both UIs ask: every country by name in the device's language when nothing is typed, and otherwise the country whose code was typed first, then those whose names contain it, ignoring case and accents.

#### Scenario: Accents ignored
- **WHEN** "cote" is typed
- **THEN** both UIs list Côte d'Ivoire

#### Scenario: A code first
- **WHEN** "us" is typed
- **THEN** both UIs list United States first
