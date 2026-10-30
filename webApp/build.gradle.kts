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
            implementation(projects.appPresenters)
            implementation(projects.appScreens)
            implementation(projects.composeApp)
            implementation(projects.coreData)
            implementation(projects.featureFeedfilterPresenter)
            implementation(projects.featureFeedfilterUi)
            implementation(projects.featureMapPresenter)
            implementation(projects.featureMapUi)
            implementation(projects.featureMediaPresenter)
            implementation(projects.featureMediaUi)
            implementation(projects.featureModerationPresenter)
            implementation(projects.featureModerationUi)
            implementation(projects.featureReportPresenter)
            implementation(projects.featureReportUi)
            implementation(projects.featureSearchPresenter)
            implementation(projects.featureSearchUi)
            implementation(projects.uiResources)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
