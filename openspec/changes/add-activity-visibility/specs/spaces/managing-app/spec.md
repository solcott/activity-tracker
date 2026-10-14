# Spec Delta

## MODIFIED Requirements

### Requirement: Read access by tier
For a space of type `app.activitytracker.space.athlete`, a `read` check SHALL be decided by the space key: `public` admits any user; `followers` admits a user whose follow of the authority is effective in this app's follow graph; `mutuals` admits a user whose follow of the authority is effective and whom the authority effectively follows; `self` and every other key admit no one. The AppView's own service DID SHALL be admitted for `read` on the `public`, `followers`, `mutuals` and `self` spaces, so it can sync them. Any other space type SHALL be denied.

#### Scenario: Public tier
- **WHEN** any user is checked for `read` on the authority's `public` space
- **THEN** the answer is `authorized: true`, even if neither follows the other

#### Scenario: Effective follower reads the followers tier
- **WHEN** a user whose follow of the authority is effective (the authority's policy is `open`, or the authority approved them) is checked for `read` on the `followers` space
- **THEN** the answer is `authorized: true`

#### Scenario: Pending follower
- **WHEN** a user follows the authority, the authority's policy is `approve`, and the authority hasn't approved them
- **THEN** a `read` check on the `followers` space or the `mutuals` space answers `authorized: false`

#### Scenario: Non-follower and the followers tier
- **WHEN** a user who doesn't follow the authority is checked for `read` on the `followers` space
- **THEN** the answer is `authorized: false`

#### Scenario: Removed follower
- **WHEN** the authority deletes their approval of a follower and the AppView has indexed the deletion
- **THEN** a later `read` check for that follower on the `followers` space answers `authorized: false`

#### Scenario: Mutual reads the mutuals tier
- **WHEN** a user who follows the authority, and whom the authority follows, is checked for `read` on the `mutuals` space, and both follows are effective
- **THEN** the answer is `authorized: true`

#### Scenario: One-way follower
- **WHEN** a user follows the authority but the authority doesn't follow them back, or the reverse
- **THEN** a `read` check on the `mutuals` space answers `authorized: false`

#### Scenario: Unfollow takes effect
- **WHEN** either side of a mutual follow deletes their follow record and the AppView has indexed the deletion
- **THEN** a later `read` check on the `mutuals` space answers `authorized: false`

#### Scenario: Only-me tier
- **WHEN** any user other than the authority and the AppView, mutual or not, is checked for `read` on the `self` space
- **THEN** the answer is `authorized: false`

#### Scenario: AppView reads every tier
- **WHEN** the AppView's own service DID is checked for `read` on the authority's `public`, `followers`, `mutuals` or `self` space
- **THEN** the answer is `authorized: true`, whatever the follow graph says

#### Scenario: AppView and an unknown tier
- **WHEN** the AppView's own service DID is checked for `read` on a space of type `app.activitytracker.space.athlete` with a key other than the four tiers
- **THEN** the answer is `authorized: false`

#### Scenario: Unknown tier
- **WHEN** a `read` check names a space of type `app.activitytracker.space.athlete` with a key other than `public`, `followers`, `mutuals` or `self`
- **THEN** the answer is `authorized: false`

#### Scenario: Other space type
- **WHEN** a check names a space of any other type
- **THEN** the answer is `authorized: false`

#### Scenario: Bluesky follows don't count
- **WHEN** two accounts follow each other only with `app.bsky.graph.follow` records
- **THEN** a `read` check on the `followers` or `mutuals` space answers `authorized: false`

### Requirement: Only the authority writes
A `write` check, or a check with any `access` value other than `read`, SHALL be denied for every user and tier, the AppView's own service DID included.

#### Scenario: Mutual asks to write
- **WHEN** a mutual of the authority is checked for `write` on any of the authority's spaces
- **THEN** the answer is `authorized: false`

#### Scenario: AppView asks to write
- **WHEN** the AppView's own service DID is checked for `write` on any of the authority's spaces
- **THEN** the answer is `authorized: false`

#### Scenario: Unknown access kind
- **WHEN** a check sends an `access` value other than `read` or `write`
- **THEN** the answer is `authorized: false`
