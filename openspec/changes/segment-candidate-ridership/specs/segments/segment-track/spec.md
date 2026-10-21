# Spec Delta

## MODIFIED Requirements

### Requirement: The AppView keeps segment tracks only while matching is on
The AppView SHALL NOT read or use any segment track data of an athlete whose matching is off, and SHALL use it only to match segment efforts and to count riders on staged candidate segments. When an athlete's matching goes off, their `self` space is deleted, or their account is deleted, the AppView SHALL drop everything it holds from their segment tracks, the efforts matched from them and their rider records on candidates included.

#### Scenario: Matching turned off
- **WHEN** an athlete turns matching off
- **THEN** once the AppView indexes the new setting, it holds nothing from their segment tracks, even before the deletes reach it

#### Scenario: Stray track
- **WHEN** an athlete's `self` space holds a segment track while their setting says matching is off
- **THEN** the AppView doesn't read or use it

#### Scenario: Efforts dropped with the tracks
- **WHEN** an athlete with efforts on several segments turns matching off
- **THEN** once the AppView indexes the new setting, it holds and serves none of those efforts

#### Scenario: Candidate riders dropped with the tracks
- **WHEN** an athlete counted as a rider of several staged candidates turns matching off
- **THEN** once the AppView indexes the new setting, it holds no record that they rode any of them
