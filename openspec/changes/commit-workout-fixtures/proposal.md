# Proposal

## Why

Checking keep-workout-structure needed sample files (pool swim, strength, triathlon, ski day, MTB ride, TCX interval run and duathlon). They came from a throwaway test that was deleted afterwards, so the next change touching activity files has to write them again. They also had flaws that hid what they were meant to show: laps and splits with no average speed (every lap table showed "–" for speed), and a triathlon whose stored average heart rate (140) was above every sample (130).

Separately, the "–" speed is also what a real file shows: TCX laps carry speed only in the optional `LX/AvgSpeed` extension, which many exporters omit, though every lap has its distance and time.

## What Changes

- A committed generator writes a set of sample activity files to `build/workout-fixtures`, on request only (not part of `check`), from the app's own `FitWriter` and `TcxWriter`.
- The samples are self-consistent: each lap's and split's average speed matches its distance and time, and each summary average lies within its samples' range and matches them.
- The set covers the structure the app shows: pool swim lengths with rests, strength sets with rests, a triathlon with transitions, a ski day's runs and lifts, a ride with laps and power, a TCX interval run with rest laps and a TCX duathlon, plus a sparse-sampled TCX run.
- The lap table shows a lap's speed from its distance and moving time (or elapsed, when no moving time) when the file gives none.

## Capabilities

### New Capabilities
- `dev/sample-activity-files`: the generated sample files for checking activity import, display and export by hand.

### Modified Capabilities
- `feed/activity-file-data`: lap table speed when the file gives none.

## Impact

- `core/activityfile` jvmTest: a generator test gated by a Gradle property, plus its documentation in the `activity-files.md` rule.
- `app-presenters` `ChartsBuilder.lapRow`: speed falls back to distance over time.
- No lexicon, AppView or stored-data change; files go under `build/`, which git ignores.
