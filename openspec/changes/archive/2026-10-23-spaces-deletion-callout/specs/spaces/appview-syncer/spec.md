# Spec Delta

## ADDED Requirements

### Requirement: Deletion notifications purge a space at once
The AppView SHALL accept `com.atproto.space.notifySpaceDeleted` callouts, authenticated as service auth from the space's authority addressed to the syncer service. For a space it syncs, it SHALL stop syncing it and remove everything indexed from it before answering. Repeated or unknown notifications SHALL succeed without effect.

#### Scenario: Space deleted
- **WHEN** alice deletes her `followers` space and her PDS notifies the AppView
- **THEN** the AppView no longer serves any activity, like, comment or other record indexed from that space, without waiting for a sync

#### Scenario: Notification repeated
- **WHEN** the same deletion notification arrives twice
- **THEN** both calls succeed and the second changes nothing

#### Scenario: Forged deletion
- **WHEN** a `notifySpaceDeleted` arrives with no valid service auth, or from a DID that isn't the space's authority
- **THEN** the AppView rejects it and keeps syncing and serving the space

#### Scenario: Space recreated
- **WHEN** alice recreates a space at the same URI after it was deleted and her client announces it
- **THEN** the AppView joins it as a new space and indexes only what the new space holds
