# Tasks

## 1. Lap speed

- [x] 1.1 In `ChartsBuilder.lapRow`, fall back to distance over moving (else elapsed) time when `avgSpeed` is null; verify with an `app-presenters` jvmTest: a 1,000 m, 300 s lap without speed shows 12.0 km/h, a lap without distance shows none

## 2. Sample generator

- [x] 2.1 Add the gated `WriteWorkoutSamples` jvmTest in `core/activityfile` writing the eight samples to `build/workout-fixtures`, averages derived from the generated samples; verify the command writes them and `./gradlew :core-activityfile:jvmTest` without the property writes nothing
- [x] 2.2 Assert in the generator, before writing, that each file decodes with the app's reader and every stored average lies within its samples' range; verify by running it
- [x] 2.3 Document the command in the `activity-files.md` rule (`.agents/rules/`, mirrored to `.claude/rules/`); verify `scripts/check-agent-sync.sh` passes

## 3. Verification

- [x] 3.1 Import the MTB ride, triathlon and sparse TCX samples on desktop and check every lap shows a speed and the triathlon's average heart rate is in range (Hot Reload MCP semantic tree)
- [x] 3.2 `./gradlew check` passes
