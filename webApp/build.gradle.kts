import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("activitytracker.kmp.web")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Web application shell (the wasmJs counterpart of :androidApp / iosApp): owns the Metro graph,
// main(), index.html and dev-server config. Shared UI and web actuals live in :composeApp.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("webApp")
        // Dev-server host, port (PORT env) and headers are set in webpack.config.d/devserver.js.
        browser { commonWebpackConfig { outputFileName = "webApp.js" } }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            // The graph is generated here, so every module that contributes bindings must be on the
            // classpath.
            // GearChoices, which the record, import and edit presenters inject.
            implementation(projects.appCommonPresenter)
            // The place-name sheet's UI factory.
            implementation(projects.appCommonUi)
            implementation(projects.composeApp)
            implementation(projects.coreData)
            implementation(projects.featureDetailPresenter)
            implementation(projects.featureDetailUi)
            implementation(projects.featureDivesitePresenter)
            implementation(projects.featureDivesiteUi)
            implementation(projects.featureEditPresenter)
            implementation(projects.featureEditUi)
            implementation(projects.featureFeedPresenter)
            implementation(projects.featureFeedUi)
            implementation(projects.featureFeedfilterPresenter)
            implementation(projects.featureFeedfilterUi)
            implementation(projects.featureGearPresenter)
            implementation(projects.featureGearUi)
            implementation(projects.featureInvitesPresenter)
            implementation(projects.featureInvitesUi)
            implementation(projects.featureLoginPresenter)
            implementation(projects.featureLoginUi)
            implementation(projects.featureMapPresenter)
            implementation(projects.featureMapUi)
            implementation(projects.featureMediaPresenter)
            implementation(projects.featureMediaUi)
            implementation(projects.featureModerationPresenter)
            implementation(projects.featureModerationUi)
            implementation(projects.featurePostPresenter)
            implementation(projects.featurePostUi)
            implementation(projects.featureProfilePresenter)
            implementation(projects.featureProfileUi)
            implementation(projects.featureRecordPresenter)
            implementation(projects.featureRecordUi)
            implementation(projects.featureReportPresenter)
            implementation(projects.featureReportUi)
            implementation(projects.featureSearchPresenter)
            implementation(projects.featureSearchUi)
            implementation(projects.featureSegmentPresenter)
            implementation(projects.featureSegmentUi)
            implementation(projects.featureSettingsPresenter)
            implementation(projects.featureSettingsUi)
            implementation(projects.featureUploadsPresenter)
            implementation(projects.featureUploadsUi)
            implementation(projects.uiResources)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
