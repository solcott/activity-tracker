# Design

## Context

`SessionClients.canUseSpaces` is `athleteSpaceScopes.isNotEmpty()`. `requireSpacesFor` throws an `XrpcException` with `ScopeMissingError` when it's false, and `uiErrorOf` (and the gear presenters' own mapping) turns that into `SignInAgain`/`GearSignInAgain`. Several stores also read `canUseSpaces` synchronously to skip space work. The flag is the Oct 15 alpha's `spacesAlpha: true` in `describeServer`'s output, which isn't in the lexicon. Upstream calls the flag temporary, and beta may replace it.

## Goals / Non-Goals

**Goals:** a single, removable support check; a distinct, actionable refusal.

**Non-Goals:** checking other athletes' PDSes (their spaces simply don't exist, which member reads already report as not found), and hiding non-public options in pickers. Pickers lock to Public already, through `canUseSpaces` (`ActivityDefaults.canChoose`); D4 only changes what they say.

## Decisions

### D1. Resolve once at session start, keep `canUseSpaces` synchronous
`SessionClients` gets a `spacesSupport: SpacesSupport` (`Supported`, `Unsupported` or `Unknown`), filled by a single `describeServer` call when the session graph is created and before the first space use. `canUseSpaces` and `canWriteToSpaces` AND in `spacesSupport != Unsupported`. Callers stay synchronous, because the value is ready before any screen loads. Until it arrives, the value is `Unknown`, so it falls back to scope-only (today's behavior).
- **Alternative:** a suspend `canUseSpaces()`. Rejected: it touches every store for a temporary flag.

### D2. A distinct exception
`requireSpacesFor` throws `SpacesUnsupportedException` when support is `Unsupported`, and the `ScopeMissingError` path stays for the scope. `uiErrorOf` and the gear mappings map it to `UiError.Kind.SpacesUnsupported`, which offers no action.

### D4. Locked pickers say why
With `canUseSpaces` false, the new-activity, edit and Settings pickers lock to Public and used to say "sign in again", which can't help on an unsupported PDS. `VisibilityFields.spacesUnsupported` (from `ActivityDefaults.spacesUnsupported`) makes them say the server doesn't support private activities yet. A picker shown before the lookup answers stays open, and a non-public save from it gets D2's refusal. Added during apply.

### D3. Removal
When upstream drops the flag, `SpacesSupport` becomes `Unknown` for everyone, which is today's behavior. Removing the check is then deleting `spacesSupport`, its line in `canUseSpaces` and `canWriteToSpaces`, `SpacesUnsupportedException` and `spacesUnsupported` with their mappings. The `data-and-auth.md` note says so.

## Risks / Trade-offs

- [Upstream renames or drops the flag while PDSes still support Spaces, so supported PDSes read as `Unsupported` and private features disappear] → each alpha bump change (Oct 29 first) checks the new image's `describeServer` for the flag, and if it's gone, applies D3 in that same change.
