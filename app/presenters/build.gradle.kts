plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Circuit presenters. Compose runtime, and foundation (through :app-screens) only for TextFieldState. No material3, and no UI code: no
// layouts, modifiers or composables that emit.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.appScreens)
            implementation(libs.circuit.codegen.annotations)
            implementation(libs.circuit.runtime)
            implementation(libs.circuit.runtime.presenter)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
        }
        commonTest.dependencies {
            implementation(libs.circuit.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
