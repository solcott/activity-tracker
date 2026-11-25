# Spec Delta

## MODIFIED Requirements

### Requirement: Tier spaces created on first use
A save that puts a detail in a tier whose space doesn't exist yet SHALL create that space, of type `app.activitytracker.space.athlete` and keyed by the tier, with the AppView's managing-app id as its read policy. A save SHALL NOT create a space for a tier it leaves empty.

#### Scenario: First Mutuals detail
- **WHEN** an athlete with no `mutuals` space saves a detail as Mutuals
- **THEN** the space `at:space/{did}/app.activitytracker.space.athlete/mutuals` exists with a managing-app read policy naming the AppView, and the detail is saved in it

#### Scenario: Space already exists
- **WHEN** an athlete saves a Mutuals detail and their `mutuals` space already exists
- **THEN** the save succeeds without creating another space

#### Scenario: Public-only save
- **WHEN** an athlete whose details are all Public saves their profile
- **THEN** no tier space is created
