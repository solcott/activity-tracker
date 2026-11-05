plugins {
    id("activitytracker.kmp.compose")
    // The place-name sheet's UI is a @SubCircuitInject factory the app graphs aggregate.
    alias(libs.plugins.metro)
}

// Domain widgets several features' UIs show: the activity card, map and route preview, stats and formatting, visibility pickers, avatars,
// media rows, the details form, the place names and their sheet, and AppPreview, the harness every UI test and screenshot renders through.
composeUiTests { jvm = true }

kotlin {
    // SubCircuit is experimental, opted in module-wide as in the feature conventions.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            // ReportingHost embeds ReportScreen by key.
            api(projects.featureReportApi)
            api(projects.uiDesignsystem)
            api(libs.coil.compose)

            implementation(libs.circuit.codegen.annotations)
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
