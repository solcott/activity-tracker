plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
}

// What every presenter test uses: presenterTestOf / Presenter.test on a fixed thread, and awaitUntil. JVM only, since presenter tests run
// there; presenter modules add it to jvmTest.
kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.circuit.test)
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            implementation(libs.compose.runtime)
        }
    }
}
