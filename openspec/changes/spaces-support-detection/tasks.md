# Tasks

## 1. Lookup

- [ ] 1.1 Add a `com.atproto.server.describeServer` descriptor to `core/lexicon` reading `did` and `spacesAlpha`, ignoring other keys. Verify a `core-lexicon` test decodes the local PDS's response shape with and without the flag.
- [ ] 1.2 Add `SpacesSupport` to `SessionClients`, resolved once at session start, and AND it into `canUseSpaces`/`canWriteToSpaces` (design D1). Verify `core-data` `MockEngine` tests: flag true → spaces used; flag absent → `canUseSpaces` false with the scope granted; `describeServer` 500 or a network failure → the scope decides.

## 2. Refusal

- [ ] 2.1 Throw `SpacesUnsupportedException` from `requireSpacesFor` on an unsupported PDS (design D2). Verify a `core-data` test that a Followers publish on an unsupported PDS writes nothing and throws it, and that a missing scope on a supported PDS still throws `ScopeMissingError`.
- [ ] 2.2 Map it to `UiError.Kind.SpacesUnsupported` in `uiErrorOf` and the gear presenters, add the message string, and add a sample to `:app-fixtures`. Verify presenter tests for both spec scenarios, `ContentLoadingConformanceTest`, and the screenshot tests (updating references if a sample was added).
- [ ] 2.3 Document `SpacesSupport` and its removal (design D3) in `.agents/rules/data-and-auth.md` under "No `space:` scope, no choice". Verify `scripts/check-agent-sync.sh` passes.

## 3. Integration check

- [ ] 3.1 Run `./gradlew check`, and sign in on the local stack (flag present) to confirm a Followers activity still publishes. Verify both.

## Workflow follow-up

- Run `openspec validate spaces-support-detection --strict`; archive once merged.
