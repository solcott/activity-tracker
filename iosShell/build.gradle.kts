plugins {
    kotlin("multiplatform")
    id("activitytracker.quality")
    // Exported to Swift whole: checkSwiftExportSources keeps its sources to what the export can carry.
    id("activitytracker.swift.contract")
}

// The Kotlin entry points iosApp's Swift calls, in signatures Swift export can carry: a UIViewController, FitDecoder with its DTOs, and
// plain values. :iosExport exports this module to Swift; :composeApp, which does the work behind it, is not exported, because a Compose
// type anywhere in an exported API breaks the iOS build (dev/ios-swift-interop).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        iosMain.dependencies {
            // api, so Swift sees the presenter host's types, which IosApp's signatures name.
            api(projects.appHost)
            // api, so Swift sees FitDecoder and the DTOs SwiftFitDecoder builds.
            api(projects.coreActivityfile)
            // api, so Swift sees ExportArchive, which IosApp.writeArchive takes from the Account page's state.
            api(projects.coreData)
            // api, so Swift sees ByteString, which IosApp.heldPhoto returns and Swift turns into Data (toNSData).
            api(libs.kotlinx.io.bytestring)

            // compressPhoto, which IosApp.compressPhoto runs for a photo the native gear form picks.
            implementation(projects.appCommonUi)
            implementation(projects.composeApp)
        }
    }
}
