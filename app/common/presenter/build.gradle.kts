plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Presenter helpers several features share: rememberSingleFlight, content-state seeds, the details form, the gear picker's choices,
// new-activity visibility, the activity-list event sink, reporting and revealing. Compose runtime and no UI, like every presenter module.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            // The shared helpers open screens that still live in :app-screens (the activity and profile). This goes once those
            // features have their own -api modules.
            api(projects.appScreens)
            // The activity-list sink opens the gallery.
            api(projects.featureMediaApi)
            // rememberReporting builds report's Reporting.
            api(projects.featureReportApi)

            implementation(libs.circuit.retained)
            implementation(libs.circuit.runtime)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.dataresult.uistate.circuit)
        }
        commonTest.dependencies { implementation(libs.circuit.test) }
    }
}
