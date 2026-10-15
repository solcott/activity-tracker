## ADDED Requirements

### Requirement: Viewer sees whether the athlete follows them
For a signed-in viewer, `getProfile` SHALL report the athlete's follow record of the viewer in `viewer.followedBy` when that follow is effective. It SHALL be absent when the athlete doesn't follow the viewer, when the athlete's follow is pending the viewer's approval, and when the viewer is signed out.

#### Scenario: Athlete follows an open-policy viewer
- **WHEN** alice follows bob, bob's policy is `open`, and bob calls `getProfile` for alice
- **THEN** `viewer.followedBy` is the URI of alice's follow of bob

#### Scenario: Follow pending the viewer's approval
- **WHEN** alice follows bob, bob's policy is `approve`, bob hasn't approved alice, and bob calls `getProfile` for alice
- **THEN** `viewer.followedBy` is absent

#### Scenario: Viewer approves the follow
- **WHEN** bob, with policy `approve`, approves alice's follow and then calls `getProfile` for alice
- **THEN** `viewer.followedBy` is the URI of alice's follow of bob

#### Scenario: No follow back
- **WHEN** bob follows alice, alice doesn't follow bob, and bob calls `getProfile` for alice
- **THEN** `viewer.following` is bob's follow and `viewer.followedBy` is absent

#### Scenario: Signed out
- **WHEN** a signed-out client calls `getProfile` for alice
- **THEN** the view has no `viewer.followedBy`
