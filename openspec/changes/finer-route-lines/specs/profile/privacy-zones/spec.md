# Spec Delta

## MODIFIED Requirements

### Requirement: Re-trimming finishes after an interruption
A re-trim that doesn't finish, because the app was closed, went offline or failed, SHALL finish in a later session on any of the athlete's devices, without the athlete saving their zones again. This SHALL hold for polylines and segment tracks alike.

#### Scenario: App closed mid re-trim
- **WHEN** an athlete adds a zone and closes the app before every activity is rewritten
- **THEN** the next session rewrites the rest

#### Scenario: Re-trim done
- **WHEN** a re-trim has rewritten every activity
- **THEN** later sessions don't rewrite them again until the zones change or the app's polyline detail does (feed/route-line-detail)

#### Scenario: Segment tracks left behind
- **WHEN** an athlete with segment matching on adds a zone and the app closes before every segment track is rewritten
- **THEN** a later session rewrites the remaining segment tracks by the new zones
