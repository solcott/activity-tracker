plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Circuit presenters. Compose runtime, and foundation (through :app-screens) only for TextFieldState. No material3, and no UI code: no
// layouts, modifiers or composables that emit.
kotlin {
    // SubCircuit is experimental. Opted in module-wide so Metro's generated sub-factories compile too.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain.dependencies {
            api(projects.appScreens)
            implementation(projects.appCommonPresenter)
            // Moved features' screens these presenters open: editing, gear and the route map and gallery (detail), the gear list
            // and moderation (settings).
            implementation(projects.featureEditApi)
            implementation(projects.featureGearApi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureModerationApi)
            implementation(libs.circuit.codegen.annotations)
            implementation(libs.circuit.retained)
            implementation(libs.circuit.runtime)
            implementation(libs.circuit.runtime.presenter)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.dataresult.uistate.circuit)
        }
        jvmTest.dependencies { implementation(projects.appCommonPresenterTesting) }
        commonTest.dependencies {
            implementation(libs.circuit.test)
            implementation(libs.circuitx.subcircuit.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
