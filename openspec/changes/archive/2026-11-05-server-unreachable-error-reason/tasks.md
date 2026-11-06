# Tasks

## 1. Reason wording

- [x] 1.1 In `ui/resources/src/commonMain/composeResources/values/strings.xml`, rename `error_reason_offline` to `error_reason_unreachable` with the text "Couldn't reach the server.", and change the example comment on `error_with_reason` to "Couldn't load your feed. Couldn't reach the server." Verify `grep -rn error_reason_offline --exclude-dir=build .` matches nothing outside `openspec/`.
- [x] 1.2 In `app/common/ui/.../ui/ErrorText.kt`, map `DataError.Network` to `Res.string.error_reason_unreachable` in `errorReason()`, fix its import, and update the `errorReason`/`failureText` KDoc if it mentions offline. In `ErrorTextTest.kt`, rename `offline()` to `unreachable()` and assert the new resource. Verify that `./gradlew :app-common-ui:jvmTest --tests "*.ErrorTextTest"` passes.
- [x] 1.3 In `.agents/rules/compose-screens.md` (Text bullet), say the error adds "unreachable, server or unreadable" instead of "offline, server or unreadable". Verify that `scripts/check-agent-sync.sh` passes.

## 2. Screens and screenshots

- [x] 2.1 Run the UI tests that may assert the reason text: `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"`, then each feature's `-ui` `jvmTest` and the login UI test. Fix any that look for "You're offline". Verify they all pass.
- [x] 2.2 Regenerate the screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`. Review the changed PNGs: only the failed-state reason text should differ. Verify that `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes.

## 3. Integration

- [x] 3.1 Run `./gradlew check` and verify it passes (ktfmt, detekt, stability, module boundaries, screenshots). (Run with the iOS simulator test tasks off: `:core-data:iosSimulatorArm64Test` hung on a simulator another worktree was using at the same time, and this change touches no iOS code.)
- [x] 3.2 Run the desktop app (`:desktopApp:hotRun`) while signed in, then stop the stack (`scripts/dev.sh down`) and refresh the feed. Verify it shows "Couldn't load your feed. Couldn't reach the server." and that Retry loads the feed once the stack is back up (`scripts/dev.sh`). (Done in localhost mode, since in device mode Cloudflare answers 502/530 for a stopped stack and the app says "The server had a problem." With the AppView stopped, the feed said "Couldn't load your feed. Couldn't reach the server." with Retry and kept the cached activities. After the AppView was back, Retry in that localhost build got a 401 because the AppView was now in device mode, so the recovery was checked by relaunching in device mode instead: the feed loaded with no error.)

## Workflow follow-up

- Archive the change once it's reviewed (`/opsx:archive`). This merges the six spec deltas into `openspec/specs/`.
