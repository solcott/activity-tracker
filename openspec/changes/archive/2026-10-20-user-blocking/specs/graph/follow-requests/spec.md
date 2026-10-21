# Spec Delta

## MODIFIED Requirements

### Requirement: Effective follows
A follow of an athlete SHALL be effective when the athlete's policy is `open`, or when the athlete has published an `app.activitytracker.graph.followApproval` record whose `subject` is the follower. A follow that isn't effective SHALL be pending. An approval SHALL take effect whether it is indexed before or after the follow it approves. Whatever the policy and approvals, a follow SHALL NOT be effective while either the follower or the athlete blocks the other.

#### Scenario: Open policy
- **WHEN** bob follows alice and alice's policy is `open`
- **THEN** bob's follow is effective without any approval

#### Scenario: Approve policy without approval
- **WHEN** bob follows alice, alice's policy is `approve`, and alice has no approval for bob
- **THEN** bob's follow is pending

#### Scenario: Approval indexed first
- **WHEN** the AppView indexes alice's approval of bob before it indexes bob's follow of alice
- **THEN** bob's follow is effective as soon as it is indexed

#### Scenario: Refollow after unfollow
- **WHEN** alice approved bob, bob unfollows alice, and then follows her again
- **THEN** bob's new follow is effective without another approval

#### Scenario: Block makes a follow ineffective
- **WHEN** bob follows alice, alice's policy is `open`, and either alice blocks bob or bob blocks alice
- **THEN** bob's follow of alice isn't effective while the block holds
