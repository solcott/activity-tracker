plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

// State contracts are what the UIs take as params, so every field must be statically stable.
composeStability { strictClasses.set(true) }

// Circuit contracts: each feature's Screen, State and Event. Presenters (:app-presenters) and UIs (:composeApp) both depend on this
// module and never on each other.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreData)
            api(libs.circuit.runtime)
            api(libs.circuit.serialization)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.io.bytestring)
            implementation(libs.compose.runtime)
        }
    }
}
