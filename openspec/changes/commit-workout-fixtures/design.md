# Design

## Context

The deleted generator was a `core/activityfile` jvmTest (`DumpWorkoutFixturesTest`) that built `ActivityFileData`-shaped values and wrote them with `FitWriter`/`TcxWriter` to `build/workout-fixtures`. `ChartsBuilder.lapRow` takes `speed` only from `lap.avgSpeed`; `TcxParser` reads `LX/AvgSpeed` and otherwise leaves it null.

## Goals / Non-Goals

**Goals:** a generator anyone can run, consistent data, and lap speed shown when derivable.

**Non-Goals:** using the samples as automated test fixtures (unit tests keep their own small inline data); committing the binary files themselves; deriving speed at import (stored laps stay as the file had them).

## Decisions

- **A jvmTest gated by a Gradle property** (`./gradlew :core-activityfile:jvmTest --tests "*.WriteWorkoutSamples" -PworkoutSamples`), skipped through `assumeTrue` without it. It reuses the writers and test-source helpers with no new Gradle wiring. Alternative: a `JavaExec` task over the jvm test classpath, which is more KMP plumbing for the same result.
- **Build samples from a few helpers that derive averages from the samples they generate** (speed = distance / time, HR average from the HR series), so consistency comes from construction, not hand-typed numbers.
- **Write to `build/workout-fixtures`** under the repo root: ignored by git, and served by the web dev server for the browser-pane upload hook used in earlier checks.
- **Lap speed fallback in the presenter** (`lapRow`), not the importer: it fixes laps already stored and keeps the stored record faithful to the file.

## Risks / Trade-offs

- [Samples drift from what real devices write] → keep them structurally like Garmin's (session, lap, length, set, split messages as the FIT SDK examples) and check new structure against a real file when one is available.
