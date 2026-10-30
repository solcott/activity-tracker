plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
}

// What every presenter test uses: presenterTestOf / Presenter.test on a fixed thread, and awaitUntil, plus the fixtures several features'
// tests share (ActivityTestBase's served activity, test segment stores, routes). JVM only, since presenter tests run there; presenter
// modules add it to jvmTest.
kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.coreData)
            api(libs.circuit.test)
            api(libs.kotlinx.coroutines.test)
            api(libs.ktor.client.mock)
            api(libs.turbine)
            implementation(libs.compose.runtime)
            implementation(libs.kermit)
        }
    }
}
