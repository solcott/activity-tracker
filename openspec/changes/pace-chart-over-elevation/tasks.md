# Tasks

## 1. A hilly run sample

- [x] 1.1 Give `WriteWorkoutSamples.intervalRun()`'s hard stretches a climb and its jogs the matching descent, and verify `./gradlew :core-activityfile:jvmTest --tests "*.WriteWorkoutSamples" -PworkoutSamples` writes `build/workout-fixtures/interval-run.tcx` and passes its consistency checks
- [x] 1.2 Import `interval-run.tcx` on desktop and verify the activity's page shows an elevation gain of about 330 m and an Elevation card with five climbs and a last gentle one

## 2. Pace over elevation

- [x] 2.1 At 1000dp on desktop, screenshot the interval run's Pace card and verify each climb draws rising behind the line while the pace line drops on it; record the screenshot's finding in this task
  - Finding (1240px window, 1000dp page): each hill repeat's 60 m climb draws as a rising ramp behind the pace line and drops on the jog back down, and the cool-down's 30 m climb rises at the end while pace stays slow. The pace line stays clear, and its fill lets the backdrop show through, so nothing is lost.
- [x] 2.2 If 2.1 shows the line or fill lost over the backdrop, make the line's fill fainter over a backdrop in `TimeSeriesChart` (design decision 2) and verify by a new screenshot at 1000dp; otherwise mark this done as not needed
  - Not needed: 2.1 found the line and fill readable.
- [x] 2.3 At 400dp verify the Pace card shows no elevation behind it, and verify a heart rate card on the MTB ride at 1000dp looks unchanged from before
- [x] 2.4 Run `./gradlew check composeStabilityCheck` and verify it passes
