# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Registered for write notifications`
- TO: `### Requirement: Registered for update notifications`

- FROM: `### Requirement: Deletion notifications purge a space at once`
- TO: `### Requirement: Deletion notices confirmed before a space is dropped`

## MODIFIED Requirements

### Requirement: AppView joins as a member
To read a tier space, the AppView SHALL obtain a space credential from the space's authority PDS with `com.atproto.space.authorizeSpaceSync`, using a delegation token it signs as itself, addressed to the authority's `#atproto_pds` service, and SHALL reuse the credential until shortly before it expires.

#### Scenario: Credential for a tier space
- **WHEN** the AppView joins alice's `mutuals` space
- **THEN** alice's PDS asks `checkAccess` about the AppView's DID as `repo`, the AppView answers `read: true` for itself, and the AppView holds a credential for that space

#### Scenario: Delegation token audience
- **WHEN** the AppView signs a delegation token for a space whose authority is alice
- **THEN** the token's `aud` is alice's DID followed by `#atproto_pds`, and its header `kid` is `#atproto`

### Requirement: Registered for update notifications
The AppView SHALL register for `com.atproto.space.notifySpaceUpdate` for every space it syncs by passing `notify` (its public base URL and a token it issued for that space) in each `authorizeSpaceSync` for it. A registration lasts only as long as the credential of its latest authorization, so the AppView SHALL reauthorize each space before that credential expires. It SHALL answer `com.atproto.space.confirmRegistration` by echoing its challenge, only for the token it issued for that space.

#### Scenario: Registration renewed
- **WHEN** a space's registration lapses within the renewal margin
- **THEN** the AppView's next sync of it reauthorizes with the same `notify`, and notices keep arriving

#### Scenario: Token kept across authorizations
- **WHEN** the AppView authorizes a space it has authorized before
- **THEN** its `notify` carries the same endpoint and token as the first time: 32 random bytes made for that space alone and stored with its sync state

#### Scenario: Registration confirmed
- **WHEN** alice's PDS calls `confirmRegistration` with her `followers` space, the token the AppView issued for it and a challenge
- **THEN** the AppView answers with that challenge

#### Scenario: Confirmation with another token
- **WHEN** a `confirmRegistration` carries a token the AppView didn't issue for the space it names, one issued for another space included
- **THEN** the AppView answers `400 InvalidToken`

#### Scenario: Registration not confirmed
- **WHEN** an authorization is answered `RegistrationNotConfirmed`
- **THEN** that sync fails and is retried with backoff, and nothing indexed from the space is removed

#### Scenario: Registration expired
- **WHEN** the host sends `notifySpaceUpdate` with a `#registrationExpired` update for a space the AppView syncs
- **THEN** the AppView syncs the space at once, reauthorizing first, and catches up on what it missed

### Requirement: Writes indexed on notification
The AppView SHALL accept `com.atproto.space.notifySpaceUpdate` callouts, which carry no service auth, and SHALL authenticate each by its token, compared in constant time with the one it issued for the space named. For a `com.atproto.space.defs#repoWrite` or `#repoRemoved` update of the space authority's repo, it SHALL then fetch the space's ops since the last revision it indexed, and apply them in order. It SHALL ignore an update of another repo, and an update of a kind it doesn't know.

#### Scenario: Followers activity appears
- **WHEN** alice puts an activity in her `followers` space
- **THEN** after the notification, an effective follower of alice sees it in their timeline

#### Scenario: Deleted from a space
- **WHEN** alice deletes an activity from her `mutuals` space
- **THEN** after the notification, the AppView no longer serves it at the Mutuals tier

#### Scenario: Forged notification
- **WHEN** a `notifySpaceUpdate` arrives with a token the AppView didn't issue for the space it names, with service auth from the space's authority or without
- **THEN** the AppView answers `400 InvalidToken` and fetches nothing

#### Scenario: Another repo's update
- **WHEN** a `#repoWrite` update names a repo other than the space's authority
- **THEN** the AppView answers it and fetches nothing

#### Scenario: Unknown update kind
- **WHEN** a `notifySpaceUpdate` with a valid token carries an update whose `$type` the AppView doesn't know
- **THEN** the AppView answers it and changes nothing

#### Scenario: Duplicate notification
- **WHEN** the same notification is delivered twice
- **THEN** the index is the same as after one delivery

### Requirement: Periodic reconcile
The AppView SHALL fetch every synced space's ops since the last indexed revision at least every 5 minutes, so that writes whose notices were lost (the host sends write notices best effort) are indexed without a new write.

#### Scenario: Notification lost
- **WHEN** the AppView was down when alice wrote to a space, so the write's notice never reached it
- **THEN** the next reconcile indexes alice's write

### Requirement: Space sync failures are contained
A failure to sync one space, such as a refused credential or a deleted space, SHALL NOT stop the AppView from syncing other spaces or from indexing Jetstream. Only a space whose authorization answers `SpaceDeleted` SHALL stop being synced at once, its records removed. A space answered `SpaceNotFound`, which is also a refusal, SHALL keep its records and back off, and be dropped only after 24 hours of `SpaceNotFound` answers. Any other failure SHALL keep the space's records and back off.

#### Scenario: Space deleted
- **WHEN** alice deletes her `mutuals` space
- **THEN** once an authorization of it is answered `SpaceDeleted`, the AppView stops syncing it and no longer serves activities from it

