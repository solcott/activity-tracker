plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Presenter helpers several features share: rememberSingleFlight, content-state seeds, the details form, the gear picker's choices,
// the activity page's builder, new-activity visibility, the activity-list event sink, reporting and revealing, and the place-name sheet's
// presenter. Compose runtime and no UI,
// like every presenter module.
kotlin {
    // SubCircuit is experimental, opted in module-wide as in the feature conventions.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            // The activity-list sink opens the activity, the gallery and profiles.
            api(projects.featureDetailApi)
            api(projects.featureDivesiteApi)
            api(projects.featureMediaApi)
            api(projects.featureProfileApi)
            // rememberReporting builds report's Reporting.
            api(projects.featureReportApi)
            // The post sink opens the segment or dive site a shared proposal names (feed/posts).
            api(projects.featureSegmentApi)

            implementation(libs.circuit.codegen.annotations)
            implementation(libs.circuit.retained)
            implementation(libs.circuit.runtime)
            implementation(libs.circuitx.subcircuit)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.dataresult.uistate.circuit)
        }
        commonTest.dependencies { implementation(libs.circuit.test) }
        jvmTest.dependencies {
            implementation(libs.circuitx.subcircuit.test)
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
