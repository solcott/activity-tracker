# Spec Delta

## MODIFIED Requirements

### Requirement: Spaces-capable local PDS
The local PDS SHALL run an image that serves the `com.atproto.space.*`, `com.atproto.memberspace.*` and `com.atproto.managedspace.*` XRPC methods of the 2026-10-29 Spaces alpha. The image SHALL be pinned to an exact digest, so a rebuild never picks up an unreviewed alpha.

#### Scenario: PDS serves simplespace
- **WHEN** a signed-in local account calls `com.atproto.space.createSpace` on the local PDS with a `com.atproto.managedspace.policy`
- **THEN** the PDS creates the space and returns its `at://{did}/space/{type}/{skey}` URI

#### Scenario: Rebuild is reproducible
- **WHEN** the PDS image is rebuilt
- **THEN** it uses the same pinned digest, not whatever the moving `pds-spaces-alpha` tag points to now
