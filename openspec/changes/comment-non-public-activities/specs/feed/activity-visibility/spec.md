# Spec Delta

## REMOVED Requirements

### Requirement: No comments on non-public activities
**Reason**: Comments are now taken at every visibility. Where each one is stored and who sees it are defined by the `feed/comments` capability.
**Migration**: None for data. Comments given while an activity was Public stay indexed, and are shown again under `feed/comments`' "Who sees a comment" rule. Clients stop hiding the comment field and count on non-public activities.
