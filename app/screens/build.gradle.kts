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
    // SubCircuit is experimental. Opted in module-wide so Metro's generated sub-factories compile too.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain.dependencies {
            api(projects.appCommonApi)
            api(projects.coreData)
            // Moved features' contracts that States here hold: Reporting (detail, profile).
            api(projects.featureReportApi)
            api(libs.circuit.runtime)
            api(libs.circuit.serialization)
            api(libs.circuitx.subcircuit)
            // Only for TextFieldState: a State holds its text fields' state, which the presenter owns.
            api(libs.compose.foundation)
            api(libs.dataresult.uistate)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.io.bytestring)

            implementation(libs.compose.runtime)
        }
    }
}
