plugins {
    id("activitytracker.kmp.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    id("activitytracker.native.oauth")
}

val generateNativeNetworkConfig =
    tasks.register<GenerateNativeNetworkConfig>("generateNativeNetworkConfig") {
        description = "Generate native network config for Kotlin/Native"
        packageName = "io.github.solcott.activitytracker.app"
        appviewPublicUrl = nativeOAuth.appviewPublicUrl
        redirectScheme = nativeOAuth.redirectScheme
        outputDir = layout.buildDirectory.dir("generated/nativeNetworkConfig/kotlin")
    }

// Public so the platform shells (desktopApp's window title) can read the shared strings.
compose.resources { publicResClass = true }

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // So iosApp's SwiftFitDecoder sees FitDecoder and its DTOs under their own names.
            export(projects.coreActivityfile)
            // Calf's UIKit components need it exported to work on iOS.
            export(libs.calf.ui)
        }
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateNativeNetworkConfig) }
        commonMain.dependencies {
            // api so the framework can export it (see the framework block above).
            api(libs.calf.ui)
            // Only so the platform Metro graphs here aggregate the presenter factories. UI code never imports from it.
            implementation(projects.appPresenters)
            implementation(projects.appScreens)
            implementation(projects.coreData)
            // SF Symbols-style glyphs for the Compose-drawn iOS chrome (the native bars take SF Symbol names instead).
            implementation(libs.calf.cupertino.icons)
            implementation(libs.circuit.foundation)
            implementation(libs.circuit.runtime)
            implementation(libs.circuit.serialization)
            implementation(libs.circuitx.gesture.navigation)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.vico.multiplatform)
            implementation(libs.zoomimage.compose.coil3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            // OpenGL rather than Vulkan: wider device and emulator support.
            runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
        }
        // MapLibre Compose has no wasmJs target yet (maplibre/maplibre-compose#209, needs CMP 1.13), so the web app keeps the Canvas
        // RoutePreview (see ActivityMap and BACKLOG.md).
        // Desktop's native runtime is picked per OS in :desktopApp.
        nonWebMain.dependencies { implementation(libs.maplibre.compose) }
        // api so the framework can export it (see the framework block above).
        iosMain.dependencies { api(projects.coreActivityfile) }
        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
        // Skia's native library, for tests that encode images (PhotoCompressionTest).
        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
