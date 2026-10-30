plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Presenter helpers several features share: rememberSingleFlight, content-state seeds, the details form, new-activity visibility, the
// activity-list event sink, reporting and revealing. Compose runtime and no UI, like every presenter module.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            // The shared helpers open screens that still live in :app-screens (the activity, profile and gallery). This goes once those
            // features have their own -api modules.
            api(projects.appScreens)
            implementation(libs.circuit.retained)
            implementation(libs.circuit.runtime)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.dataresult.uistate.circuit)
        }
        commonTest.dependencies { implementation(libs.circuit.test) }
    }
}
