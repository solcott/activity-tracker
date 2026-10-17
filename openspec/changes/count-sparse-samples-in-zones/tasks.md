# Tasks

## 1. Resampling

- [x] 1.1 In `PerSecond.hold`, derive the hold limit from the median gap (`max(MAX_HOLD_SECONDS, 2 × median)`) and update its KDoc; verify with new `core/activityfile` tests: a 30 s steady stream of 8:00 gives 480 values, a 1 s stream with a 600 s gap gives one value for the gap, a 1–8 s smart-recorded stream gives its duration
- [x] 1.2 Extend `StreamAnalytics` tests in `core/data` with the 30 s, all-zone-3 case for `timeInZones` (about 480 s in zone 3); verify `./gradlew :core-data:jvmTest :core-activityfile:jvmTest`
- [x] 1.3 Confirm existing power curve, normalized power and zone tests pass unchanged (1 s data is unaffected)

## 2. Verification

- [x] 2.1 Import a 30 s TCX interval run on desktop and check its heart-rate zones add up to its time (Hot Reload MCP semantic tree)
- [x] 2.2 `./gradlew check` passes
