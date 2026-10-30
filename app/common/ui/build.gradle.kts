plugins { id("activitytracker.kmp.compose") }

// Domain widgets several features' UIs show: the activity card, map and route preview, stats and formatting, visibility pickers, avatars,
// media rows, the details form and AppPreview, the harness every UI test and screenshot renders through.
composeUiTests { jvm = true }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            // Contracts of features that haven't moved out yet (an activity's figures, an import's previews); each goes when its feature
            // gets its own -api module.
            api(projects.appScreens)
            // ReportingHost embeds ReportScreen by key.
            api(projects.featureReportApi)
            api(projects.uiDesignsystem)
            api(libs.coil.compose)

            implementation(libs.circuit.runtime)
            implementation(libs.circuitx.subcircuit)
            implementation(libs.coil.network.ktor3)
            implementation(libs.filekit.dialogs.compose)
        }
        // Activity-result launchers for the location permission request.
        androidMain.dependencies { implementation(libs.androidx.activity.compose) }
        // MapLibre Compose has no wasmJs target yet (maplibre/maplibre-compose#209), so the web keeps the Canvas RoutePreview.
        nonWebMain.dependencies { implementation(libs.maplibre.compose) }
        jvmTest.dependencies { implementation(projects.appFixtures) }
    }
}
