plugins {
    kotlin("multiplatform")
    id("activitytracker.quality")
}

// The Kotlin entry points iosApp's Swift calls, in signatures Swift export can carry: a UIViewController, FitDecoder with its DTOs, and
// plain values. :iosExport exports this module to Swift; :composeApp, which does the work behind it, is not exported, because a Compose
// type anywhere in an exported API breaks the iOS build (dev/ios-swift-interop).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        iosMain.dependencies {
            // api, so Swift sees FitDecoder and the DTOs SwiftFitDecoder builds.
            api(projects.coreActivityfile)

            implementation(projects.composeApp)
        }
    }
}
