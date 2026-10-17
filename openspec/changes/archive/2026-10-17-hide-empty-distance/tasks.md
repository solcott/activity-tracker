# Tasks

## 1. Stat rule

- [x] 1.1 Add the distance and elevation stat helpers beside `Format.rateStat` that return null for a `RateKind.None` sport whose value is absent or zero; verify with a `composeApp` jvmTest (or a presenter-free unit test of the decision) covering strength at 0 m, strength with none, an indoor climb with 120 m ascent, and a run with no distance
- [x] 1.2 Use them in `ActivityCard`, `overviewStats` and the import preview; verify `./gradlew :composeApp:jvmTest` passes

## 2. Verification

- [x] 2.1 On desktop (Hot Reload MCP semantic tree), check a strength workout's card and page show no distance or elevation and a run's are unchanged
- [x] 2.2 `./gradlew check` passes
