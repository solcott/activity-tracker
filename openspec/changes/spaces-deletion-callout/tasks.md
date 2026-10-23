# Tasks

## 1. Route and purge

- [ ] 1.1 Add `Space.NotifySpaceDeleted` (`{space}`, empty output) to `core/lexicon/.../atproto/Space.kt`, from the pinned image's lexicon. Verify `SpaceSerializationTest` round-trips the input.
- [ ] 1.2 Add `SpaceSyncer.deleted(space)`, which takes the space's lock and runs the same purge as `dropped()` (a no-op for an unknown space), and serve `notifySpaceDeleted` in `spaceSyncRoutes` with the `notifyWrite` verifier and authority check. Verify `SpaceSyncTest` covers each scenario of "Deletion notifications purge a space at once", including that a purge racing a sync of the same space leaves no rows behind.
- [ ] 1.3 Update the `.agents/rules/appview.md` syncer paragraph (deletion is pushed, `SpaceDeleted` on sync stays as the fallback). Verify `./gradlew :server-appview:test` and `scripts/check-agent-sync.sh` pass.

## 2. Integration check

- [ ] 2.1 On the local stack, have alice delete a space the AppView syncs (via `simplespace.deleteSpace` in a smoke step), and confirm the AppView's log shows the purge within seconds and her Followers activity is gone for bob. Verify both.

## Workflow follow-up

- Run `openspec validate spaces-deletion-callout --strict`; archive once merged.
