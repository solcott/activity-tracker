plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

// Circuit contracts: each feature's Screen, State and Event. Presenters (:app-presenters) and UIs (:composeApp) both depend on this
// module and never on each other.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreData)
            api(libs.circuit.runtime)
            api(libs.circuit.serialization)
            implementation(libs.compose.runtime)
        }
    }
}
