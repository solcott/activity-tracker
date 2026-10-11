# Proposal

## Why

Feed filters (`add-feed-filters`), and the Profile reuse after them, need parts of screens that can be embedded and reused, each with its own presenter and UI. Circuit's `circuitx-subcircuit` provides this, and Metro's built-in Circuit codegen already understands `@SubCircuitInject`. But the app doesn't wire SubCircuit up and has no conventions for it.

Landing that setup as its own change lets us prove it works on every platform before any feature depends on it, and keeps feature changes about behavior.

## What Changes

- Add `com.slack.circuit:circuitx-subcircuit` and `circuitx-subcircuit-test` (0.39.0, matching `circuit`) to the modules that hold screen contracts, presenters and UIs, with the `ExperimentalSubCircuitApi` opt-in.
- Bind a session-scoped `SubCircuit`, built from Metro-multibound sub-presenter and sub-UI factories (allowed to be empty), and provide it to the session's UI through `LocalSubCircuit`.
- Prove once, with a throwaway sub-screen, that `@SubCircuitInject` codegen generates and resolves on jvm, android, iOS and wasmJs, then remove the throwaway sub-screen.
- Document SubCircuit anatomy in the screen conventions, including a narrow exception that allows `@AssistedInject` presenter classes for sub-presenters only.
- No user-visible behavior changes.

## Capabilities

### New Capabilities
<!-- None: infrastructure only. The change sets skip_specs: true. -->

### Modified Capabilities
<!-- None. -->

## Impact

- **Build:**
  - `gradle/libs.versions.toml`.
  - Build files for `:app-screens` (api), `:app-presenters` (plus jvmTest for the `-test` artifact) and `composeApp`, including the opt-in.
- **DI:** `composeApp/.../app/di/CircuitBindings.kt` and `SessionGraph.kt`.
- **UI root:** `composeApp/.../app/App.kt`.
- **Docs:** `.claude/rules/compose-screens.md` and the "Screens span three modules" section of CLAUDE.md.
- **Downstream:** `add-feed-filters` depends on this change.
