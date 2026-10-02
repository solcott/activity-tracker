plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Circuit presenters. Compose runtime only: no foundation, ui or material3, so presenters can't reach for UI code.
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
