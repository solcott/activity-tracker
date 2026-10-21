# Spec Delta

## MODIFIED Requirements

### Requirement: The athlete is asked once
The app SHALL ask an athlete who has never chosen whether to turn segment matching on. It SHALL explain that a copy of their routes, with their privacy zones removed, will be shared with the AppView to find segments, and that the efforts found can appear on segment leaderboards to those who may see them. Declining SHALL be stored as matching off, and the app SHALL NOT ask again. Settings SHALL let the athlete change the choice at any time.

#### Scenario: Decline
- **WHEN** an athlete is asked and declines
- **THEN** their `actor.segmentPrefs` says matching is off, and no device asks them again

#### Scenario: Change later
- **WHEN** an athlete who declined turns segment matching on in Settings
- **THEN** their `actor.segmentPrefs` says matching is on

#### Scenario: Leaderboards explained
- **WHEN** an athlete is asked
- **THEN** the prompt says their efforts can appear on leaderboards, and that they can keep off them in Settings

## ADDED Requirements

### Requirement: A segment track keeps its leaderboard exclusion
A segment track SHALL say whether its activity is excluded from leaderboards, not excluded when absent. Rewriting a segment track for the same activity, as when it is trimmed again by changed zones, SHALL keep the mark. A segment track written for a new activity, or for a rebuilt combined activity, SHALL start unexcluded. Turning matching off deletes the segment tracks, so it SHALL forget every mark.

#### Scenario: Re-trimmed by new zones
- **WHEN** an athlete excludes a ride, then adds a privacy zone, and the ride's segment track is rewritten
- **THEN** the new segment track is still excluded

#### Scenario: Matching off and on
- **WHEN** an athlete who excluded a ride turns matching off and later on again
- **THEN** the ride's new segment track isn't excluded

### Requirement: Saving the leaderboard choice keeps the matching setting
`actor.segmentPrefs` SHALL also hold whether the athlete hides from leaderboards, not hidden when absent. Saving either setting SHALL keep the other as stored.

#### Scenario: Turn matching off
- **WHEN** an athlete who hides from leaderboards turns matching off
- **THEN** their `actor.segmentPrefs` still says they hide from leaderboards
