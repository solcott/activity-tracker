plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
}

// Contracts several features share: UiError, the route and visibility types, the details-form and gear-picker fields, history
// dates, the activity page (detail's, and record's import preview), the activity-list events and the place-name sheet. Feature API modules
// build on these;
// nothing here belongs to one feature.
composeStability { strictClasses.set(true) }

kotlin {
    // SubCircuit is experimental, opted in module-wide as in the feature conventions.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain.dependencies {
            api(projects.coreData)
            // Only for Screen: the SettingsPage marker that settings' pages in several features carry.
            api(libs.circuit.runtime)
            // The place-name sheet (ProposePlaceNameScreen) is a SubCircuit screen.
            api(libs.circuitx.subcircuit)
            // Only for TextFieldState: a State holds its text fields' state, which the presenter owns.
            api(libs.compose.foundation)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.io.bytestring)

            implementation(libs.compose.runtime)
        }
    }
}
