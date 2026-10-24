# Proposal

## Why

Only PDSes running the Spaces alpha serve `com.atproto.space.*`. An athlete on any other PDS (bsky.social today) can still get a session, and the app then decides spaces access from the granted `space:` scope alone (`SessionClients.canUseSpaces`). When the PDS can't actually host spaces, a non-public save fails late with a protocol error, or is shown as "sign in again", which can't help. The Oct 15 alpha added a temporary feature flag for this: `describeServer` returns `spacesAlpha: true` on a Spaces-capable PDS (atproto PR 5626; the thread "Detecting spaces support").

Optional follow-up to `spaces-alpha-2026-10-22`. Not needed on the local stack, where every PDS has Spaces.

## What Changes

- At session start, the client reads the athlete's own PDS's `com.atproto.server.describeServer` once and records whether it reports `spacesAlpha: true`.
- `canUseSpaces` (and `canWriteToSpaces`) is true only when the scope is granted and the PDS supports spaces. A PDS that answers without the flag counts as unsupported. A failed lookup counts as unknown and falls back to the scope alone, so a flaky `describeServer` never hides private data from a PDS that has it.
- A non-public choice on an unsupported PDS is refused up front with its own message ("your server doesn't support private activities yet"), not "sign in again". Public features are unaffected.
- The flag is temporary upstream, so the check is isolated in one place and the design records how to remove it.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `spaces/client-protocol`: spaces are used only on a PDS that reports Spaces support, and an unsupported PDS gets its own refusal.

## Impact

- **`core/lexicon`:** `com.atproto.server.describeServer` descriptor, only the fields read (`did`, `spacesAlpha`), unknown keys ignored.
- **`core/data`:** `SessionClients` (support lookup, `canUseSpaces`), `requireSpacesFor` (distinct exception).
- **`app/presenters`:** `uiErrorOf` maps the new exception to a new `UiError.Kind`. `composeApp` strings and `:app-fixtures` samples get the message.
- **Tests:** `core-data` `MockEngine` tests, presenter tests, a screenshot reference if a sample changes.
