plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// The presenter host (dev/presenter-host): runs screens' presenters with no UI attached and owns the navigation stacks they drive, so
// a native UI can draw their states, and holds the session logic both shells share (the session mode, the session's background work,
// the tabs). Compose runtime and no UI, like a presenter module, and it sees features only through their API modules: it gets
// presenters from the app's graph, never from a module it depends on (dev/module-boundaries).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            api(projects.coreData)
            // Navigator, Screen and CircuitUiState are in the host's signatures.
            api(libs.circuit.runtime)

            // The screens the host names: the tabs' roots, the flows that take the whole window, and what a request opens.
            implementation(projects.coreLocation)
            implementation(projects.featureEditApi)
            implementation(projects.featureFeedApi)
            implementation(projects.featureLoginApi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureProfileApi)
            implementation(projects.featureRecordApi)
            implementation(projects.featureSearchApi)
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSettingsApi)
            implementation(libs.circuit.retained)
            implementation(libs.circuit.runtime.presenter)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.molecule.runtime)
        }
        commonTest.dependencies { implementation(projects.featureDetailApi) }
        jvmTest.dependencies { implementation(libs.turbine) }
    }
}