#### Scenario: PDS unreachable
- **WHEN** one athlete's PDS can't be reached during reconcile
- **THEN** other spaces reconcile normally, and that space is retried later

#### Scenario: Refusal while the managing app is unreachable
- **WHEN** alice's PDS can't reach the AppView's `checkAccess` for a while and answers the AppView's authorizations of her `followers` space with `UpstreamFailure`
- **THEN** the AppView keeps serving the activities it indexed from that space to the viewers its tier admits, retries with backoff, and resumes syncing once a credential is issued again

#### Scenario: Refusal answered as not found
- **WHEN** alice's PDS answers the AppView's authorizations of her `followers` space with `SpaceNotFound` for a while
- **THEN** the AppView keeps serving what it indexed from that space, retries with backoff, and resumes syncing once a credential is issued again

#### Scenario: Suspended authority or failed registration
- **WHEN** an authorization of alice's space is answered `RegistrationNotConfirmed`, `RepoTakendown` or `RepoDeactivated`
- **THEN** the AppView keeps serving what it indexed from that space, retries with backoff, and never drops the space for it

#### Scenario: Space missing for a day
- **WHEN** every sync of a space has been answered `SpaceNotFound` for 24 hours
- **THEN** the AppView stops syncing it and removes everything indexed from it

#### Scenario: Not-found streak broken
- **WHEN** a space answered `SpaceNotFound` and a later sync succeeds or fails with another error
- **THEN** the 24 hours start over at the next `SpaceNotFound`

### Requirement: Deletion notices confirmed before a space is dropped
The AppView SHALL treat a `notifySpaceUpdate` whose update is `#spaceDeleted`, authenticated by its token like any other, as a prompt: it SHALL sync the space at once, reauthorizing first, and stop syncing it and remove everything indexed from it only when that authorization is answered `SpaceDeleted`. A deletion notice alone SHALL NOT remove anything. Repeated notices, and notices for a space it doesn't sync, SHALL succeed.

#### Scenario: Space deleted
- **WHEN** alice deletes her `followers` space and her PDS notifies the AppView
- **THEN** the AppView reauthorizes the space at once, is answered `SpaceDeleted`, and no longer serves any activity, like, comment or other record indexed from that space

#### Scenario: Notice for a live space
- **WHEN** a deletion notice with a valid token arrives for a space whose authorization still succeeds
- **THEN** the AppView keeps syncing and serving the space, and removes nothing

#### Scenario: Notification repeated
- **WHEN** the same deletion notification arrives twice
- **THEN** both calls succeed and the second changes nothing

#### Scenario: Forged deletion
- **WHEN** a `#spaceDeleted` update arrives with a token the AppView didn't issue for the space it names
- **THEN** the AppView answers `400 InvalidToken` and keeps syncing and serving the space

#### Scenario: Space recreated
- **WHEN** alice recreates a space at the same URI after it was deleted and her client announces it
- **THEN** the AppView joins it as a new space and indexes only what the new space holds

### Requirement: Pulled ops indexed only under a verified commit
Each `listRepoOps` the AppView makes as a syncer SHALL ask for a commit MAC (`Accept-Signature`, profile `atproto-space-commit`) keyed by HPKE with the authority's `#atproto_kem` key and a fresh key share each time. It SHALL apply a pull's ops and record its revision only after the commit on its last page (every page has one) has a valid MAC for that space and repo: an RFC 9421 `hmac-sha256` signature over exactly the `Atproto-Space-Commit` header, which matches the body's commit.

#### Scenario: Verified pull
- **WHEN** alice writes to her `followers` space and the AppView pulls the new ops with a valid commit MAC
- **THEN** the ops are indexed and the commit's revision becomes the space's last indexed revision

#### Scenario: Commit for another space
- **WHEN** a pull's commit names a different space or repo than the one requested
- **THEN** the AppView indexes none of that pull's ops and keeps the previous revision

#### Scenario: Signed header disagrees with the body
- **WHEN** the `Atproto-Space-Commit` header a valid MAC covers gives a `ver`, `repo`, `space`, `rev` or `data` other than the body's commit
- **THEN** the MAC check fails as if the MAC were wrong, and none of the pull's ops are indexed

#### Scenario: Signature under another label
- **WHEN** the host answers with the MAC under a label other than the one the AppView asked for
- **THEN** the AppView finds it by its `atproto-space-commit` tag and checks it as usual

#### Scenario: MAC fails twice
- **WHEN** a pull's commit MAC doesn't verify, and still doesn't after the AppView re-resolves alice's DID document and pulls again with a new key share
- **THEN** that space's sync fails and is retried with backoff, none of the pull's ops are indexed, and other spaces keep syncing

#### Scenario: Key rotated
- **WHEN** alice's `#atproto_kem` key changed since the AppView last resolved her DID document
- **THEN** the first MAC fails, the retry with her new key verifies, and the pull is indexed

## REMOVED Requirements

### Requirement: Syncer service in the DID document
**Reason**: The Nov 12 alpha removes `registerNotify` and service-auth notifications. A syncer registers by passing an endpoint URL and its own token in `authorizeSpaceSync`, so the host never resolves a syncer service id.
**Migration**: The `#activitytracker_space_syncer` entry is gone from `did.json`; the `#activity_appview` and `#activitytracker_space` entries are unchanged. Registration is covered by "Registered for update notifications".
