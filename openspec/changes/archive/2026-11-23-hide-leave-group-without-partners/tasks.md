# Tasks

## 1. Offer Leave group only on a grouped activity

- [x] 1.1 Offer the owner's group state only while the activity lists a partner or has left (`OwnGroup.offered`, used by `ActivityDetailPresenter`), and update the `ActivityDetailPage.ownGroup` and `OwnGroupState` docs; verify with `./gradlew :feature-detail-presenter:jvmTest --tests "*.ActivityDetailGroupTest"`
- [x] 1.2 Add presenter tests: no Leave group on an own activity with no partner, and Rejoin group on one that left with no partner listed; verify they pass in `ActivityDetailGroupTest`
- [x] 1.3 Check the activity detail samples that show `ownGroup` also list partners, and that `TogetherWithUiTest` and the screenshot suite still pass (`./gradlew :feature-detail-ui:jvmTest :androidApp:testDebugScreenshotTestDefaultTestSuite`)

## Workflow follow-up

- Archive the change once it lands.
